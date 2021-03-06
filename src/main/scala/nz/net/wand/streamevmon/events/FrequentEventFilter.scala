/* This file is part of streamevmon.
 *
 * Copyright (C) 2021  The University of Waikato, Hamilton, New Zealand
 *
 * Author: Daniel Oosterwijk
 *
 * All rights reserved.
 *
 * This code has been developed by the University of Waikato WAND
 * research group. For further information please see https://wand.nz,
 * or our Github organisation at https://github.com/wanduow
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nz.net.wand.streamevmon.events

import nz.net.wand.streamevmon.flink.HasFlinkConfig

import java.time.{Duration, Instant}

import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector

import scala.collection.JavaConverters._
import scala.collection.mutable

/** Filters out events that occur frequently, replacing them with batched
  * summaries at certain intervals.
  *
  * This function should be placed directly after an event-producing operator,
  * such as a detector.
  *
  * ==Configuration==
  *
  * This class is configured by the `eventGrouping.frequentFilter` config
  * key group. The key group should contain a number of other key groups, each
  * of which should have fields as shown in the default settings below.
  *
  * {{{
  * eventGrouping:
  *   frequentFilter:
  *     small:
  *       interval: 60
  *       count: 5
  *       cooldown: 120
  *       severity: 25
  *     medium:
  *       interval: 120
  *       count: 15
  *       cooldown: 240
  *     large:
  *       interval: 360
  *       count: 100
  *       cooldown: 720
  *       severity: 75
  * }}}
  * ;
  * These fields together determine the frequency of events that will trigger
  * filtering by that group. There must be `count` many events within `interval`
  * seconds. When the threshold is reached, a bulk event will be emitted with
  * the provided severity, and the configuration starts a cooldown, after which
  * it is re-enabled. Events will only be passed through unscathed when all
  * configurations are enabled.
  */
class FrequentEventFilter
  extends KeyedProcessFunction[String, Event, Event]
          with CheckpointedFunction
          with HasFlinkConfig {
  override val flinkName: String = "Frequent Event Filter"
  override val flinkUid: String = "frequent-event-filter"
  override val configKeyGroup: String = "frequentFilter"

  @transient var configs: Iterable[FrequencyConfig] = _
  @transient lazy val longestInterval: Duration = Duration.ofSeconds(configs.maxBy(_.interval).interval)

  /** Parses the configuration section corresponding to this operator. Will
    * throw an IllegalArgumentException (or possibly other types, depending on
    * the issue) if anything is wrong with the format of the configuration.
    */
  protected def parseConfig(getDefaults: Boolean = false): Iterable[FrequencyConfig] = {
    val conf = configWithOverride(getRuntimeContext)
    val result = conf
      .toMap
      .asScala
      .keySet
      // Grab the config keys for just this operator
      .filter { item =>
        if (getDefaults) {
          item.startsWith(s"eventGrouping.$configKeyGroup.defaults")
        }
        else {
          item.startsWith(s"eventGrouping.$configKeyGroup") &&
            !item.startsWith(s"eventGrouping.$configKeyGroup.defaults")
        }
      }
      // Trim off the common section
      .map { item =>
        if (getDefaults) {
          item.drop(s"eventGrouping.$configKeyGroup.defaults.".length)
        }
        else {
          item.drop(s"eventGrouping.$configKeyGroup.".length)
        }
      }
      .map { i =>
        // The rest of the config key should just be two parts...
        val parts = i.split('.')
        if (parts.length != 2) {
          throw new IllegalArgumentException(s"Config key eventGrouping.$configKeyGroup.$i unrecognised!")
        }
        else {
          // ... the name of the group, and one of the fields of a FrequencyConfig
          (parts.head, parts.drop(1).head)
        }
      }
      // Turn the list of tuples (name, key) into a Map[name -> Set[keys]]
      .groupBy(_._1)
      .mapValues(_.map(_._2))
      .map { case (groupName, configKeys) =>
        // If the group doesn't contain all the keys we expect, fail loudly
        // since it's invalid. We allow unrecognised keys to be present.
        if (FrequencyConfig.configKeyNames.exists(k => !configKeys.contains(k))) {
          throw new IllegalArgumentException(
            s"Config keys ${configKeys.mkString(",")} for group $groupName " +
              s"unrecognised! Expected ${FrequencyConfig.configKeyNames.mkString(",")}"
          )
        }
        else {
          // Now we know all the keys are there, we can go ahead and construct
          // a config for this group.
          FrequencyConfig(groupName, configKeyGroup, getDefaults, conf)
        }
      }

    if (result.isEmpty) {
      if (getDefaults) {
        throw new IllegalStateException(
          s"No configurations specified under `eventGrouping.$configKeyGroup, " +
            s"and couldn't find defaults under `eventGrouping.$configKeyGroup.defaults!")
      }
      else {
        parseConfig(getDefaults = true)
      }
    }
    else {
      result
    }
  }

  var recentTimestampsState: MapState[String, mutable.Queue[Instant]] = _

  // Value is set to None if the conf is enabled, or Some(timestamp) if it was
  // disabled at the associated stamp.
  var configEnabledMapState: MapState[String, mutable.Map[FrequencyConfig, Option[Instant]]] = _

  @transient protected var justInitialised = false

  def enabledConfigs(key: String): Iterable[FrequencyConfig] = configEnabledMapState.get(key).filter(_._2.isEmpty).keys

  def disabledConfigs(key: String): Map[FrequencyConfig, Instant] = configEnabledMapState.get(key).flatMap { case (k, v) =>
    v.map(s => (k, s))
  }.toMap

  def timestampsWithinInterval(key: String, config: FrequencyConfig, currentTime: Instant): Iterable[Instant] = {
    val startOfInterval = currentTime.minus(Duration.ofSeconds(config.interval))
    recentTimestampsState.get(key).reverse.takeWhile(t => t.isAfter(startOfInterval)).reverse
  }

  def createRecordsForNewKey(key: String): Unit = {
    val configEntries: Seq[(FrequencyConfig, Option[Instant])] = configs.map(conf => (conf, None)).toSeq
    configEnabledMapState.put(key, mutable.Map(configEntries: _*))
    recentTimestampsState.put(key, mutable.Queue())
  }

  override def open(parameters: Configuration): Unit = {
    configs = parseConfig()
  }

  override def processElement(
    value: Event,
    ctx: KeyedProcessFunction[String, Event, Event]#Context,
    out: Collector[Event]
  ): Unit = {
    // See comments in ModeDetector for info about super-null from serialising mutable.Queue
    if (justInitialised) {
      recentTimestampsState.keys.forEach { k =>
        recentTimestampsState.put(k, recentTimestampsState.get(k).map(identity))
      }
      justInitialised = false
    }

    if (!recentTimestampsState.keys.asScala.toList.contains(ctx.getCurrentKey)) {
      createRecordsForNewKey(ctx.getCurrentKey)
    }

    recentTimestampsState.get(ctx.getCurrentKey).enqueue(value.time)
    recentTimestampsState.get(ctx.getCurrentKey).dequeueAll(_.isBefore(value.time.minus(longestInterval)))

    configEnabledMapState.get(ctx.getCurrentKey)
      .map(conf => (conf._1, conf._2, timestampsWithinInterval(ctx.getCurrentKey, conf._1, value.time)))
      .foreach { case (conf, disabledAt, stamps) =>
        val isTriggered = stamps.size > conf.count
        (disabledAt, isTriggered) match {
          // If the conf is disabled, but still triggered, don't start the cooldown.
          case (Some(_), true) => configEnabledMapState.get(ctx.getCurrentKey).put(conf, Some(value.time))
          // If the conf is disabled, but not triggered, check if the cooldown has expired.
          case (Some(time), false) =>
            if (time.isBefore(value.time.minusSeconds(conf.cooldown))) {
              configEnabledMapState.get(ctx.getCurrentKey).put(conf, None)
            }
          // If the conf is enabled and triggered, output a bulk event and disable the conf.
          case (None, true) =>
            if (stamps.size > conf.count) {
              out.collect(Event(
                s"bulk_${value.eventType}",
                value.stream,
                conf.severity,
                value.time,
                Duration.ZERO,
                s"""Frequent events of type ${value.eventType} - configuration name "${conf.name} (${conf.count} events in ${conf.interval} seconds)"""",
                Map()
              ))
              configEnabledMapState.get(ctx.getCurrentKey).put(conf, Some(value.time))
            }
          // If the conf is enabled, but not yet triggered, do nothing.
          case (None, false) =>
        }
      }

    if (disabledConfigs(ctx.getCurrentKey).isEmpty) {
      out.collect(value)
    }
  }

  override def snapshotState(context: FunctionSnapshotContext): Unit = {}

  override def initializeState(context: FunctionInitializationContext): Unit = {
    recentTimestampsState = context
      .getKeyedStateStore
      .getMapState(
        new MapStateDescriptor(
          s"recent-timestamps",
          classOf[String],
          classOf[mutable.Queue[Instant]]
        )
      )

    configEnabledMapState = context
      .getKeyedStateStore
      .getMapState(
        new MapStateDescriptor(
          s"configs-enabled-map",
          classOf[String],
          classOf[mutable.Map[FrequencyConfig, Option[Instant]]]
        )
      )

    justInitialised = true
  }
}

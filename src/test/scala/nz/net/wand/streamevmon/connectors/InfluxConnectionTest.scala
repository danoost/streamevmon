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

package nz.net.wand.streamevmon.connectors

import nz.net.wand.streamevmon.connectors.influx.InfluxConnection
import nz.net.wand.streamevmon.measurements.traits.InfluxMeasurementFactory
import nz.net.wand.streamevmon.test.{InfluxContainerSpec, SeedData}

import java.io.{BufferedReader, InputStreamReader}
import java.net.{ServerSocket, SocketTimeoutException}

import com.github.fsanaulla.chronicler.ahc.io.InfluxIO
import com.github.fsanaulla.chronicler.ahc.management.{AhcManagementClient, InfluxMng}
import com.github.fsanaulla.chronicler.core.enums.Destinations
import com.github.fsanaulla.chronicler.core.model.{InfluxCredentials, Subscription, SubscriptionInfo}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class InfluxConnectionTest extends InfluxContainerSpec {

  def checkSubscription(
    influx: InfluxConnection,
    subscriptionInfo: SubscriptionInfo,
    checkPresent    : Boolean
  ): Unit = {
    checkSubscription(influx.influx.get, subscriptionInfo, checkPresent)
  }

  def checkSubscription(
    influx          : AhcManagementClient,
    subscriptionInfo: SubscriptionInfo,
    checkPresent    : Boolean
  ): Unit = {
    Await.result(
      influx.showSubscriptionsInfo.map {
        case Left(exception) => fail(s"Checking subscription failed: $exception")
        case Right(infos) =>
          val firstInfo = subscriptionInfo.subscriptions.head
          if (checkPresent) {
            val rightDb = infos.filter(c => subscriptionInfo.dbName == c.dbName)
            rightDb.length should be >= 0
            rightDb.foreach {
              c =>
                // We have to do a deep comparison here since Chronicler changed
                // their SubscriptionInfo to use an Array instead of a Seq, which
                // breaks simple comparisons.
                c.subscriptions.foreach { s =>
                  s.rpName shouldBe firstInfo.rpName
                  s.subsName shouldBe firstInfo.subsName
                  s.destType shouldBe firstInfo.destType
                  s.addresses.deep shouldBe firstInfo.addresses.deep
                }
            }
          }
          else {
            val rightDb = infos.filter(c => subscriptionInfo.dbName == c.dbName)
            if (rightDb.length == 0) {
              succeed
            }
            else {
              rightDb.foreach { c =>
                c.subscriptions.exists { s =>
                  s.rpName == firstInfo.rpName &&
                    s.subsName == firstInfo.subsName &&
                    s.destType == firstInfo.destType &&
                    s.addresses.deep == firstInfo.addresses.deep
                } shouldBe false
              }
            }
          }
      },
      Duration.Inf
    )
  }

  "InfluxDB container" should {

    "successfully ping" in {
      val influx =
        InfluxMng(containerAddress, containerPort, Some(InfluxCredentials(container.username, container.password)))

      Await.result(influx.ping.map {
        case Right(_) => succeed
        case Left(_)  => fail
      }, Duration.Inf)
    }

    "authenticate, by adding and removing a subscription" in {
      val influx =
        InfluxMng(containerAddress, containerPort, Some(InfluxCredentials(container.username, container.password)))

      val subscriptionName = "basicAuthAddRemove"
      val destinations = Array("http://localhost:3456")

      val expected = SubscriptionInfo(container.database,
                                      Array(
                                        Subscription(
                                          "autogen",
                                          subscriptionName,
                                          Destinations.ALL,
                                          destinations
                                        )))

      Await.result(
        influx.createSubscription(
          subscriptionName,
          container.database,
          "autogen",
          Destinations.ALL,
          destinations
        ),
        Duration.Inf
      )

      checkSubscription(influx, expected, checkPresent = true)

      Await.result(
        influx.dropSubscription(
          subscriptionName,
          container.database,
          "autogen"
        ),
        Duration.Inf
      )

      checkSubscription(influx, expected, checkPresent = false)
    }
  }

  "InfluxConnection" should {
    def getExpectedSubscriptionInfo(influx: InfluxConnection): SubscriptionInfo = {
      SubscriptionInfo(container.database,
                       Array(
                         Subscription(
                           "autogen",
                           influx.subscriptionName,
                           Destinations.ALL,
                           influx.destinations
                         )))
    }

    "add a subscription" in {
      val influx = getInfluxSubscriber("addRemove")

      val expected = getExpectedSubscriptionInfo(influx)

      Await.result(influx.addSubscription(), Duration.Inf)

      checkSubscription(influx, expected, checkPresent = true)
    }

    "remove a subscription" in {
      val influx = getInfluxSubscriber("addRemove")
      val expected = getExpectedSubscriptionInfo(influx)

      checkSubscription(influx, expected, checkPresent = true)

      Await.result(influx.dropSubscription(), Duration.Inf)

      checkSubscription(influx, expected, checkPresent = false)
    }

    "clobber an existing subscription" in {
      val influx = getInfluxSubscriber("clobber")
      val expected = getExpectedSubscriptionInfo(influx)

      Await.result(influx.addSubscription(), Duration.Inf)

      checkSubscription(influx, expected, checkPresent = true)

      val newInflux = getInfluxSubscriber("clobber", "different-address")
      val newExpected = getExpectedSubscriptionInfo(newInflux)
      Await.result(newInflux.addOrUpdateSubscription(), Duration.Inf)

      checkSubscription(influx, expected, checkPresent = false)
      checkSubscription(newInflux, newExpected, checkPresent = true)

      Await.result(newInflux.dropSubscription(), Duration.Inf)
    }
  }

  var shouldSend = false
  private lazy val ourClassLoader: ClassLoader = this.getClass.getClassLoader
  private lazy val actorSystem =
    akka.actor.ActorSystem(getClass.getSimpleName, classLoader = Some(ourClassLoader))

  def sendData(andThen: () => Any = () => Unit): Unit = {
    val db = InfluxIO(containerAddress, containerPort, Some(InfluxCredentials(container.username, container.password)))
      .database(container.database)

    actorSystem.scheduler
      .scheduleOnce(20 millis) {
        println("Sending data")
        Await.result(db.writeNative(SeedData.icmp.subscriptionLine), Duration.Inf)
        Thread.sleep(20)
        Await.result(db.writeNative(SeedData.dns.subscriptionLine), Duration.Inf)
        Thread.sleep(20)
        Await.result(db.writeNative(SeedData.traceroutePathlen.subscriptionLine), Duration.Inf)
        println("Data sent.")
        andThen()
      }
  }

  def sendDataAnd(
      afterSend: () => Any = () => Unit,
      withSend: () => Any = () => Unit
  ): Unit = {
    sendData(andThen = afterSend)
    withSend()
  }

  "Subscription listener" should {
    var isRunning: Boolean = false

    def getListener(influx: InfluxConnection): ServerSocket = {
      influx.getSubscriptionListener match {
        case Some(x) => x.setSoTimeout(100); x
        case None    => fail("No subscription listener obtained")
      }
    }

    "receive valid data" in {
      val influx = getInfluxSubscriber("receiveData")

      sendDataAnd(
        afterSend = { () =>
          println("Disabling flag")
          isRunning = false
        },
        withSend = { () =>
          val ssock = getListener(influx)
          println(s"Listening on ${ssock.getInetAddress}")
          var gotData = false

          isRunning = true
          while (isRunning) {
            try {
              val sock = ssock.accept
              sock.setSoTimeout(10)
              val reader = new BufferedReader(new InputStreamReader(sock.getInputStream))

              println("Receiving data")
              gotData = true

              val result = Stream
                .continually(reader.readLine)
                .takeWhile(line => line != null)
                .toList

              result.isEmpty shouldBe false
              result.exists(line => InfluxMeasurementFactory.createMeasurement(line).isDefined) shouldBe true

            } catch {
              case _: SocketTimeoutException =>
            }
          }

          gotData shouldBe true

          influx.stopSubscriptionListener(ssock)
        }
      )
    }
  }
}

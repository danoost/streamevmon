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

package nz.net.wand.streamevmon.connectors.esmond.schema

import com.fasterxml.jackson.annotation.{JsonIgnore, JsonProperty, JsonPropertyOrder}

@JsonPropertyOrder(alphabetic = true)
class HrefTimeSeriesEntry extends AbstractTimeSeriesEntry {
  @JsonProperty("val")
  val value: Map[String, String] = Map()

  @JsonIgnore
  lazy val hrefLocation: Option[String] = value.get("href")

  override def toString: String = {
    s"$timestamp: ${value.values}"
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[HrefTimeSeriesEntry]

  override def equals(other: Any): Boolean = other match {
    case that: HrefTimeSeriesEntry =>
      (that canEqual this) &&
        value == that.value &&
        hrefLocation == that.hrefLocation
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(value, hrefLocation)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

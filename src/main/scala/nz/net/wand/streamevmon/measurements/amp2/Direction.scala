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

package nz.net.wand.streamevmon.measurements.amp2

/** Certain measurements ([[Throughput]] and [[Udpstream]] at time of writing)
  * include a `direction` tag, which can only have the values `in` and `out`.
  * The companion object contains declarations for case objects representing
  * those values.
  */
sealed trait Direction

object Direction {
  case object In extends Direction {
    override def toString: String = "in"
  }

  case object Out extends Direction {
    override def toString: String = "out"
  }

  def apply(direction: String): Direction = direction.toLowerCase match {
    case "in" => In
    case "out" => Out
    case _ => throw new IllegalArgumentException(s"""Unknown direction supplied. Expected "in" or "out" but got $direction""")
  }
}

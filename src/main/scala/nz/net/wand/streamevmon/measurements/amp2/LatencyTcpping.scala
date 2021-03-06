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

import nz.net.wand.streamevmon.connectors.influx.LineProtocol

import java.time.Instant

case class LatencyTcpping(
  source: String,
  destination: String,
  test: String,
  time: Instant,
  dscp: String,
  family: String,
  packet_size: Long,
  port: Long,
  random     : Boolean,
  count      : Option[Long],
  error_code : Option[Long],
  error_type : Option[Long],
  icmpcode: Option[Long],
  icmptype   : Option[Long],
  loss       : Option[Long],
  rtt        : Option[Long],
) extends Latency(
  source, destination, test, time, dscp, family, count, error_code, error_type, icmpcode, icmptype, loss, rtt
) {
  override val tags: Seq[Any] = Seq(dscp, family, packet_size, port, random)
}

object LatencyTcpping {
  val measurementName: String = Latency.measurementName

  /** @see [[Amp2Measurement `Amp2Measurement.createFromLineProtocol`]] */
  def create(proto: LineProtocol): Option[LatencyTcpping] = {
    Some(LatencyTcpping(
      proto.tags("source"),
      proto.tags("destination"),
      proto.tags("test"),
      proto.time,
      proto.tags("dscp"),
      proto.tags("family"),
      proto.getTagAsLong("packet_size"),
      proto.getTagAsLong("port"),
      proto.getTagAsBoolean("random"),
      proto.getFieldAsLong("count"),
      proto.getFieldAsLong("error_code"),
      proto.getFieldAsLong("error_type"),
      proto.getFieldAsLong("icmpcode"),
      proto.getFieldAsLong("icmptype"),
      proto.getFieldAsLong("loss"),
      proto.getFieldAsLong("rtt"),
    ))
  }
}

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

package nz.net.wand.streamevmon.parameters

import nz.net.wand.streamevmon.{Logging, Perhaps}

/** A particular value for a parameter specified by a ParameterSpec.
  *
  * @param spec  The ParameterSpec for this instance.
  * @param value The particular value of the parameter. Note that for ordered
  *              types, this should be between `spec.min` and `spec.max`. This
  *              is not enforced in this class, so it is possible to create an
  *              invalid ParameterInstance.
  * @tparam T See [[ParameterSpec]] for details.
  */
case class ParameterInstance[T](
  spec: ParameterSpec[T],
  value: T
)(
  implicit val ordering: Perhaps[Ordering[T]]
) extends Serializable with Logging {
  val name: String = spec.name

  // No type annotation here because there's no way to guarantee typing... :(
  // If I could, it would be public and : T.
  private lazy val typedValue = value match {
    case v: String if spec.default.getClass == classOf[java.lang.Integer] || spec.default.getClass == classOf[Int] => v.toDouble.toInt
    case v: String if spec.default.getClass == classOf[java.lang.Long] || spec.default.getClass == classOf[Long] => v.toDouble.toLong
    case v: String if spec.default.getClass == classOf[java.lang.Double] || spec.default.getClass == classOf[Double] => v.toDouble
    case v: String if spec.default.getClass == classOf[java.lang.Float] || spec.default.getClass == classOf[Float] => v.toFloat
    case _ => value
  }

  // We need to go via typedValue here in order to correctly format integer
  // values - it's possible to be given a value of type String that doesn't
  // conform to the format required of a particular type, like an Int that has
  // a decimal point.
  lazy val asArg: Iterable[String] = Seq(s"--$name", typedValue.toString)

  override def toString: String = asArg.mkString(" ")

  def isValid: Boolean = {
    if (ordering.isDefined) {
      implicit val myOrdering: Ordering[T] = ordering.value.get
      import myOrdering.mkOrderingOps

      val minValid = spec.min match {
        case Some(min) => value >= min
        case None => true
      }
      val maxValid = spec.max match {
        case Some(max) => value <= max
        case None => true
      }
      minValid && maxValid
    }
    else {
      true
    }
  }
}

object ParameterInstance {
  /** A ParameterInstance with a corresponding Spec that only allows one value.
    */
  class Constant[T](override val value: T) extends ParameterInstance[T](
    new ParameterSpec.Constant(value),
    value
  )
}

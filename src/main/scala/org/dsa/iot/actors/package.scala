package org.dsa.iot

import scala.util.matching.Regex

/**
 * Helper types, constants, and implicits for benchmark actors.
 */
package object actors {

  /**
   * Interpolates strings to produce RegEx.
   */
  implicit class RegexContext(val sc: StringContext) extends AnyVal {
    def r = new Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
  }

}
package org.dsa.iot

import org.scalatest.{ BeforeAndAfterAll, Inside, MustMatchers, OptionValues, Suite, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures

/**
 * Base class for test specs.
 */
class AbstractSpec extends Suite with WordSpecLike with MustMatchers with OptionValues
  with BeforeAndAfterAll with ScalaFutures with Inside
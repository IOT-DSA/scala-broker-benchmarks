package org.dsa.iot.handshake

import org.scalatest._

/**
 * Test suite for LocalKeys.
 */
class LocalKeysSpec extends Suite with WordSpecLike with MustMatchers {

  "serialized-deserialized keys" should {
    "retain equality to the originals" in {
      val keys = LocalKeys.generate
      val newKeys = LocalKeys.deserialize(keys.serialize)

      keys.hashCode mustBe newKeys.hashCode
      keys mustEqual newKeys
    }
  }

}
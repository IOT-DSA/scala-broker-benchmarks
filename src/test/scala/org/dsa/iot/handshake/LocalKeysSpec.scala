package org.dsa.iot.handshake

import org.dsa.iot.AbstractSpec

/**
 * Test suite for LocalKeys.
 */
class LocalKeysSpec extends AbstractSpec {

  "serialized-deserialized keys" should {
    "retain equality to the originals" in {
      val keys = LocalKeys.generate
      val newKeys = LocalKeys.deserialize(keys.serialize)

      keys.hashCode mustBe newKeys.hashCode
      keys mustEqual newKeys
    }
  }

}
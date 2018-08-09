package org.dsa.iot.actors

/**
 * Similar to java AtomicInteger, but not thread safe,
 * optimized for single threaded execution by an actor.
 */
class IntCounter(init: Int = 0) {
  private var value = init

  @inline def get = value

  @inline def inc = {
    val result = value
    value += 1
    result
  }

  @inline def inc(count: Int) = {
    val start = value
    value += count
    (start until value)
  }
}
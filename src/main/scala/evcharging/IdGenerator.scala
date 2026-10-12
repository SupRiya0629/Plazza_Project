package evcharging

import java.util.concurrent.atomic.AtomicInteger

// Generates simple, readable, sequential IDs like DRV_001, STN_001, CON_001, SES_001.
// Each prefix has its own counter. Thread-safe via AtomicInteger.
object IdGenerator {
  private val counters = scala.collection.concurrent.TrieMap[String, AtomicInteger]()

  def next(prefix: String): String = {
    val counter = counters.getOrElseUpdate(prefix, new AtomicInteger(0))
    val number = counter.incrementAndGet()
    f"${prefix}_$number%03d"
  }
}

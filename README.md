# RingLogger

A high-performance, minimal-overhead logger designed to reduce runtime allocations and CPU contention — ideal for applications where every microsecond counts.

## Why RingLogger?

- **Optimized Memory Usage:** Utilizes a fixed-size ring buffer to efficiently manage log entries, avoiding unnecessary allocations.
- **Recyclable Buffers:** Uses a buffer pool to minimize allocation overhead and optimize memory usage.
- **Atomic Counters:** Leverages atomic counters for thread-safe producer-consumer synchronization.
- **Background Thread:** A dedicated background thread processes and writes log entries asynchronously to minimize impact on main application performance.
- **Lock-Free Logging:** Uses a ring-buffer-based lock-free algorithm to minimize contention.

> ⚠️ Intended for **high-throughput scenarios** where logging overhead must be minimized.  
> Not a replacement for general-purpose logging frameworks like Logback or Log4j.

---

## Key Features

- **Fixed-size ring buffer:** Efficiently handles log entries with minimal memory allocations.
- **Ring Buffer Recycling:** Uses a pool of reusable buffers to minimize overhead from object creation.
- **Thread-safe logging:** Ensures high concurrency with atomic counters and lock-free memory management.
- **Asynchronous Logging:** Processes log entries in the background on a dedicated thread for optimal performance.

---

## Example Usage

```java
RingLogger logger = RingLogger.getInstance();

// Log messages at different levels
logger.writeString(RingLogger.INFO, (byte) 1, "Something happened");
logger.writeBytes(RingLogger.DEBUG, (byte) 2, "More detailed message".getBytes());

// Set minimum log level (optional)
logger.setMinimumLogLevel(RingLogger.DEBUG);

// Shut down when done
logger.shutdown();
```

---

## Benchmarks
```
Benchmark                                    Mode  Cnt   Score    Error   Units
RingLoggerBenchmark.log4jLong               thrpt    5   1.061 ±  0.014  ops/ns
RingLoggerBenchmark.log4jMedium             thrpt    5   1.064 ±  0.012  ops/ns
RingLoggerBenchmark.log4jShort              thrpt    5   1.051 ±  0.022  ops/ns
RingLoggerBenchmark.ringLoggerLongBytes     thrpt    5   0.005 ±  0.001  ops/ns
RingLoggerBenchmark.ringLoggerLongString    thrpt    5   0.003 ±  0.001  ops/ns
RingLoggerBenchmark.ringLoggerMediumBytes   thrpt    5   0.022 ±  0.003  ops/ns
RingLoggerBenchmark.ringLoggerMediumString  thrpt    5   0.015 ±  0.001  ops/ns
RingLoggerBenchmark.ringLoggerShortBytes    thrpt    5   0.026 ±  0.003  ops/ns
RingLoggerBenchmark.ringLoggerShortString   thrpt    5   0.020 ±  0.001  ops/ns
RingLoggerBenchmark.slf4jLong               thrpt    5  ≈ 10⁻⁴           ops/ns
RingLoggerBenchmark.slf4jMedium             thrpt    5  ≈ 10⁻⁴           ops/ns
RingLoggerBenchmark.slf4jShort              thrpt    5  ≈ 10⁻⁴           ops/ns
RingLoggerBenchmark.systemOutLong           thrpt    5  ≈ 10⁻⁴           ops/ns
RingLoggerBenchmark.systemOutMedium         thrpt    5  ≈ 10⁻⁴           ops/ns
RingLoggerBenchmark.systemOutShort          thrpt    5  ≈ 10⁻⁴           ops/ns
```

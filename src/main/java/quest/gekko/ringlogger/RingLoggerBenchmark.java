package quest.gekko.ringlogger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.slf4j.LoggerFactory;
import quest.gekko.ringlogger.model.LogLevel;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class RingLoggerBenchmark {
    private static final byte COMPONENT_ID = 1;

    private RingLogger ringLogger;
    private static Logger log4jLogger;
    private static org.slf4j.Logger slf4jLogger;

    private String shortMessage;
    private String mediumMessage;
    private String longMessage;

    private byte[] shortBytes;
    private byte[] mediumBytes;
    private byte[] longBytes;

    @Setup(Level.Trial)
    public void setupTrial() {
        ringLogger = RingLogger.getInstance();
        ringLogger.setMinimumLogLevel(LogLevel.TRACE);
        log4jLogger = LogManager.getLogger(RingLoggerBenchmark.class);
        slf4jLogger = LoggerFactory.getLogger(RingLoggerBenchmark.class);

        shortMessage = generateMessage(32);
        mediumMessage = generateMessage(256);
        longMessage = generateMessage(2048);

        shortBytes = shortMessage.getBytes(StandardCharsets.UTF_8);
        mediumBytes = mediumMessage.getBytes(StandardCharsets.UTF_8);
        longBytes = longMessage.getBytes(StandardCharsets.UTF_8);
    }

    @TearDown(Level.Trial)
    public void teardownTrial() {
        ringLogger.shutdown();
    }

    // --- RingLogger Benchmarks ---

    @Benchmark
    public void ringLoggerShortString() {
        ringLogger.writeString(LogLevel.INFO, COMPONENT_ID, shortMessage);
    }

    @Benchmark
    public void ringLoggerMediumString() {
        ringLogger.writeString(LogLevel.INFO, COMPONENT_ID, mediumMessage);
    }

    @Benchmark
    public void ringLoggerLongString() {
        ringLogger.writeString(LogLevel.INFO, COMPONENT_ID, longMessage);
    }

    @Benchmark
    public void ringLoggerShortBytes() {
        ringLogger.writeBytes(LogLevel.INFO, COMPONENT_ID, shortBytes);
    }

    @Benchmark
    public void ringLoggerMediumBytes() {
        ringLogger.writeBytes(LogLevel.INFO, COMPONENT_ID, mediumBytes);
    }

    @Benchmark
    public void ringLoggerLongBytes() {
        ringLogger.writeBytes(LogLevel.INFO, COMPONENT_ID, longBytes);
    }

    // --- Log4j Benchmarks ---

    @Benchmark
    public void log4jShort() {
        log4jLogger.info(shortMessage);
    }

    @Benchmark
    public void log4jMedium() {
        log4jLogger.info(mediumMessage);
    }

    @Benchmark
    public void log4jLong() {
        log4jLogger.info(longMessage);
    }

    // --- SLF4J Benchmarks ---

    @Benchmark
    public void slf4jShort() {
        slf4jLogger.info(shortMessage);
    }

    @Benchmark
    public void slf4jMedium() {
        slf4jLogger.info(mediumMessage);
    }

    @Benchmark
    public void slf4jLong() {
        slf4jLogger.info(longMessage);
    }

    // --- println Baseline ---

    @Benchmark
    public void systemOutShort() {
        System.out.println(shortMessage);
    }

    @Benchmark
    public void systemOutMedium() {
        System.out.println(mediumMessage);
    }

    @Benchmark
    public void systemOutLong() {
        System.out.println(longMessage);
    }

    private String generateMessage(int size) {
        return IntStream.range(0, size)
                .mapToObj(i -> String.valueOf((char) ('a' + (i % 26))))
                .collect(Collectors.joining());
    }

    public static void main(String[] args) throws RunnerException {
        final Options options = new OptionsBuilder()
                .include(RingLoggerBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(options).run();
    }
}
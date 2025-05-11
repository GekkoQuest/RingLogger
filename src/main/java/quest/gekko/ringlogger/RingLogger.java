package quest.gekko.ringlogger;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class RingLogger {
    // Log levels
    public static final byte TRACE = 1;
    public static final byte DEBUG = 2;
    public static final byte INFO = 3;
    public static final byte WARN = 4;
    public static final byte ERROR = 5;

    private static final RingLogger INSTANCE = new RingLogger();

    // Ring buffer configuration
    private static final int BUFFER_SIZE = 4096;
    private static final int RING_SIZE = 16384;
    private static final int RING_MASK = RING_SIZE - 1; // Bitmask for fast modulo
    private static final int THREAD_PRIORITY = Thread.MAX_PRIORITY - 1;

    // Thread-local scratch space for building log entries
    private static final ThreadLocal<ByteBuffer> THREAD_LOCAL_BUFFER =
            ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(BUFFER_SIZE));

    // Shared ring buffer and pre-allocated entry buffers to reduce GC pressure
    private final ByteBuffer[] ringBuffer = new ByteBuffer[RING_SIZE];
    private final ByteBuffer[] bufferPool = new ByteBuffer[RING_SIZE];

    // Sequence counters for producer and consumer positions
    private final PaddedAtomicLong producerSequence = new PaddedAtomicLong(0);
    private final PaddedAtomicLong consumerSequence = new PaddedAtomicLong(0);

    private final Thread loggerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Consumer<ByteBuffer> logWriter;

    // Global minimum log level (filtering)
    private volatile byte minimumLogLevel = INFO;

    // Log entry format: timestamp, level, component ID, length, message
    private static final int TIMESTAMP_SIZE = Long.BYTES;
    private static final int LEVEL_SIZE = Byte.BYTES;
    private static final int COMPONENT_ID_SIZE = Byte.BYTES;
    private static final int MESSAGE_LENGTH_SIZE = Integer.BYTES;

    // Offsets for decoding log entries
    private static final int TIMESTAMP_OFFSET = 0;
    private static final int LEVEL_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
    private static final int COMPONENT_ID_OFFSET = LEVEL_OFFSET + LEVEL_SIZE;
    private static final int MESSAGE_LENGTH_OFFSET = COMPONENT_ID_OFFSET + COMPONENT_ID_SIZE;
    private static final int MESSAGE_OFFSET = MESSAGE_LENGTH_OFFSET + MESSAGE_LENGTH_SIZE;

    private RingLogger() {
        // Initialize reusable buffer pool to avoid allocation at runtime
        for (int i = 0; i < RING_SIZE; i++) {
            bufferPool[i] = ByteBuffer.allocateDirect(BUFFER_SIZE);
        }

        this.logWriter = defaultLogWriter();
        this.loggerThread = startLoggerThread();
    }

    // Main public API: write a String log message
    public void writeString(final byte level, final byte componentId, final String message) {
        if (level < minimumLogLevel) return;

        try {
            final ByteBuffer buffer = THREAD_LOCAL_BUFFER.get();
            buffer.clear();

            // Encode log fields into buffer
            buffer.putLong(System.nanoTime());
            buffer.put(level);
            buffer.put(componentId);

            final byte[] messageBytes = message.getBytes();
            final int availableSpace = buffer.capacity() - buffer.position() - 4;
            final int messageLength = Math.min(messageBytes.length, availableSpace);

            buffer.putInt(messageLength);
            buffer.put(messageBytes, 0, messageLength);
            buffer.flip();

            publishLogEntry(buffer);
        } catch (final Exception e) {
            System.err.println("Logging failure: " + e.getMessage());
        }
    }

    // Overload for logging raw byte messages
    public void writeBytes(final byte level, final byte componentId, final byte[] messageBytes) {
        if (level < minimumLogLevel) return;

        try {
            final ByteBuffer buffer = THREAD_LOCAL_BUFFER.get();
            buffer.clear();

            buffer.putLong(System.nanoTime());
            buffer.put(level);
            buffer.put(componentId);

            final int availableSpace = buffer.capacity() - buffer.position() - 4;
            final int messageLength = Math.min(messageBytes.length, availableSpace);

            buffer.putInt(messageLength);
            buffer.put(messageBytes, 0, messageLength);
            buffer.flip();

            publishLogEntry(buffer);
        } catch (final Exception e) {
            System.err.println("Logging failure: " + e.getMessage());
        }
    }

    // Places a log buffer into the ring for the background thread to consume
    private void publishLogEntry(final ByteBuffer buffer) {
        final long sequence = producerSequence.get();

        // Drop oldest entry if ring is full
        if (sequence - consumerSequence.get() >= RING_SIZE) {
            consumerSequence.incrementAndGet();
        }

        final int index = (int) (sequence & RING_MASK);
        final ByteBuffer entryBuffer = bufferPool[index];
        entryBuffer.clear();
        entryBuffer.put(buffer);
        entryBuffer.flip();

        ringBuffer[index] = entryBuffer;
        producerSequence.incrementAndGet();
    }

    // Clean shutdown hook for logger thread
    public void shutdown() {
        running.set(false);
        loggerThread.interrupt();

        try {
            loggerThread.join(1000);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Background thread continuously flushes entries to logWriter
    private Thread startLoggerThread() {
        final Thread thread = new Thread(() -> {
            long nextSequence = consumerSequence.get();

            while (running.get() || nextSequence < producerSequence.get()) {
                try {
                    if (nextSequence < producerSequence.get()) {
                        final int index = (int) (nextSequence & RING_MASK);
                        final ByteBuffer entry = ringBuffer[index];

                        if (entry != null) {
                            logWriter.accept(entry);
                            nextSequence++;
                            consumerSequence.set(nextSequence);
                        }
                    } else {
                        // No new logs yet; yield briefly
                        LockSupport.parkNanos(1);
                    }
                } catch (final Exception e) {
                    System.err.println("Logger thread error: " + e.getMessage());
                }
            }
        });

        thread.setName("ringlogger-worker");
        thread.setPriority(THREAD_PRIORITY);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    // Default log output logic to console
    private Consumer<ByteBuffer> defaultLogWriter() {
        return buffer -> {
            final long timestamp = buffer.getLong(TIMESTAMP_OFFSET);
            final byte level = buffer.get(LEVEL_OFFSET);
            final byte componentId = buffer.get(COMPONENT_ID_OFFSET);
            final int messageLength = buffer.getInt(MESSAGE_LENGTH_OFFSET);

            final byte[] messageBytes = new byte[messageLength];
            buffer.position(MESSAGE_OFFSET);
            buffer.get(messageBytes);

            final String logMessage = String.format("[%d] [%s] [Component-%d] %s",
                    timestamp, levelToString(level), componentId, new String(messageBytes));

            if (level >= ERROR) {
                System.err.println(logMessage);
            } else {
                System.out.println(logMessage);
            }
        };
    }

    // Human-readable log level name
    private static String levelToString(final byte level) {
        return switch (level) {
            case TRACE -> "TRACE";
            case DEBUG -> "DEBUG";
            case INFO -> "INFO";
            case WARN -> "WARN";
            case ERROR -> "ERROR";
            default -> "UNKNOWN";
        };
    }

    public static RingLogger getInstance() {
        return INSTANCE;
    }

    // Dynamically adjust log filtering at runtime
    public void setMinimumLogLevel(final byte level) {
        this.minimumLogLevel = level;
    }
}

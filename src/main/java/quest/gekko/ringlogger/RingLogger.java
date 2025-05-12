package quest.gekko.ringlogger;

import quest.gekko.ringlogger.model.LogLevel;
import quest.gekko.ringlogger.writer.LogWriter;
import quest.gekko.ringlogger.util.PaddedAtomicLong;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public final class RingLogger {
    // Ring buffer configuration
    private static final int BUFFER_SIZE = 4096;
    private static final int RING_SIZE = 16384;
    private static final int RING_MASK = RING_SIZE - 1; // Bitmask for fast modulo
    private static final int THREAD_PRIORITY = Thread.MAX_PRIORITY - 1;

    // Singleton instance
    private static final RingLogger INSTANCE = new RingLogger();

    // Thread-local scratch space for building log entries
    private static final ThreadLocal<ByteBuffer> THREAD_LOCAL_BUFFER =
            ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(BUFFER_SIZE));

    // Shared ring buffer and pre-allocated entry buffers to reduce GC pressure
    private final ByteBuffer[] ringBuffer = new ByteBuffer[RING_SIZE];
    private final ByteBuffer[] bufferPool = new ByteBuffer[RING_SIZE];

    // Sequence counters for producer and consumer positions
    private final PaddedAtomicLong producerSequence = new PaddedAtomicLong(0);
    private final PaddedAtomicLong consumerSequence = new PaddedAtomicLong(0);

    // Logging thread and control
    private final Thread loggerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final LogWriter logWriter;

    // Global minimum log level (filtering)
    private volatile LogLevel minimumLogLevel = LogLevel.INFO;

    private RingLogger() {
        // Initialize reusable buffer pool to avoid allocation at runtime
        for (int i = 0; i < RING_SIZE; i++) {
            bufferPool[i] = ByteBuffer.allocateDirect(BUFFER_SIZE);
        }

        this.logWriter = LogWriter.consoleWriter();
        this.loggerThread = startLoggerThread();
    }

    /**
     * Write a string log message.
     *
     * @param level log level
     * @param componentId component identifier
     * @param message log message
     */
    public void writeString(final LogLevel level, final byte componentId, final String message) {
        writeMessage(level, componentId, message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Write a byte array log message.
     *
     * @param level log level
     * @param componentId component identifier
     * @param messageBytes log message bytes
     */
    public void writeBytes(final LogLevel level, final byte componentId, final byte[] messageBytes) {
        writeMessage(level, componentId, messageBytes);
    }

    /**
     * Internal method to write log messages.
     *
     * @param level log level
     * @param componentId component identifier
     * @param messageBytes log message bytes
     */
    private void writeMessage(final LogLevel level, final byte componentId, final byte[] messageBytes) {
        if (level.getValue() < minimumLogLevel.getValue()) return;

        try {
            final ByteBuffer buffer = THREAD_LOCAL_BUFFER.get();
            buffer.clear();

            // Encode log fields into buffer
            buffer.putLong(System.nanoTime());
            buffer.put(level.getValue());
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

    /**
     * Places a log buffer into the ring for the background thread to consume.
     *
     * @param buffer ByteBuffer containing log entry
     */
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

    /**
     * Starts the background logger thread.
     *
     * @return configured logging thread
     */
    private Thread startLoggerThread() {
        final Thread thread = new Thread(() -> {
            long nextSequence = consumerSequence.get();

            while (running.get() || nextSequence < producerSequence.get()) {
                try {
                    if (nextSequence < producerSequence.get()) {
                        final int index = (int) (nextSequence & RING_MASK);
                        final ByteBuffer entry = ringBuffer[index];

                        if (entry != null) {
                            logWriter.write(entry);
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

    /**
     * Performs a clean shutdown of the logger thread.
     */
    public void shutdown() {
        running.set(false);
        loggerThread.interrupt();

        try {
            loggerThread.join(1000);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Retrieves the singleton RingLogger instance.
     *
     * @return RingLogger singleton instance
     */
    public static RingLogger getInstance() {
        return INSTANCE;
    }

    /**
     * Dynamically adjust log filtering at runtime.
     *
     * @param level minimum log level to capture
     */
    public void setMinimumLogLevel(final LogLevel level) {
        this.minimumLogLevel = level;
    }
}
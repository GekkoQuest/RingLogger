package quest.gekko.ringlogger.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class LogEntry {
    // Log entry format constants
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

    private final long timestamp;
    private final LogLevel level;
    private final byte componentId;
    private final String message;

    /**
     * Constructs a log entry from the given ByteBuffer.
     *
     * @param buffer ByteBuffer containing log entry data
     */
    public LogEntry(final ByteBuffer buffer) {
        this.timestamp = buffer.getLong(TIMESTAMP_OFFSET);
        this.level = LogLevel.fromByte(buffer.get(LEVEL_OFFSET));
        this.componentId = buffer.get(COMPONENT_ID_OFFSET);

        final int messageLength = buffer.getInt(MESSAGE_LENGTH_OFFSET);
        final byte[] messageBytes = new byte[messageLength];
        buffer.position(MESSAGE_OFFSET);
        buffer.get(messageBytes);

        this.message = new String(messageBytes, StandardCharsets.UTF_8);
    }

    /**
     * Formats the log entry as a human-readable string.
     *
     * @return formatted log entry string
     */
    public String format() {
        return String.format("[%d] [%s] [Component-%d] %s",
                timestamp, level, componentId, message);
    }


    public long getTimestamp() {
        return timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public byte getComponentId() {
        return componentId;
    }

    public String getMessage() {
        return message;
    }
}
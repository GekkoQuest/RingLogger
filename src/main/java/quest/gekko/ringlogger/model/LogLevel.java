package quest.gekko.ringlogger.model;

public enum LogLevel {
    TRACE((byte) 1),
    DEBUG((byte) 2),
    INFO((byte) 3),
    WARN((byte) 4),
    ERROR((byte) 5);

    private final byte value;

    LogLevel(final byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    /**
     * Convert byte value to LogLevel.
     *
     * @param value the byte value of the log level
     * @return the corresponding LogLevel
     */
    public static LogLevel fromByte(final byte value) {
        for (LogLevel level : values()) {
            if (level.value == value) {
                return level;
            }
        }

        throw new IllegalArgumentException("Unknown log level: " + value);
    }

    /**
     * Convert LogLevel to human-readable string.
     *
     * @return string representation of log level
     */
    @Override
    public String toString() {
        return name();
    }
}
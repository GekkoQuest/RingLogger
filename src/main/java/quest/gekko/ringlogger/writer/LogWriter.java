package quest.gekko.ringlogger.writer;

import quest.gekko.ringlogger.model.LogEntry;
import quest.gekko.ringlogger.model.LogLevel;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface LogWriter {
    /**
     * Writes a log entry from the given ByteBuffer.
     *
     * @param logEntry the log entry buffer
     */
    void write(final ByteBuffer logEntry);

    /**
     * Default console log writer.
     *
     * @return a LogWriter that writes to console
     */
    static LogWriter consoleWriter() {
        return buffer -> {
            final LogEntry entry = new LogEntry(buffer);

            if (entry.getLevel().getValue() >= LogLevel.ERROR.getValue()) {
                System.err.println(entry.format());
            } else {
                System.out.println(entry.format());
            }
        };
    }
}
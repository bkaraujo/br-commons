package br.commons.logger;

import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogRecordTest {

    @Test
    void constructor_storesFields() {
        val ts = System.currentTimeMillis();
        val record = new LogRecord(ts, LogLevel.INFO, () -> "hello");

        assertEquals(ts, record.timestamp());
        assertEquals(LogLevel.INFO, record.level());
        assertEquals(Thread.currentThread().getName(), record.threadName());
        assertNull(record.callerClass());
    }

    @Test
    void userMessage_returnsMsgFromSupplier() {
        val record = new LogRecord(0L, LogLevel.DEBUG, () -> "test message");
        assertEquals("test message", record.userMessage());
    }

    @Test
    void fullConstructor_setsCallerClass() {
        val frame = new br.commons.Meta.StackFrame("br.Example", "method", 42);
        val record = new LogRecord(0L, LogLevel.WARN, "main", () -> "msg", frame);

        assertEquals("main", record.threadName());
        assertEquals(frame, record.callerClass());
        assertEquals(LogLevel.WARN, record.level());
    }

    @Test
    void userMessage_lazyEvaluation() {
        int[] callCount = {0};
        val record = new LogRecord(0L, LogLevel.INFO, () -> {
            callCount[0]++;
            return "msg";
        });

        assertEquals(0, callCount[0]);
        record.userMessage();
        assertEquals(1, callCount[0]);
    }
}

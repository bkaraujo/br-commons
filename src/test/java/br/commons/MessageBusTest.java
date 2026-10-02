package br.commons;

import br.commons.message.Message;
import br.commons.message.MessageListener;
import br.commons.message.MessageResult;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MessageBusTest {

    // ── test message types (unique per test to avoid static-state interference) ──

    record OrderPlaced(String orderId) implements Message {}
    record OrderCancelled(String orderId) implements Message {}
    record InventoryUpdated(int quantity) implements Message {}
    record PaymentReceived(double amount) implements Message {}
    record ConsumedMsg() implements Message {}

    // ── subscribe ─────────────────────────────────────────────────────────────

    @Test
    void subscribe_validListener_returnsCount() {
        var handler = new Object() {
            @MessageListener
            public MessageResult onOrder(OrderPlaced msg) {
                return MessageResult.AVAILABLE;
            }
        };
        val count = MessageBus.subscribe(handler);
        assertTrue(count >= 1);
    }

    @Test
    void subscribe_noAnnotatedMethods_returnsZero() {
        var handler = new Object() {
            public void notAListener(OrderCancelled msg) {}
        };
        val count = MessageBus.subscribe(handler);
        assertEquals(0, count);
    }

    @Test
    void subscribe_wrongReturnType_notRegistered() {
        var handler = new Object() {
            @MessageListener
            public void onEvent(InventoryUpdated msg) {}
        };
        val count = MessageBus.subscribe(handler);
        assertEquals(0, count);
    }

    @Test
    void subscribe_wrongParamCount_notRegistered() {
        var handler = new Object() {
            @MessageListener
            public MessageResult onEvent(PaymentReceived a, PaymentReceived b) {
                return MessageResult.AVAILABLE;
            }
        };
        val count = MessageBus.subscribe(handler);
        assertEquals(0, count);
    }

    // ── submit / dispatch ─────────────────────────────────────────────────────

    @Test
    void submit_dispatchesToSubscriber() {
        val received = new AtomicBoolean(false);

        record UniqueMsg1() implements Message {}

        var handler = new Object() {
            @MessageListener
            public MessageResult on(UniqueMsg1 msg) {
                received.set(true);
                return MessageResult.AVAILABLE;
            }
        };

        MessageBus.subscribe(handler);
        MessageBus.submit(new UniqueMsg1());
        assertTrue(received.get());
    }

    @Test
    void submit_neverInterruptsFanOut_regardlessOfReturnValue() {
        val callCount = new AtomicInteger(0);

        record UniqueMsg2() implements Message {}

        var first = new Object() {
            @MessageListener
            public MessageResult on(UniqueMsg2 msg) {
                callCount.incrementAndGet();
                return MessageResult.CONSUMED;
            }
        };
        var second = new Object() {
            @MessageListener
            public MessageResult on(UniqueMsg2 msg) {
                callCount.incrementAndGet();
                return MessageResult.AVAILABLE;
            }
        };

        MessageBus.subscribe(first);
        MessageBus.subscribe(second);
        MessageBus.submit(new UniqueMsg2());

        // Every subscriber runs, whatever it returns — no "first handler wins" semantics.
        assertEquals(2, callCount.get());
    }

    @Test
    void submit_listenerException_propagatesToCaller() {
        record UniqueMsg4() implements Message {}

        var handler = new Object() {
            @MessageListener
            public MessageResult on(UniqueMsg4 msg) {
                throw new IllegalStateException("boom");
            }
        };

        MessageBus.subscribe(handler);
        assertThrows(IllegalStateException.class, () -> MessageBus.submit(new UniqueMsg4()));
    }

    @Test
    void submit_noSubscriber_noException() {
        record OrphanMsg() implements Message {}
        assertDoesNotThrow(() -> MessageBus.submit(new OrphanMsg()));
    }

    @Test
    void submit_passesMessageToHandler() {
        val received = new AtomicInteger(0);

        record UniqueMsg3(int value) implements Message {}

        var handler = new Object() {
            @MessageListener
            public MessageResult on(UniqueMsg3 msg) {
                received.set(msg.value());
                return MessageResult.AVAILABLE;
            }
        };

        MessageBus.subscribe(handler);
        MessageBus.submit(new UniqueMsg3(42));
        assertEquals(42, received.get());
    }
}

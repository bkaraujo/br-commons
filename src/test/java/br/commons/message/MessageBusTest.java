package br.commons.message;

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

    // ── hierarchy, identity, concurrency ──────────────────────────────────────

    interface Family extends Message {}

    @Test
    void submit_dispatchesToInterfaceListeners_generalFirst() {
        record Member() implements Family {}
        val order = new StringBuilder();

        var handler = new Object() {
            @MessageListener
            public MessageResult onMember(Member msg) { order.append("M"); return MessageResult.CONSUMED; }
            @MessageListener
            public MessageResult onFamily(Family msg) { order.append("F"); return MessageResult.CONSUMED; }
        };

        MessageBus.subscribe(handler);
        MessageBus.submit(new Member());
        assertEquals("FM", order.toString());
    }

    @Test
    void subscribe_distinctInstancesOfSameClass_bothReceive() {
        record UniqueMsg5() implements Message {}
        val count = new AtomicInteger();

        class Counter {
            @MessageListener
            public MessageResult on(UniqueMsg5 msg) { count.incrementAndGet(); return MessageResult.CONSUMED; }
        }

        val first = new Counter();
        assertEquals(1, MessageBus.subscribe(first));
        assertEquals(1, MessageBus.subscribe(new Counter()));
        assertEquals(1, MessageBus.subscribe(first)); // same instance: ignored
        MessageBus.submit(new UniqueMsg5());
        assertEquals(2, count.get());
    }

    @Test
    void subscribe_concurrentWithSubmit_everyLateListenerSeen() throws Exception {
        record UniqueMsg6() implements Message {}
        val count = new AtomicInteger();
        val listeners = 200;

        class Counter {
            @MessageListener
            public MessageResult on(UniqueMsg6 msg) { count.incrementAndGet(); return MessageResult.CONSUMED; }
        }

        val publisher = new Thread(() -> {
            for (var i = 0; i < 5_000; i++) { MessageBus.submit(new UniqueMsg6()); }
        });
        publisher.start();
        for (var i = 0; i < listeners; i++) { MessageBus.subscribe(new Counter()); }
        publisher.join();

        count.set(0);
        MessageBus.submit(new UniqueMsg6());
        assertEquals(listeners, count.get());
    }

    // ── unsubscribe ───────────────────────────────────────────────────────────

    @Test
    void unsubscribe_instance_stopsOnlyThatInstance() {
        record UniqueMsg7() implements Message {}
        val count = new AtomicInteger();

        class Counter {
            @MessageListener
            public MessageResult on(UniqueMsg7 msg) { count.incrementAndGet(); return MessageResult.CONSUMED; }
            @MessageListener
            public MessageResult onAny(Message msg) { return MessageResult.AVAILABLE; }
        }

        val first = new Counter();
        MessageBus.subscribe(first);
        MessageBus.subscribe(new Counter());

        assertEquals(2, MessageBus.unsubscribe(first));
        assertEquals(0, MessageBus.unsubscribe(first)); // already gone
        MessageBus.submit(new UniqueMsg7());
        assertEquals(1, count.get());

        assertEquals(2, MessageBus.subscribe(first)); // can subscribe again
        MessageBus.submit(new UniqueMsg7());
        assertEquals(3, count.get());
    }

    public static class ByClass {
        static final AtomicInteger COUNT = new AtomicInteger();
        record ByClassMsg() implements Message {}

        @MessageListener
        public MessageResult on(ByClassMsg msg) { COUNT.incrementAndGet(); return MessageResult.CONSUMED; }
    }

    @Test
    void unsubscribe_class_removesInstanceCreatedBySubscribe() {
        assertEquals(1, MessageBus.subscribe(ByClass.class));
        assertEquals(1, MessageBus.subscribe(ByClass.class)); // resubscribe: same count
        assertEquals(1, MessageBus.unsubscribe(ByClass.class));
        assertEquals(0, MessageBus.unsubscribe(ByClass.class));

        MessageBus.submit(new ByClass.ByClassMsg());
        assertEquals(0, ByClass.COUNT.get());
    }
}

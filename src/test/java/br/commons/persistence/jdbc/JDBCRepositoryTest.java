package br.commons.persistence.jdbc;

import br.commons.persistence.jdbc.primitives.JDBCResultSet;
import br.commons.cdi.Context;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Schema-qualified SQL (C3) and the native upsert of {@code save} (C4), on H2. */
class JDBCRepositoryTest {

    record Item(long id, String name, String created) {}

    private static final class ItemRepository extends JDBCRepository<Item> {
        ItemRepository() {
            super("SALES", "ITEM");
        }

        @Override
        protected Item map(JDBCResultSet rs) {
            return new Item(rs.getLong("ID").get(), rs.getString("NAME").get(), rs.getString("CREATED").get());
        }

        @Override
        protected Map<String, @Nullable Object> values(Item item) {
            val values = new LinkedHashMap<String, @Nullable Object>();
            values.put("ID", item.id());
            values.put("NAME", item.name());
            values.put("CREATED", item.created());
            return values;
        }

        @Override
        protected Set<String> updateImmutableColumns() {
            return Set.of("CREATED");
        }
    }

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        val p = new JDBCProperties();
        p.name("repo");
        p.driver("org.h2.Driver");
        p.url("jdbc:h2:mem:repo" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        p.username("sa");
        p.password("");
        p.autoCommit(false);
        p.validationQuery("SELECT 1");
        p.minPoolSize(1);
        p.maxPoolSize(10);
        p.connectionTimeout(5000);
        dataSource = new DataSource(p);
        dataSource.execute("CREATE SCHEMA SALES");
        dataSource.execute("CREATE TABLE SALES.ITEM (ID BIGINT PRIMARY KEY, NAME VARCHAR(50), CREATED VARCHAR(20))");
        // Same name in the default schema: unqualified SQL would hit this one.
        dataSource.execute("CREATE TABLE ITEM (ID BIGINT PRIMARY KEY, NAME VARCHAR(50), CREATED VARCHAR(20))");
        dataSource.execute("INSERT INTO ITEM VALUES (99, 'wrong schema', 'x')");
        Context.set(DataSource.class, () -> dataSource);
    }

    @AfterEach
    void tearDown() {
        Context.remove(DataSource.class);
        dataSource.close();
    }

    /** Regression (C3): the schema only filtered the introspection; SQL went to the default schema. */
    @Test
    void generatedSqlUsesTheSchema() {
        val repository = new ItemRepository();
        assertEquals("SALES.ITEM", repository.qualifiedTable());
        assertEquals(0, repository.count());
        assertTrue(repository.findById(99L).isEmpty());

        repository.insert(new Item(1, "pen", "monday"));
        assertEquals(List.of(new Item(1, "pen", "monday")), repository.findAll());
        assertTrue(repository.exists(1L));
        repository.deleteById(1L);
        assertEquals(0, repository.count());
    }

    /** Regression (C4): save inserts, then updates, keeping the immutable column. */
    @Test
    void saveIsAnUpsertThatKeepsImmutableColumns() {
        val repository = new ItemRepository();
        repository.save(new Item(1, "pen", "monday"));
        repository.save(new Item(1, "pencil", "tuesday"));
        assertEquals(List.of(new Item(1, "pencil", "monday")), repository.findAll());
    }

    /** Regression (C4): SELECT + INSERT lost the race — two threads saw "absent" and both inserted. */
    @Test
    void concurrentSavesOfTheSameKeyDoNotCollide() throws Exception {
        val repository = new ItemRepository();
        val start = new CountDownLatch(1);
        val errors = new ArrayList<Throwable>();
        val threads = new ArrayList<Thread>();
        for (int t = 0; t < 8; t++) {
            val name = "writer" + t;
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 20; i++) repository.save(new Item(7, name, "first"));
                } catch (Throwable e) {
                    synchronized (errors) { errors.add(e); }
                }
            }));
        }
        start.countDown();
        for (val thread : threads) thread.join(20_000);

        assertEquals(List.of(), errors);
        assertEquals(1, repository.count());
    }
}

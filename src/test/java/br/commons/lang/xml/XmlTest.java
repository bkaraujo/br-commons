package br.commons.lang.xml;

import br.commons.lang.Xml;
import br.commons.serializer.Serializer;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class XmlTest {

    enum Kind { DEBIT, CREDIT }

    record Entry(UUID id, String description, Kind kind, int cents) {}

    record Statement(String account, List<Entry> entries) {}

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void writesAndReadsRecords() {
        val xml = Xml.write(new Entry(ID, "PÃO & CAFÉ <1>", Kind.DEBIT, 1250), "entry");
        assertEquals("<entry><id>" + ID + "</id><description>PÃO &amp; CAFÉ &lt;1&gt;</description>"
                + "<kind>DEBIT</kind><cents>1250</cents></entry>", xml);
        assertEquals(new Entry(ID, "PÃO & CAFÉ <1>", Kind.DEBIT, 1250), Xml.from(xml).as(Entry.class));
    }

    @Test
    void listUnderAKeyRepeatsTheElement() {
        val statement = new Statement("123", List.of(new Entry(ID, "A", Kind.CREDIT, 1), new Entry(ID, "B", Kind.DEBIT, 2)));
        val xml = Xml.write(statement);
        assertTrue(xml.startsWith("<root><account>123</account><entries><id>"), xml);
        assertEquals(statement, Xml.from(xml).as(Statement.class));
    }

    @Test
    void listNotUnderAKeyBecomesItems() {
        val xml = Xml.write(Arrays.asList("a", null, "b"));
        assertEquals("<root><item>a</item><item/><item>b</item></root>", xml);
        val read = Xml.from(xml);
        assertEquals("a", read.asString("item.0"));
        assertNull(read.asString("item.1"));
        assertEquals("b", read.asString("item.2"));
    }

    /** Attributes and elements are both keys; text beside them is "value"; repeated siblings are a list. */
    @Test
    void readsAttributesTextAndRepeatedSiblings() {
        val xml = Xml.from("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- configuration -->
                <server port="8080" debug="true">
                  <host>localhost</host>
                  <tag>a</tag>
                  <tag><![CDATA[<b>]]></tag>
                  <limit unit="MB">512</limit>
                  <empty/>
                </server>""");
        assertEquals(8080, xml.asInt("port"));
        assertEquals(true, xml.asBoolean("debug"));
        assertEquals("localhost", xml.asString("host"));
        assertEquals("<b>", xml.asString("tag.1"));
        assertEquals(2, xml.list("tag").size());
        assertEquals("MB", xml.asString("limit.unit"));
        assertEquals(512, xml.asInt("limit." + XmlReader.TEXT));
        assertTrue(xml.contains("empty"));
        assertNull(xml.asString("empty"));
        assertEquals(7, xml.asInt("missing", 7));
    }

    @Test
    void namespacePrefixesAreDropped() {
        val xml = Xml.from("<a:doc xmlns:a=\"urn:a\" xmlns:b=\"urn:b\" b:id=\"1\"><a:name>x</a:name></a:doc>");
        assertEquals("1", xml.asString("id"));
        assertEquals("x", xml.asString("name"));
    }

    @Test
    void scalarRootIsTheDocument() {
        assertEquals("<root>x</root>", Xml.write("x"));
        assertTrue(Xml.from("<root/>").isEmpty());
        assertEquals("1", Xml.from("<!DOCTYPE root><root><a>1</a></root>").asString("a"));
    }

    @Test
    void malformedTextThrowsWithPosition() {
        val e = assertThrows(IllegalArgumentException.class, () -> Xml.from("<a><b></a>"));
        assertNotNull(e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Xml.from("<a/><b/>"));
        assertThrows(IllegalArgumentException.class, () -> Xml.from(""));
    }

    @Test
    void externalEntitiesAreRejected(@TempDir Path dir) throws IOException {
        val secret = Files.writeString(dir.resolve("secret.txt"), "SECRET");
        val xml = "<!DOCTYPE a [<!ENTITY x SYSTEM \"" + secret.toUri() + "\">]><a>&x;</a>";
        assertThrows(IllegalArgumentException.class, () -> Xml.from(xml));
    }

    @Test
    void invalidNamesAndCharactersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Xml.write(Map.of("1st", "x")));
        assertThrows(IllegalArgumentException.class, () -> Xml.write(Map.of("a b", "x")));
        assertThrows(IllegalArgumentException.class, () -> Xml.write("\u0001"));
    }

    @Test
    void savesKeepingTheRootAndReadsBack(@TempDir Path dir) throws IOException {
        val path = Files.writeString(dir.resolve("app.xml"), "<config><server><port>8080</port></server></config>");
        val cfg = Xml.from(path);
        assertEquals(8080, cfg.asInt("server.port"));

        Xml.save(Serializer.builder(cfg).put("server.port", 9090).append("users", "name", "ana").build());

        val saved = Files.readString(path);
        assertTrue(saved.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<config>\n  <server>\n    <port>9090</port>"), saved);
        val reread = Xml.from(path);
        assertEquals(9090, reread.asInt("server.port"));
        assertEquals("ana", reread.asString("users.name"));
    }

    @Test
    void missingFileIsEmpty(@TempDir Path dir) {
        assertTrue(Xml.from(dir.resolve("none.xml")).isEmpty());
        assertTrue(Xml.from((Path) null).isEmpty());
        assertTrue(Xml.fromClasspath("none/none.xml").isEmpty());
    }
}

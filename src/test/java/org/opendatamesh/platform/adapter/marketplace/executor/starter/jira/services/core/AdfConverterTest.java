package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// BDMD-5340: Jira marketplace executor
class AdfConverterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AdfConverter converter = new AdfConverter(mapper);

    /**
     * Scenario: REST v3 converts the description to ADF
     *   Given the connection uses REST API version 3 on Jira Cloud
     *   When an issue is created with a two-paragraph description
     *   Then the description is sent as an ADF document with two paragraphs
     * (converter level: two paragraphs, the first one with a line break, survive a round trip)
     */
    @Test
    void whenTwoParagraphsWithLineBreakThenRoundTrips() {
        JsonNode adf = converter.toAdf("line one\nline two\n\nsecond paragraph");

        assertEquals("doc", adf.get("type").asText());
        assertEquals(1, adf.get("version").asInt());
        assertEquals(2, adf.get("content").size());
        assertEquals("hardBreak", adf.at("/content/0/content/1/type").asText());
        assertEquals("line one\nline two\nsecond paragraph", converter.toText(adf));
    }

    @Test
    void whenTextEmptyThenEmptyDocument() {
        assertEquals(0, converter.toAdf("").get("content").size());
        assertEquals(0, converter.toAdf(null).get("content").size());
        assertEquals("", converter.toText(null));
    }

    @Test
    void whenTextualNodeThenReturnedAsIs() throws Exception {
        assertEquals("plain", converter.toText(mapper.readTree("\"plain\"")));
    }

    @Test
    void whenUnknownNodeTypesThenContentTraversed() throws Exception {
        JsonNode node = mapper.readTree("""
                {"type":"doc","content":[{"type":"panel","content":[{"type":"paragraph","content":[{"type":"text","text":"inside"}]}]}]}
                """);

        assertEquals("inside", converter.toText(node));
    }

    @Test
    void whenNestedListsThenItemsOnSeparateLines() throws Exception {
        JsonNode node = mapper.readTree("""
                {"type":"doc","content":[{"type":"bulletList","content":[
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"a"}]}]},
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"b"}]}]}]}]}
                """);

        assertEquals("a\n\nb", converter.toText(node));
    }
}

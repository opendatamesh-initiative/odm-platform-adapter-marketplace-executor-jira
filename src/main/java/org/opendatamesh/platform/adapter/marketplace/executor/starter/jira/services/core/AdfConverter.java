package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

@ApplicationScoped
public class AdfConverter {

    private static final Set<String> BLOCK_TYPES = Set.of("paragraph", "heading", "listItem", "blockquote", "codeBlock");

    private final ObjectMapper objectMapper;

    public AdfConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode toAdf(String text) {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("type", "doc");
        doc.put("version", 1);
        ArrayNode content = doc.putArray("content");
        if (text != null && !text.isBlank()) {
            for (String block : text.split("\\n\\s*\\n")) {
                content.add(paragraph(block));
            }
        }
        return doc;
    }

    public String toText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        StringBuilder text = new StringBuilder();
        appendText(node, text);
        return text.toString().trim();
    }

    private ObjectNode paragraph(String block) {
        ObjectNode paragraph = objectMapper.createObjectNode();
        paragraph.put("type", "paragraph");
        ArrayNode content = paragraph.putArray("content");
        String[] lines = block.strip().split("\\n");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                content.addObject().put("type", "hardBreak");
            }
            content.addObject().put("type", "text").put("text", lines[i]);
        }
        return paragraph;
    }

    private void appendText(JsonNode node, StringBuilder text) {
        String type = node.path("type").asText("");
        if ("text".equals(type)) {
            text.append(node.path("text").asText(""));
        } else if ("hardBreak".equals(type)) {
            text.append('\n');
        } else {
            node.path("content").forEach(child -> appendText(child, text));
            if (BLOCK_TYPES.contains(type)) {
                text.append('\n');
            }
        }
    }
}

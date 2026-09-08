package net.dstone.ai.api.dto;

import java.util.Map;

public record RetrievedChunk(String text, Map<String, Object> metadata, Double score) {
}

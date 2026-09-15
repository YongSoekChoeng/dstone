package net.dstone.ai.api.dto;

public record ChatResponse(String message, String provider, String sessionId, String agent) {
}

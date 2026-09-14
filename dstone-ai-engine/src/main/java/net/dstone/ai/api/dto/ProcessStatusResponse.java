package net.dstone.ai.api.dto;

/** status는 RUNNING/DONE/FAILED 중 하나다. DONE이면 result가, FAILED면 error가 채워진다. */
public record ProcessStatusResponse(String jobId, String status, String result, String error) {
}

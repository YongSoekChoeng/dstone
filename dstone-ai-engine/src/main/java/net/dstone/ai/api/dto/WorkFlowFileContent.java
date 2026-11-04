package net.dstone.ai.api.dto;

import java.time.Instant;

/**
 * <pre>
 * 실행이 건드린 파일 하나의 내용입니다(GET/POST /api/ai/workflow/executions/{id}/files/content).
 *
 * 저장할 때는 받은 hash를 baseHash로 돌려보냅니다. 그사이에 Agent가 파일을 다시 썼으면 hash가 달라서 저장을 거절합니다(409).
 * </pre>
 *
 * @param path           파일 경로
 * @param name           파일 이름
 * @param text           파일 내용(줄바꿈은 LF로 맞춘 값)
 * @param charset        파일의 글자셋(UTF-8 또는 MS949). 저장할 때도 이 글자셋으로 씁니다
 * @param lineBreak      파일의 줄바꿈 방식(CRLF 또는 LF). 저장할 때도 이 방식으로 씁니다
 * @param hash           파일 바이트의 SHA-256(16진수)
 * @param size           파일 크기(바이트)
 * @param lastModified   마지막으로 바뀐 시각
 * @param editable       지금 저장할 수 있는지
 * @param readOnlyReason 저장할 수 없으면 그 이유. 저장할 수 있으면 null
 */
public record WorkFlowFileContent(String path, String name, String text, String charset, String lineBreak, String hash, long size, Instant lastModified, boolean editable, String readOnlyReason) {
}

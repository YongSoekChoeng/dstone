package net.dstone.ai.runtime.workflow.file;

import java.time.Instant;

/**
 * <pre>
 * Workflow 실행 중에 step 하나가 파일 하나를 읽었거나 쓴 기록입니다(AI_WORKFLOW_EXECUTION_FILE 한 행).
 * 같은 step이 같은 파일을 여러 번 건드리면 한 줄로 합치고 횟수와 마지막 시각만 바뀝니다.
 * </pre>
 *
 * @param stepId      파일을 건드린 step의 id
 * @param path        파일 경로(FileAccessRule.normalize()로 다듬은 값)
 * @param action      READ 또는 WRITE
 * @param accessCount 이 step이 이 파일을 건드린 횟수
 * @param firstAt     처음 건드린 시각
 * @param lastAt      마지막으로 건드린 시각
 */
public record WorkFlowFileEntry(String stepId, String path, String action, int accessCount, Instant firstAt, Instant lastAt) {
}

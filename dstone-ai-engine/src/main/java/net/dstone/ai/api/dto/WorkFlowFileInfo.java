package net.dstone.ai.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * <pre>
 * 실행이 건드린 파일 하나와, 그 파일을 건드린 step들입니다(WorkFlowFileList.files의 한 항목).
 * exists/size/lastModified는 목록을 조회한 지금 디스크에서 읽은 값입니다.
 * </pre>
 *
 * @param path         파일 경로
 * @param name         파일 이름(경로의 마지막 부분)
 * @param extension    확장자(소문자, 점 없이). 없으면 빈 글자
 * @param exists       지금 파일이 있는지
 * @param size         파일 크기(바이트). 없으면 0
 * @param lastModified 마지막으로 바뀐 시각. 없으면 null
 * @param editableType 확장자가 저장할 수 있는 종류인지
 * @param written      한 step이라도 이 파일을 썼는지
 * @param steps        이 파일을 건드린 step들
 */
public record WorkFlowFileInfo(String path, String name, String extension, boolean exists, long size, Instant lastModified, boolean editableType, boolean written, List<Step> steps) {

	/**
	 * 파일 하나를 건드린 step 하나입니다.
	 *
	 * @param stepId      step id
	 * @param action      READ 또는 WRITE
	 * @param accessCount 이 step이 이 파일을 건드린 횟수
	 * @param firstAt     처음 건드린 시각
	 * @param lastAt      마지막으로 건드린 시각
	 */
	public record Step(String stepId, String action, int accessCount, Instant firstAt, Instant lastAt) {
	}
}

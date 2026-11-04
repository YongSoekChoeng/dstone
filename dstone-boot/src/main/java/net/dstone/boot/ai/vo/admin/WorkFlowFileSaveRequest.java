package net.dstone.boot.ai.vo.admin;

/**
 * Workflow 실행 관리 화면의 문서 팝업에서 고친 파일을 저장하는 요청이다.
 *
 * @param executionId 실행 id
 * @param path        파일 경로(문서 목록에 나온 값)
 * @param text        새 내용
 * @param baseHash    팝업이 파일을 열 때 받은 hash. 그사이 파일이 바뀌었으면 엔진이 409로 거절한다
 */
public record WorkFlowFileSaveRequest(String executionId, String path, String text, String baseHash) {
}

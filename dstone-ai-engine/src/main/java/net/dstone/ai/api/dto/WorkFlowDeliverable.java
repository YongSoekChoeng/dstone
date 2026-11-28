package net.dstone.ai.api.dto;

import java.util.List;

/**
 * <pre>
 * 승인 step의 approval.deliverable이 가리키는 결과 파일 목록입니다(GET .../deliverable).
 * 경로는 모두 원본 루트 기준 상대경로이고 구분자는 /입니다.
 *
 * 해시는 SHA-256(16진수)이며, 글자 파일(NUL 바이트가 없는 파일)은 줄바꿈 CRLF를 LF로 바꾼 뒤 계산합니다.
 * 화면도 로컬 파일을 같은 방법으로 계산해 비교하므로, 줄바꿈만 다른 파일은 같은 파일로 봅니다.
 * </pre>
 *
 * @param available     내려받을 것이 있는지
 * @param reason        없으면 그 이유(있으면 null)
 * @param stepId        deliverable을 적은 승인 step의 id
 * @param key           화면이 로컬 폴더 맵핑을 저장할 때 쓰는 이름
 * @param files         새로 만들거나 고친 파일
 * @param deletes       지울 파일
 * @param documentCount ZIP에 함께 담는 문서 수
 */
public record WorkFlowDeliverable(boolean available, String reason, String stepId, String key, List<Item> files, List<Deletion> deletes, int documentCount) {

	/**
	 * @param path     원본 루트 기준 상대경로
	 * @param size     바이트 수
	 * @param hash     새 내용의 해시
	 * @param baseHash 원본 파일의 해시. 원본에 없는 새 파일이거나 원본 폴더를 모르면 null
	 * @param content  새 내용(base64). 목록만 달라고 했으면 null
	 */
	public record Item(String path, long size, String hash, String baseHash, String content) {
	}

	/**
	 * @param path     원본 루트 기준 상대경로
	 * @param baseHash 원본 파일의 해시. 원본에 없거나 원본 폴더를 모르면 null
	 */
	public record Deletion(String path, String baseHash) {
	}
}

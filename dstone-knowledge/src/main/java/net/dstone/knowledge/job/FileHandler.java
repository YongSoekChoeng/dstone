package net.dstone.knowledge.job;

import java.util.Map;

/**
 * <pre>
 * 파일 하나를 처리하는 일입니다. FilePassRunner가 파일마다 prepare → write 순서로 한 번씩 부릅니다.
 *
 * 일을 둘로 나눈 이유:
 * - prepare: 파일을 읽고, 파싱하고, 풀어 보는 "오래 걸릴 수 있는" 부분. DB에 쓰지 않는다.
 *            감시 아래의 작업 스레드에서 돌고, 정해 둔 시간 안에 끝나지 않으면 이 파일은 버려진다.
 * - write:   prepare가 만든 결과를 DB에 쓰는 부분. 트랜잭션 안에서 돈다. 금방 끝난다.
 * 오래 걸리는 부분이 DB 연결이나 트랜잭션을 붙들고 있지 않아야, 그 부분이 멈췄을 때 깨끗하게 버릴 수 있습니다.
 * </pre>
 *
 * @param <T> prepare가 만들어 write에 넘기는 결과의 타입
 */
public interface FileHandler<T> {

	/**
	 * <pre>
	 * 파일 하나를 읽고 계산합니다. DB에 쓰지 않습니다(조회는 일반 세션으로 해도 됩니다. 트랜잭션 밖입니다).
	 * 예외를 던지면 이 파일은 FAILED가 되고 분석은 다음 파일로 계속합니다.
	 * </pre>
	 *
	 * @param file analysis_file 한 행. 키: fileId, path, module, sourceRoot, language, fileType, packageName, encoding, languageLevel, parseStatus
	 */
	T prepare(Map<String, Object> file) throws Exception;

	/**
	 * <pre>
	 * prepare의 결과를 DB에 씁니다. 트랜잭션 안에서 불립니다.
	 *
	 * - 대량 저장용 세션(sqlSessionBatch, Dao의 ...InBatch 메소드)으로만 씁니다.
	 *   같은 트랜잭션 안에서 일반 세션을 섞으면 MyBatis가 오류를 냅니다.
	 * - 예외를 던지면 이 파일에서 쓴 것이 모두 취소되고 파일은 FAILED가 됩니다.
	 * - "실패했다"는 사실 자체를 DB에 남기고 싶으면(예: 파싱 오류 내용) 예외 대신 FileResult.failed(...)를 돌려줍니다.
	 * </pre>
	 */
	FileResult write(Map<String, Object> file, T prepared) throws Exception;

	/**
	 * <pre>
	 * prepare가 시간 안에 끝나지 않아 그 작업 스레드를 버렸을 때 불립니다.
	 * 버려진 스레드는 쓰던 객체(해석기, 캐시 등)를 계속 만지고 있을 수 있습니다.
	 * 파일 사이에 걸쳐 들고 있는 객체가 있으면 여기서 새로 만듭니다. 그런 것이 없으면 아무것도 하지 않습니다.
	 * </pre>
	 */
	void reset() throws Exception;

}

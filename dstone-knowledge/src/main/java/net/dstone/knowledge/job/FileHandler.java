package net.dstone.knowledge.job;

import java.util.Map;

/**
 * 파일 하나를 처리하는 일입니다. FilePassRunner가 파일마다 한 번씩 부릅니다.
 */
public interface FileHandler {

	/**
	 * 파일 하나를 처리합니다. 트랜잭션 안에서 불립니다.
	 *
	 * - 결과는 대량 저장용 세션(sqlSessionBatch, Dao의 ...InBatch 메소드)으로만 씁니다.
	 *   같은 트랜잭션 안에서 일반 세션을 섞으면 MyBatis가 오류를 냅니다.
	 * - 예외를 던지면 이 파일에서 쓴 것이 모두 취소되고 파일은 FAILED가 됩니다.
	 * - "실패했다"는 사실 자체를 DB에 남기고 싶으면(예: 파싱 오류 내용) 예외 대신 FileResult.failed(...)를 돌려줍니다.
	 *
	 * @param file analysis_file 한 행. 키: fileId, path, module, sourceRoot, language, fileType, packageName, encoding
	 */
	FileResult handle(Map<String, Object> file) throws Exception;

}

package net.dstone.boot.ai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import net.dstone.boot.ai.dao.SqlConvertDao;
import net.dstone.boot.ai.vo.SqlConvertVo;
import net.dstone.boot.ai.vo.WorkFlowCallResult;
import net.dstone.common.config.ConfigProperty;

/**
 * "오라클 SQL을 PostgreSQL SQL로 바꿔줘" 화면의 실제 일꾼이다. dstone-ai-engine의
 * oracle-to-postgresql Workflow(analyze→convert→validate, POST /api/ai/workflow/{id}/execute)를
 * 동기로 호출해서 답을 받고, 그 결과(성공/실패 모두)를 TB_AI_SQLCONVERT에 이력으로 남긴 뒤 화면에
 * 돌려준다(2026-09-15 dstone-ai-engine 재설계로 capability 기반 /api/ai/chat 호출에서 이 Workflow
 * 호출로 바뀌었다 - 프롬프트를 직접 얹어 보내는 대신, Workflow 자체가 이미 분석→변환→문법검증까지
 * 끝낸 SQL을 돌려준다).
 *
 * 변환 하나하나는 완전히 독립된 요청으로 취급한다 - sessionId를 매번 새로 발급해서, 이전 변환 내용이
 * 다음 변환에 실수로 섞여 들어가지 않게 한다(로그인 사용자의 일반 채팅 세션과도 당연히 무관하다).
 */
@Service
public class SqlConvertService extends net.dstone.boot.common.biz.BaseService {

	private static final String WORKFLOW_ID = "oracle-to-postgresql";

	@Autowired
	private ConfigProperty configProperty;

	@Autowired
	private SqlConvertDao sqlConvertDao;

	public SqlConvertVo convert(String originalSql, String requesterId) {

		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", originalSql);
		body.put("sessionId", UUID.randomUUID().toString());

		SqlConvertVo sqlConvertVo = new SqlConvertVo();
		sqlConvertVo.setORIGINAL_SQL(originalSql);
		sqlConvertVo.setREQUESTER_ID(requesterId);

		try {
			// 분석→변환→문법검증 3 step(실패 시 재작성 루프 포함)까지 끝나야 응답이 오는 동기 호출이라 넉넉하게 준다 - 일반 채팅(기본 60초)보다 오래 걸릴 수 있다.
			WorkFlowCallResult workflowCallResult = this.getWebClient( (5 * 60) ).post()
					.uri(baseUrl + "/api/ai/workflow/" + WORKFLOW_ID + "/execute")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(body)
					.retrieve()
					.bodyToMono(WorkFlowCallResult.class)
					.block();

			sqlConvertVo.setCONVERTED_SQL(workflowCallResult != null ? workflowCallResult.message() : null);
			sqlConvertVo.setSUCCESS_YN("Y");
		} catch (Exception e) {
			// dstone-ai-engine이 응답을 못 주는 경우(타임아웃, 5xx 등)도 화면에는 "왜 실패했는지"를
			// 보여줘야 하므로, 예외를 위로 던지지 않고 실패 이력으로 남긴다.
			String errorMessage = e.getMessage();
			sqlConvertVo.setSUCCESS_YN("N");
			sqlConvertVo.setERROR_MESSAGE(errorMessage != null && errorMessage.length() > 1000
					? errorMessage.substring(0, 1000) : errorMessage);
		}

		try {
			if(sqlConvertVo != null && sqlConvertVo.getCONVERTED_SQL() != null) {
				this.info(sqlConvertVo.getCONVERTED_SQL());
			}
			this.sqlConvertDao.insertHistory(sqlConvertVo);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return sqlConvertVo;
	}

	public List<SqlConvertVo> listHistory() {
		try {
			return this.sqlConvertDao.listHistory();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}

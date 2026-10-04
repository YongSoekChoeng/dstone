package net.dstone.knowledge.api.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.common.config.ConfigProperty;
import net.dstone.knowledge.api.dao.SystemDao;

/**
 * <pre>
 * 모듈이 잘 떠 있는지 확인하는 API입니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

	/** 스키마 SQL(02-create-table-postgresql-dstone-knowledge.sql)이 만드는 테이블 수입니다. 테이블을 추가하면 같이 고칩니다. */
	private static final int EXPECTED_TABLE_COUNT = 21;

	@Autowired
	private SystemDao systemDao;

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * <pre>
	 * 상태 확인.
	 *
	 * 앱만 떠 있고 DB가 안 붙거나 스키마가 덜 만들어진 경우를 구분해서 알려 줍니다.
	 * - UP: 정상
	 * - SCHEMA_NOT_READY: DB는 붙었지만 테이블이 모자람(스키마 SQL을 아직 안 돌렸을 때)
	 * - DB_DOWN: DB 연결 실패
	 * </pre>
	 */
	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("app", "dstone-knowledge");
		body.put("analyzerVersion", configProperty.getProperty("dstone.knowledge.analyzer-version"));
		try {
			Map<String, Object> db = systemDao.selectDbStatus();
			body.put("db", db);
			long tableCount = ((Number) db.get("tableCount")).longValue();
			if (tableCount < EXPECTED_TABLE_COUNT || db.get("pgvectorVersion") == null) {
				body.put("status", "SCHEMA_NOT_READY");
				body.put("expectedTableCount", EXPECTED_TABLE_COUNT);
				return new ResponseEntity<Map<String, Object>>(body, HttpStatus.SERVICE_UNAVAILABLE);
			}
			body.put("status", "UP");
			return new ResponseEntity<Map<String, Object>>(body, HttpStatus.OK);
		} catch (Exception e) {
			body.put("status", "DB_DOWN");
			body.put("error", String.valueOf(e.getMessage()));
			return new ResponseEntity<Map<String, Object>>(body, HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

}

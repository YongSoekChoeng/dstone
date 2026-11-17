package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.EncUtil;
import net.dstone.knowledge.analysis.pass08.semantic.dictionary.DbCommentReader;
import net.dstone.knowledge.api.dao.ProjectDbDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 프로젝트가 쓰는 DB 의 접속 정보를 저장 · 조회 · 시험합니다. (있어도 되고 없어도 되고, 여러 개여도 됩니다)
 *
 * 비밀번호 규칙:
 *   - 저장할 때 EncUtil 로 암호화합니다. 평문은 DB 에도, 로그에도 남기지 않습니다.
 *   - 응답에는 절대 넣지 않습니다. 저장돼 있으면 password 가 "****", 없으면 빈 값입니다.
 *   - 고칠 때 비밀번호를 비워 두거나 "****" 그대로 보내면 저장된 값을 그대로 씁니다(dbSeq 로 찾는다).
 * </pre>
 */
@Service
public class ProjectDbService extends BaseObject {

	/** 응답에서 "비밀번호가 저장돼 있음"을 뜻하는 글자 */
	public static final String MASK = "****";

	/** 한 프로젝트에 둘 수 있는 접속 수 */
	private static final int MAX_CONNECTIONS = 20;

	@Autowired
	private ProjectDbDao projectDbDao;

	@Autowired
	private DbCommentReader dbCommentReader;

	@Autowired
	private UnusedService unusedService;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/**
	 * <pre>
	 * 화면에 보여 줄 접속 목록. 비밀번호 대신 password = "****"(저장돼 있을 때) / ""(없을 때)
	 * </pre>
	 */
	public List<Map<String, Object>> list(String projectId) {
		List<Map<String, Object>> rows = projectDbDao.selectList(projectId);
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			Map<String, Object> shown = new LinkedHashMap<String, Object>();
			shown.put("dbSeq", row.get("dbSeq"));
			shown.put("dbName", row.get("dbName"));
			shown.put("envLabel", row.get("envLabel"));
			shown.put("jdbcUrl", row.get("jdbcUrl"));
			shown.put("username", row.get("username"));
			shown.put("password", isBlank(row.get("passwordEnc")) ? "" : MASK);
			shown.put("schemaName", row.get("schemaName"));
			shown.put("updatedAt", row.get("updatedAt"));
			result.add(shown);
		}
		return result;
	}

	/**
	 * <pre>
	 * 요청의 dbConnections 를 확인하고 저장할 모양으로 바꿉니다. 프로젝트를 저장하기 전에 불러서, 잘못된 값이면 아무것도 저장하지 않게 합니다.
	 * 빈 줄(이름 · 주소 모두 비었음)은 건너뜁니다.
	 * </pre>
	 *
	 * @return 저장할 행 목록(비밀번호는 아직 평문이거나 null)
	 */
	public List<Map<String, Object>> check(Object value) {
		if (value != null && !(value instanceof List)) {
			throw ApiException.badRequest("dbConnections 는 목록이어야 합니다.");
		}
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		List<?> list = value == null ? new ArrayList<Object>() : (List<?>) value;
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof Map)) {
				throw ApiException.badRequest("dbConnections 의 " + (i + 1) + "번째 값이 올바르지 않습니다.");
			}
			Map<?, ?> in = (Map<?, ?>) list.get(i);
			String name = text(in.get("dbName"));
			String url = text(in.get("jdbcUrl"));
			if (name == null && url == null) {
				continue;
			}
			String where = "DB 접속 " + (i + 1) + "번째 줄";
			if (name == null) {
				throw ApiException.badRequest(where + ": 이름을 적어 주세요.");
			}
			if (url == null || !url.startsWith("jdbc:")) {
				throw ApiException.badRequest(where + "(" + name + "): JDBC 주소는 jdbc: 로 시작해야 합니다.");
			}
			Map<String, Object> row = new HashMap<String, Object>();
			row.put("dbSeq", in.get("dbSeq") instanceof Number ? Integer.valueOf(((Number) in.get("dbSeq")).intValue()) : null);
			row.put("dbName", limit(name, 100));
			row.put("envLabel", limit(text(in.get("envLabel")), 50));
			row.put("jdbcUrl", limit(url, 1000));
			row.put("username", limit(text(in.get("username")), 200));
			// 비밀번호는 앞뒤 공백도 값일 수 있어서 자르지 않는다. 비었으면 "그대로 두기"
			Object password = in.get("password");
			row.put("password", password == null || String.valueOf(password).length() == 0 ? null : String.valueOf(password));
			row.put("schemaName", limit(text(in.get("schemaName")), 200));
			rows.add(row);
		}
		if (rows.size() > MAX_CONNECTIONS) {
			throw ApiException.badRequest("DB 접속은 " + MAX_CONNECTIONS + "개까지 등록할 수 있습니다.");
		}
		return rows;
	}

	/**
	 * <pre>
	 * 프로젝트의 접속 정보를 통째로 바꿉니다(check() 를 거친 행). 적은 순서가 찾는 순서입니다.
	 * 비밀번호를 비워 두었거나 "****" 이면 같은 dbSeq 로 저장돼 있던 값을 그대로 둡니다.
	 * </pre>
	 */
	public void replace(final String projectId, List<Map<String, Object>> rows) {
		Map<Integer, String> saved = savedPasswords(projectId);
		int next = 1;
		for (Integer seq : saved.keySet()) {
			next = Math.max(next, seq.intValue() + 1);
		}
		final List<Map<String, Object>> inserts = new ArrayList<Map<String, Object>>();
		java.util.Set<Integer> used = new java.util.HashSet<Integer>();
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = new HashMap<String, Object>(rows.get(i));
			Integer seq = (Integer) row.get("dbSeq");
			// 저장된 줄을 고치는 것이면 그 번호를 쓰고, 새 줄이거나 번호가 겹치면 새 번호를 준다.
			boolean existing = seq != null && saved.containsKey(seq) && !used.contains(seq);
			if (!existing) {
				seq = Integer.valueOf(next++);
			}
			used.add(seq);
			String password = (String) row.remove("password");
			String encrypted;
			if (password != null && !MASK.equals(password)) {
				encrypted = EncUtil.encrypt(password);
			} else {
				encrypted = existing ? saved.get(seq) : null;
			}
			row.put("projectId", projectId);
			row.put("dbSeq", seq);
			row.put("passwordEnc", encrypted);
			row.put("sortOrder", Integer.valueOf(i));
			inserts.add(row);
		}
		txTemplateCommon.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				projectDbDao.deleteByProject(projectId);
				for (int i = 0; i < inserts.size(); i++) {
					projectDbDao.insert(inserts.get(i));
				}
			}
		});
		dbCommentReader.forget(projectId);
		// "DB에는 있는데 아무 SQL 도 안 쓰는 테이블" 판정이 접속 정보를 따르므로 다시 계산하게 한다
		unusedService.forget();
		info("DB 접속 정보 저장: projectId=" + projectId + ", 접속 " + inserts.size() + "개");
	}

	/**
	 * <pre>
	 * 접속 시험. 화면에서 아직 저장하지 않은 값으로도 시험할 수 있게 요청의 값을 씁니다.
	 * 비밀번호를 비워 두었거나 "****" 이면 dbSeq 로 저장된 비밀번호를 씁니다.
	 * </pre>
	 *
	 * @return {ok, message?, product?, schema?, tableCount?, elapsedMillis}
	 */
	public Map<String, Object> test(String projectId, Map<String, Object> request) {
		List<Object> one = new ArrayList<Object>();
		Map<String, Object> copy = new HashMap<String, Object>(request == null ? new HashMap<String, Object>() : request);
		if (text(copy.get("dbName")) == null) {
			copy.put("dbName", "시험");
		}
		one.add(copy);
		List<Map<String, Object>> rows = check(one);
		if (rows.isEmpty()) {
			throw ApiException.badRequest("JDBC 주소를 적어 주세요.");
		}
		Map<String, Object> row = rows.get(0);
		String password = (String) row.get("password");
		if (password == null || MASK.equals(password)) {
			Integer seq = (Integer) row.get("dbSeq");
			String encrypted = seq == null ? null : savedPasswords(projectId).get(seq);
			password = isBlank(encrypted) ? null : EncUtil.decrypt(encrypted);
		}
		return dbCommentReader.test(new DbCommentReader.Target(String.valueOf(row.get("dbName")), (String) row.get("jdbcUrl"),
				(String) row.get("username"), password, (String) row.get("schemaName")));
	}

	/** dbSeq → 저장된(암호화된) 비밀번호 */
	private Map<Integer, String> savedPasswords(String projectId) {
		Map<Integer, String> saved = new HashMap<Integer, String>();
		List<Map<String, Object>> rows = projectDbDao.selectList(projectId);
		for (int i = 0; i < rows.size(); i++) {
			saved.put(Integer.valueOf(((Number) rows.get(i).get("dbSeq")).intValue()), (String) rows.get(i).get("passwordEnc"));
		}
		return saved;
	}

	private boolean isBlank(Object value) {
		return value == null || String.valueOf(value).trim().length() == 0;
	}

	/** 값을 앞뒤 공백을 뗀 글자로. 없거나 비었으면 null */
	private String text(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.length() == 0 ? null : text;
	}

	private String limit(String value, int max) {
		if (value != null && value.length() > max) {
			throw ApiException.badRequest("값이 너무 깁니다(" + max + "자 이내): " + value.substring(0, Math.min(40, value.length())) + "…");
		}
		return value;
	}

}

package net.dstone.ai.common.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.ai.common.biz.BaseDao;
import net.dstone.common.utils.EncUtil;

/**
 * <pre>
 * 프로젝트 관리(ai_management_project, ai_management_project_db)의 저장과 조회입니다. SQL은 resources/sqlmap/project/ProjectDao.xml.
 *
 * ## 테이블정의
 *   ai_management_project     PROJECT_ID(KEY), PROJECT_NAME, DESCRIPTION, SOURCE_DIR, SCM_TYPE, SCM_URL, JAVA_VERSION, USE_YN
 *   ai_management_project_db  PROJECT_ID + DB_SEQ(KEY), DB_NAME, JDBC_URL, USERNAME, PASSWORD_ENC, SCHEMA_NAME, DESCRIPTION, SORT_ORDER
 *
 * 비밀번호는 EncUtil 로 암호화해 저장합니다. 화면 API(list/find)는 비밀번호를 돌려주지 않고 "있는지"만 알려 줍니다.
 * 고칠 때 비밀번호 칸을 비워 두면 저장된 비밀번호를 그대로 씁니다(같은 DB_SEQ 기준).
 * DB Tool(tools.db.DbQueryTool)은 connections()로 "프로젝트ID.DB이름" 별칭과 복호화한 접속 정보를 받습니다.
 * </pre>
 */
@Repository
public class ProjectStore extends BaseDao {

	private static final String NS = "net.dstone.ai.common.project.ProjectStore.";

	/** 이름 규칙(프로젝트ID, DB 이름). DB Tool 별칭에 쓰이므로 점은 쓰지 않습니다. */
	private static final String ID_PATTERN = "[A-Za-z0-9_-]{1,50}";

	/** Java 버전 규칙(8, 11, 17, 21 처럼 숫자만). 1.8 같은 옛 표기는 받지 않습니다. */
	private static final String JAVA_VERSION_PATTERN = "[0-9]{1,3}";

	/** 형상관리 종류 */
	public static final List<String> SCM_TYPES = List.of("GIT", "SVN", "NONE");

	private TransactionTemplate tx;

	@Autowired
	public void setDataSource(DataSource dataSource) {
		this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
	}

	/** 프로젝트 목록(DB 수 포함)입니다. */
	public List<Map<String, Object>> list() {
		return this.sqlSessionCommon.selectList(NS + "selectProjectList");
	}

	/** 프로젝트 하나와 DB 목록(비밀번호 제외)입니다. 없으면 null입니다. */
	public Map<String, Object> find(String projectId) {
		Map<String, Object> project = this.sqlSessionCommon.selectOne(NS + "selectProject", projectId);
		if (project == null) {
			return null;
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>(project);
		result.put("dbs", this.sqlSessionCommon.selectList(NS + "selectProjectDbList", projectId));
		return result;
	}

	/**
	 * 프로젝트를 만들거나 고칩니다. DB 목록은 통째로 바꿉니다.
	 *
	 * @param project {projectId, projectName, description, sourceDir, scmType, scmUrl, javaVersion, useYn, dbs: [{dbSeq?, dbName, jdbcUrl, username, password?, schemaName, description}]}
	 * @param by      작업한 사람
	 */
	@SuppressWarnings("unchecked")
	public void save(Map<String, Object> project, String by) {
		final String projectId = text(project, "projectId");
		if (projectId == null || !projectId.matches(ID_PATTERN)) {
			throw new IllegalArgumentException("프로젝트ID는 영문·숫자·_- 로 50자 이내입니다: " + projectId);
		}
		if (text(project, "projectName") == null) {
			throw new IllegalArgumentException("프로젝트명을 넣으세요.");
		}
		String scmType = text(project, "scmType");
		if (scmType != null && !SCM_TYPES.contains(scmType)) {
			throw new IllegalArgumentException("형상관리 종류는 " + SCM_TYPES + " 가운데 하나입니다: " + scmType);
		}
		String javaVersion = text(project, "javaVersion");
		if (javaVersion != null && !javaVersion.matches(JAVA_VERSION_PATTERN)) {
			throw new IllegalArgumentException("Java 버전은 숫자로 넣으세요(예: 8, 17): " + javaVersion);
		}
		final Map<String, Object> param = new HashMap<String, Object>();
		param.put("projectId", projectId);
		param.put("projectName", text(project, "projectName"));
		param.put("description", text(project, "description"));
		param.put("sourceDir", text(project, "sourceDir"));
		param.put("scmType", scmType);
		param.put("scmUrl", text(project, "scmUrl"));
		param.put("javaVersion", javaVersion);
		param.put("useYn", "N".equals(text(project, "useYn")) ? "N" : "Y");
		param.put("by", by);

		// DB 목록을 먼저 확인하고, 비워 둔 비밀번호는 저장된 것을 찾아 둡니다(트랜잭션 밖에서 실패를 먼저 알리려고).
		final List<Map<String, Object>> dbRows = new ArrayList<Map<String, Object>>();
		List<String> names = new ArrayList<String>();
		Object rawDbs = project.get("dbs");
		int seq = 0;
		for (Object raw : rawDbs instanceof List ? (List<Object>) rawDbs : new ArrayList<Object>()) {
			Map<String, Object> db = (Map<String, Object>) raw;
			String dbName = text(db, "dbName");
			if (dbName == null || !dbName.matches(ID_PATTERN)) {
				throw new IllegalArgumentException("DB 이름은 영문·숫자·_- 로 50자 이내입니다: " + dbName);
			}
			if (names.contains(dbName)) {
				throw new IllegalArgumentException("같은 DB 이름이 두 번 있습니다: " + dbName);
			}
			names.add(dbName);
			if (text(db, "jdbcUrl") == null || !text(db, "jdbcUrl").startsWith("jdbc:")) {
				throw new IllegalArgumentException("DB '" + dbName + "'의 JDBC URL은 jdbc: 로 시작해야 합니다.");
			}
			String password = db.get("password") == null ? null : String.valueOf(db.get("password"));
			String passwordEnc;
			if (password != null && !password.isEmpty()) {
				passwordEnc = EncUtil.encrypt(password);
			} else {
				passwordEnc = this.storedPassword(projectId, db.get("dbSeq"));
			}
			Map<String, Object> row = new HashMap<String, Object>();
			row.put("projectId", projectId);
			row.put("dbSeq", Integer.valueOf(++seq));
			row.put("dbName", dbName);
			row.put("jdbcUrl", text(db, "jdbcUrl"));
			row.put("username", text(db, "username"));
			row.put("passwordEnc", passwordEnc);
			row.put("schemaName", text(db, "schemaName"));
			row.put("description", text(db, "description"));
			row.put("sortOrder", Integer.valueOf(seq * 10));
			dbRows.add(row);
		}
		this.tx.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				ProjectStore.this.sqlSessionCommon.insert(NS + "upsertProject", param);
				ProjectStore.this.sqlSessionCommon.delete(NS + "deleteProjectDbs", projectId);
				for (Map<String, Object> row : dbRows) {
					ProjectStore.this.sqlSessionCommon.insert(NS + "insertProjectDb", row);
				}
			}
		});
	}

	/** 프로젝트를 지웁니다(DB 목록도 함께). */
	public void delete(String projectId) {
		this.sqlSessionCommon.delete(NS + "deleteProject", projectId);
	}

	/**
	 * DB Tool 이 쓸 접속 정보입니다. 별칭은 "프로젝트ID.DB이름", 값은 {url, username, password(복호화), schema, description}.
	 * 쓰지 않는 프로젝트의 DB는 넣지 않습니다.
	 */
	public Map<String, Map<String, String>> connections() {
		Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();
		List<Map<String, Object>> rows = this.sqlSessionCommon.selectList(NS + "selectConnectionList");
		for (Map<String, Object> row : rows) {
			Map<String, String> setting = new LinkedHashMap<String, String>();
			setting.put("url", (String) row.get("jdbcUrl"));
			setting.put("username", (String) row.get("username"));
			String enc = (String) row.get("passwordEnc");
			setting.put("password", enc == null || enc.isEmpty() ? null : EncUtil.decrypt(enc));
			setting.put("schema", (String) row.get("schemaName"));
			String description = (String) row.get("description");
			setting.put("description", (row.get("projectName") + " · " + row.get("dbName")) + (description == null || description.isBlank() ? "" : " - " + description));
			result.put(row.get("projectId") + "." + row.get("dbName"), setting);
		}
		return result;
	}

	private String storedPassword(String projectId, Object dbSeq) {
		if (dbSeq == null || String.valueOf(dbSeq).isBlank()) {
			return null;
		}
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("projectId", projectId);
		param.put("dbSeq", Integer.valueOf(String.valueOf(dbSeq).trim()));
		return this.sqlSessionCommon.selectOne(NS + "selectPasswordEnc", param);
	}

	private static String text(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		if (value == null || String.valueOf(value).trim().isEmpty()) {
			return null;
		}
		return String.valueOf(value).trim();
	}

}

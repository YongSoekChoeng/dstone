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
import net.dstone.ai.runtime.tool.SecretSource;
import net.dstone.common.utils.EncUtil;

/**
 * <pre>
 * 프로젝트 관리(ai_management_project, ai_management_project_db)의 저장과 조회입니다. SQL은 resources/sqlmap/project/ProjectDao.xml.
 *
 * ## 테이블정의
 *   ai_management_project           PROJECT_ID(KEY), PROJECT_NAME, DESCRIPTION, SOURCE_DIR, SCM_TYPE, SCM_URL, SCM_USERNAME, SCM_PASSWORD_ENC, JAVA_VERSION,
 *                                   WAS_CONTAINER, WAS_VERSION, CONTEXT_PATH, BUILD_*(리비전 빌드), CURRENT_REVISION, TEST_USER_ID, TEST_PASSWORD_ENC, TEST_STATIC_PATHS, USE_YN
 *   ai_management_project_db        PROJECT_ID + DB_SEQ(KEY), DB_NAME, JDBC_URL, USERNAME, PASSWORD_ENC, SCHEMA_NAME, JNDI_NAME, DESCRIPTION, SORT_ORDER
 *   ai_management_project_revision  PROJECT_ID + REVISION(KEY). 리비전 빌드 기록(ProjectBuildService)
 *
 * 테스트계정(TEST_USER_ID, TEST_PASSWORD_ENC)은 테스트 서버 화면에 로그인할 계정입니다. 비밀번호는 Agent에게도 주지 않고,
 * 자리표시자 ${secret:프로젝트ID.testPassword}를 엔진(runtime.tool.ToolCallGate)이 secret()으로 바꿉니다.
 * 비밀번호는 EncUtil 로 암호화해 저장합니다. 화면 API(list/find)는 비밀번호를 돌려주지 않고 "있는지"만 알려 줍니다.
 * 고칠 때 비밀번호 칸을 비워 두면 저장된 비밀번호를 그대로 씁니다(같은 DB_SEQ 기준).
 * DB Tool(tools.db.DbQueryTool)은 connections()로 "프로젝트ID.DB이름" 별칭과 복호화한 접속 정보를 받습니다.
 * </pre>
 */
@Repository
public class ProjectStore extends BaseDao implements SecretSource {

	/** 비밀값 참조의 이름: 테스트계정 비밀번호 */
	public static final String SECRET_TEST_PASSWORD = "testPassword";

	private static final String NS = "net.dstone.ai.common.project.ProjectStore.";

	/** 이름 규칙(프로젝트ID, DB 이름). DB Tool 별칭에 쓰이므로 점은 쓰지 않습니다. */
	private static final String ID_PATTERN = "[A-Za-z0-9_-]{1,50}";

	/** Java 버전 규칙(8, 11, 17, 21 처럼 숫자만). 1.8 같은 옛 표기는 받지 않습니다. */
	private static final String JAVA_VERSION_PATTERN = "[0-9]{1,3}";

	/** 형상관리 종류 */
	public static final List<String> SCM_TYPES = List.of("GIT", "SVN", "NONE");

	/** 원래 WAS 종류 */
	public static final List<String> WAS_CONTAINERS = List.of("BOOT", "TOMCAT", "JEUS", "WEBLOGIC", "WEBSPHERE");

	/** 빌드 종류(ProjectBuildService) */
	public static final List<String> BUILD_TYPES = List.of("MAVEN", "GRADLE", "ANT", "SHELL", "NONE");

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
	 * @param project {projectId, projectName, description, sourceDir, scmType, scmUrl, scmUsername, scmPassword?, javaVersion, wasContainer, wasVersion, contextPath,
	 *                buildType, buildArgs, buildWebRoot, buildClassesDir, buildLibDir, buildSourceDirs, testUserId, testPassword?, testStaticPaths?, useYn,
	 *                dbs: [{dbSeq?, dbName, jdbcUrl, username, password?, schemaName, jndiName, description}]}
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
		// 형상관리 계정: 비밀번호 칸을 비워 두면 저장된 것을 그대로 쓴다. 계정을 지우면 비밀번호도 지운다.
		String scmUsername = text(project, "scmUsername");
		String scmPassword = project.get("scmPassword") == null ? null : String.valueOf(project.get("scmPassword"));
		String scmPasswordEnc = null;
		if (scmUsername != null) {
			scmPasswordEnc = scmPassword != null && !scmPassword.isEmpty() ? EncUtil.encrypt(scmPassword)
				: (String) this.sqlSessionCommon.selectOne(NS + "selectScmPasswordEnc", projectId);
		}
		param.put("scmUsername", scmUsername);
		param.put("scmPasswordEnc", scmPasswordEnc);
		String wasContainer = text(project, "wasContainer");
		if (wasContainer != null && !WAS_CONTAINERS.contains(wasContainer)) {
			throw new IllegalArgumentException("WAS 종류는 " + WAS_CONTAINERS + " 가운데 하나입니다: " + wasContainer);
		}
		String buildType = text(project, "buildType");
		if (buildType != null && !BUILD_TYPES.contains(buildType)) {
			throw new IllegalArgumentException("빌드 종류는 " + BUILD_TYPES + " 가운데 하나입니다: " + buildType);
		}
		String contextPath = text(project, "contextPath");
		if (contextPath != null && !contextPath.matches("/[A-Za-z0-9._~/-]*")) {
			throw new IllegalArgumentException("컨텍스트 경로는 /로 시작합니다(예: /, /myApp): " + contextPath);
		}
		param.put("wasContainer", wasContainer);
		param.put("wasVersion", text(project, "wasVersion"));
		param.put("contextPath", contextPath);
		param.put("buildType", buildType);
		param.put("buildArgs", text(project, "buildArgs"));
		param.put("buildWebRoot", relativePath(text(project, "buildWebRoot"), "웹 루트"));
		param.put("buildClassesDir", relativePath(text(project, "buildClassesDir"), "클래스 폴더"));
		param.put("buildLibDir", relativePath(text(project, "buildLibDir"), "lib 폴더"));
		param.put("buildSourceDirs", text(project, "buildSourceDirs"));
		// 테스트계정: 비밀번호 칸을 비워 두면 저장된 것을 그대로 쓴다. ID를 지우면 비밀번호도 지운다.
		String testUserId = text(project, "testUserId");
		String testPassword = project.get("testPassword") == null ? null : String.valueOf(project.get("testPassword"));
		String testPasswordEnc = null;
		if (testUserId != null) {
			testPasswordEnc = testPassword != null && !testPassword.isEmpty() ? EncUtil.encrypt(testPassword)
				: (String) this.sqlSessionCommon.selectOne(NS + "selectTestPasswordEnc", projectId);
		}
		param.put("testUserId", testUserId);
		param.put("testPasswordEnc", testPasswordEnc);
		param.put("testStaticPaths", staticPathsText(text(project, "testStaticPaths")));
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
			row.put("jndiName", text(db, "jndiName"));
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

	/**
	 * <pre>
	 * 빌드와 테스트 서버가 쓸 프로젝트 설정입니다(비밀번호 제외, 쓰지 않는 프로젝트도 포함). 없으면 null입니다.
	 * </pre>
	 */
	public Map<String, Object> settings(String projectId) {
		return this.sqlSessionCommon.selectOne(NS + "selectProject", projectId);
	}

	/** 형상관리 비밀번호(복호화)입니다. 없으면 null. 로그에 남기지 않습니다. */
	public String scmPassword(String projectId) {
		String enc = this.sqlSessionCommon.selectOne(NS + "selectScmPasswordEnc", projectId);
		return enc == null || enc.isEmpty() ? null : EncUtil.decrypt(enc);
	}

	/** 리비전 빌드 기록(최근 것부터)입니다. */
	public List<Map<String, Object>> revisions(String projectId) {
		return this.sqlSessionCommon.selectList(NS + "selectRevisionList", projectId);
	}

	/** 리비전 하나입니다. 없으면 null. */
	public Map<String, Object> revision(String projectId, String revision) {
		return this.sqlSessionCommon.selectOne(NS + "selectRevision", revisionKey(projectId, revision));
	}

	/** 리비전 빌드를 시작한 것으로 적습니다(같은 이름이 있으면 다시 BUILDING). */
	public void startRevision(String projectId, String revision, String scmRevision, String by) {
		Map<String, Object> param = revisionKey(projectId, revision);
		param.put("scmRevision", scmRevision);
		param.put("by", by);
		this.sqlSessionCommon.insert(NS + "upsertRevision", param);
	}

	/**
	 * 리비전 빌드의 결과를 적습니다.
	 *
	 * @param result {status, kind, webRoot, classesDir, libDir, mainClass, message}
	 */
	public void finishRevision(String projectId, String revision, Map<String, Object> result) {
		Map<String, Object> param = revisionKey(projectId, revision);
		param.putAll(result);
		this.sqlSessionCommon.update(NS + "finishRevision", param);
	}

	/** 테스트 서버가 쓸 현재 리비전을 바꿉니다. */
	public void setCurrentRevision(String projectId, String revision) {
		this.sqlSessionCommon.update(NS + "updateCurrentRevision", revisionKey(projectId, revision));
	}

	/** 리비전 기록을 지웁니다(폴더는 부르는 쪽이 지웁니다). */
	public void deleteRevision(String projectId, String revision) {
		this.sqlSessionCommon.delete(NS + "deleteRevision", revisionKey(projectId, revision));
	}

	/** 엔진이 다시 뜰 때 빌드 중이던 기록을 실패로 바꿉니다. */
	public void abandonBuildingRevisions() {
		this.sqlSessionCommon.update(NS + "abandonBuildingRevisions");
	}

	private static Map<String, Object> revisionKey(String projectId, String revision) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("projectId", projectId);
		param.put("revision", revision);
		return param;
	}

	/** 리비전 소스 루트 기준 상대경로만 받습니다(절대경로나 ..는 거절). */
	private static String relativePath(String path, String label) {
		if (path == null) {
			return null;
		}
		String normalized = path.replace('\\', '/');
		if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*") || normalized.contains("..")) {
			throw new IllegalArgumentException(label + "는 소스 루트 기준 상대경로로 넣으세요(예: WebContent): " + path);
		}
		return normalized;
	}

	/** 프로젝트를 지웁니다(DB 목록도 함께). */
	public void delete(String projectId) {
		this.sqlSessionCommon.delete(NS + "deleteProject", projectId);
	}

	/**
	 * DB Tool 이 쓸 접속 정보입니다. 별칭은 "프로젝트ID.DB이름", 값은 {url, username, password(복호화), schema, description}.
	 * 쓰지 않는 프로젝트의 DB는 넣지 않습니다.
	 */
	/**
	 * <pre>
	 * 비밀값 자리표시자의 실제 값입니다(runtime.tool.ToolCallGate가 부릅니다). 참조는 "프로젝트ID.이름"이고 이름은 testPassword 하나입니다.
	 * 쓰지 않는 프로젝트(USE_YN='N')나 값이 없으면 null입니다. 돌려준 값은 로그에 남기지 않습니다.
	 * </pre>
	 *
	 * @param reference "프로젝트ID.testPassword"
	 */
	@Override
	public String secret(String reference) {
		if (reference == null) {
			return null;
		}
		int dot = reference.lastIndexOf('.');
		if (dot <= 0 || !SECRET_TEST_PASSWORD.equals(reference.substring(dot + 1))) {
			return null;
		}
		String projectId = reference.substring(0, dot);
		Map<String, Object> project = this.sqlSessionCommon.selectOne(NS + "selectProject", projectId);
		if (project == null || !"Y".equals(project.get("useYn"))) {
			return null;
		}
		String enc = this.sqlSessionCommon.selectOne(NS + "selectTestPasswordEnc", projectId);
		return enc == null || enc.isEmpty() ? null : EncUtil.decrypt(enc);
	}

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
			setting.put("jndiName", (String) row.get("jndiName"));
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

	/**
	 * <pre>
	 * 테스트서버 정적 경로 글을 검사하고 줄마다 "/주소=폴더" 모양으로 맞춰 돌려줍니다. 비었으면 null입니다.
	 * 원래 환경에서 웹서버나 다른 설정이 열어 주는 주소를 테스트 서버도 열게 하려는 값입니다(예: /Upload=D:/app/uploadData/Upload).
	 * </pre>
	 *
	 * @param text 화면에서 받은 글(한 줄에 하나)
	 */
	static String staticPathsText(String text) {
		List<String[]> paths = parseStaticPaths(text);
		if (paths.isEmpty()) {
			return null;
		}
		StringBuilder out = new StringBuilder();
		for (String[] path : paths) {
			if (out.length() > 0) {
				out.append('\n');
			}
			out.append(path[0]).append('=').append(path[1]);
		}
		return out.toString();
	}

	/**
	 * <pre>
	 * 테스트서버 정적 경로 글을 [주소, 폴더] 목록으로 읽습니다. 빈 줄과 #로 시작하는 줄은 건너뜁니다.
	 * 주소는 /로 시작하고 끝의 /는 뗍니다. "/"(앱 전체)는 쓸 수 없습니다. 모양이 틀리면 IllegalArgumentException입니다.
	 * </pre>
	 *
	 * @param text 저장된 글
	 */
	public static List<String[]> parseStaticPaths(String text) {
		List<String[]> paths = new ArrayList<String[]>();
		if (text == null) {
			return paths;
		}
		List<String> seen = new ArrayList<String>();
		for (String raw : text.split("\r?\n")) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int equals = line.indexOf('=');
			String url = equals < 0 ? "" : line.substring(0, equals).trim();
			String folder = equals < 0 ? "" : line.substring(equals + 1).trim().replace('\\', '/');
			while (url.length() > 1 && url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
			if (!url.matches("/[A-Za-z0-9._~/-]+") || url.contains("//") || url.contains("..") || folder.isEmpty()) {
				throw new IllegalArgumentException("테스트서버 정적 경로는 한 줄에 하나씩 '/주소=폴더' 모양으로 적습니다(주소는 /로 시작, 앱 전체 '/'는 안 됨): " + line);
			}
			if (seen.contains(url)) {
				throw new IllegalArgumentException("테스트서버 정적 경로에 같은 주소가 두 번 있습니다: " + url);
			}
			seen.add(url);
			paths.add(new String[] { url, folder });
		}
		return paths;
	}

	private static String text(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		if (value == null || String.valueOf(value).trim().isEmpty()) {
			return null;
		}
		return String.valueOf(value).trim();
	}

}

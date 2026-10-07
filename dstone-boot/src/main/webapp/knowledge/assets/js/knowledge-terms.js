/**
 * "코드 분석(Knowledge)" 화면에 나오는 말과 숫자의 뜻을 모아 둔 사전입니다.
 *
 * 같은 설명을 두 군데서 씁니다.
 *   - "프로젝트 · 분석" 화면의 리비전 요약: 표의 영문 값 옆에 뜻을 붙이고, 표마다 "이 표 읽는 법"을 보여 준다.
 *   - "사용 안내" 화면: 리비전 요약의 모든 항목을 한꺼번에 풀어 보여 준다.
 * 설명을 고칠 때는 이 파일만 고치면 두 화면에 같이 반영됩니다.
 *
 * 여기 적힌 뜻은 dstone-knowledge 가 실제로 계산하는 방식에 맞춘 것입니다(품질 지표의 계산식 등).
 * 분석 쪽 계산이 바뀌면 이 파일도 같이 고쳐야 합니다.
 */
var DstoneKnowledgeTerms = (function () {

	/* ---------- 값의 뜻. 표에 영문으로 나오는 값마다 한글 설명 ---------- */

	var PASS = {
		SCAN: "파일 훑기. 소스 폴더의 파일을 모두 찾아 종류와 인코딩을 알아낸다",
		CARRY: "(증분 분석) 바뀌지 않은 파일의 결과를 앞 리비전에서 옮겨 오기",
		DECLARE: "선언 읽기. 파일마다 클래스 · 메소드 · 필드와, 그 안에서 무엇을 부르는지를 적어 둔다",
		RESOURCE: "Java 밖의 자원 읽기. SQL 매퍼, 설정 파일, 빌드 파일, Spring 설정",
		CARRY_RESOLVE: "(증분 분석) 풀린 관계를 옮겨도 되는 파일을 가려 옮겨 오기",
		RESOLVE: "관계 풀기. 적어 둔 호출이 실제로 어느 클래스의 어느 메소드인지 알아낸다. 가장 오래 걸린다",
		LINK: "엮기. 재정의(override)와 인터페이스의 구현을 잇고, 품질 지표를 계산한다",
		SEMANTIC: "의미 붙이기. 주소(진입점)와 계층을 정하고, 메소드 · SQL · 테이블 · 화면을 잇는다",
		DOCUMENT: "검색용 글 만들기. 메소드 · 클래스 · SQL · 화면마다 검색할 글을 만들고 임베딩 대기열에 올린다"
	};

	var STATUS = {
		PENDING: "아직 안 함",
		READY: "대기 중 (리비전이면: 분석이 끝나 쓸 수 있음)",
		RUNNING: "도는 중",
		DONE: "끝남",
		DONE_WITH_WARNING: "끝났지만 일부 파일을 처리하지 못함",
		FAILED: "실패",
		CANCELLED: "취소됨",
		SKIPPED: "건너뜀 (이 단계가 볼 필요 없는 파일. 문제가 아님)",
		NONE: "임베딩 대기열에 없음"
	};

	var REFERENCE_STATUS = {
		RESOLVED: "프로젝트 안의 대상으로 확실히 풀림",
		EXTERNAL: "프로젝트 밖(JDK, 라이브러리)의 것으로 풀림. 정상",
		HEURISTIC: "확실하지 않아 이름으로 짐작해서 이음",
		UNRESOLVED: "끝내 누구인지 알아내지 못함",
		IGNORED: "관계로 만들 필요가 없어 일부러 뺌 (배열의 length 등). 문제가 아님",
		PENDING: "아직 풀지 않음 (RESOLVE 단계 전)"
	};

	var REF_KIND = {
		CALL: "메소드 호출",
		CREATE: "객체 만들기 (new)",
		TYPE_USE: "타입 사용 (변수 · 파라미터 · 반환 타입 등)",
		FIELD_ACCESS: "필드 읽기 / 쓰기",
		EXTENDS: "상속 (extends)",
		IMPLEMENTS: "구현 (implements)",
		THROWS: "던지는 예외 (throws)",
		METHOD_REF: "메소드 참조 (Foo::bar)",
		ANONYMOUS_SUPER: "익명 클래스가 물려받는 타입"
	};

	var RELATION_TYPE = {
		CALLS: "메소드가 메소드를 부른다",
		CALLS_POSSIBLE_IMPLEMENTATION: "인터페이스의 메소드를 불렀을 때 실제로 실행될 수 있는 구현 메소드",
		CREATES: "객체를 만든다 (new)",
		EXTENDS: "상속한다",
		IMPLEMENTS: "구현한다",
		OVERRIDES: "상위 타입의 메소드를 재정의한다",
		USES_TYPE: "타입을 쓴다 (변수 · 파라미터 · 반환 타입)",
		ACCESSES_FIELD: "필드를 읽거나 쓴다",
		THROWS: "예외를 던진다고 선언했다",
		INJECTS: "주입받는다 (@Autowired 등)",
		EXECUTES_SQL: "메소드가 SQL statement 를 실행한다",
		READS_TABLE: "SQL 이 테이블을 읽는다 (SELECT)",
		WRITES_TABLE: "SQL 이 테이블에 쓴다 (INSERT / UPDATE / DELETE)",
		RENDERS: "메소드가 끝나고 이 화면(JSP)을 연다",
		REQUESTS: "화면이 이 메소드를 부른다 (JSP · WebSquare · Nexacro 화면이 요청하는 주소나 거래 ID 의 처리 메소드)",
		INCLUDES: "화면이 다른 화면을 끼워 넣거나 띄운다"
	};

	var TO_KIND = {
		METHOD: "프로젝트 안의 메소드",
		TYPE: "프로젝트 안의 클래스 / 인터페이스",
		FIELD: "프로젝트 안의 필드",
		EXTERNAL_METHOD: "프로젝트 밖(JDK, 라이브러리)의 메소드",
		EXTERNAL_TYPE: "프로젝트 밖의 타입",
		EXTERNAL_FIELD: "프로젝트 밖의 필드",
		SQL: "SQL statement",
		TABLE: "DB 테이블",
		FILE: "파일 (화면: JSP · WebSquare · Nexacro)"
	};

	var CONFIDENCE = {
		HIGH: "확실하다",
		MEDIUM: "대체로 맞다 (타입은 알고 메소드를 이름으로 골랐거나, 글자가 같은 것으로 이은 경우)",
		LOW: "이름만 보고 짐작했다. 참고로만 본다",
		UNRESOLVED: "이름은 알아냈지만 그런 대상이 없다 (예: 매퍼에 없는 SQL 이름)"
	};

	var ENDPOINT_TYPE = {
		HTTP: "웹 주소. @RequestMapping 계열이나 Struts 설정으로 정해진 것",
		SERVLET: "web.xml 에 적힌 서블릿 매핑",
		MAIN: "main 메소드 (배치 · 독립 실행 프로그램의 시작점)",
		SCHEDULED: "스케줄 작업 (@Scheduled)",
		LISTENER: "메시지 / 이벤트를 받는 메소드",
		THREAD: "스레드로 도는 코드 (Runnable, Thread, Callable)",
		JSP: "주소로 바로 열 수 있는 JSP (WEB-INF 밖에 있는 것)",
		TRANSACTION: "거래 ID 로 불리는 메소드. 화면이 주소 대신 거래 ID 를 넘겨 부른다 (주소 칸에 거래 ID 가 나온다)"
	};

	var LAYER = {
		CONTROLLER: "요청을 받는 곳 (컨트롤러, 액션, 서블릿)",
		SERVICE: "업무 로직",
		REPOSITORY: "DB 접근 (DAO, Mapper)",
		MODEL: "데이터를 담는 클래스 (VO, DTO, Entity)",
		VIEW: "화면 (JSP 안의 Java 코드)",
		UTIL: "공통 도구",
		CONFIG: "설정 클래스",
		COMPONENT: "그 밖의 Spring 빈",
		ASPECT: "AOP",
		EXCEPTION: "예외 클래스",
		WEB_FILTER: "필터 / 인터셉터",
		BATCH: "배치",
		"(없음)": "어느 계층인지 정하지 못함"
	};

	var LAYER_CONFIDENCE = {
		HIGH: "애노테이션(@Controller 등)이나 설정 파일로 확정",
		MEDIUM: "상속으로 판단 (HttpServlet 을 물려받음 등)",
		LOW: "이름 끝(…Service, …DAO, …VO)으로 추정",
		"": "정하지 못함"
	};

	var FILE_TYPE = {
		SOURCE: "Java 소스",
		JSP: "JSP 화면",
		WEBSQUARE: "WebSquare 화면 (XML)",
		NEXACRO: "Nexacro / X-Platform 화면 (.xfdl)",
		NEXACRO_APP: "Nexacro / X-Platform 애플리케이션 정의 (.xadl)",
		MYBATIS_MAPPER: "MyBatis SQL 매퍼",
		IBATIS_MAPPER: "iBATIS SQL 매퍼",
		QUERY_XML: "쿼리 XML (JEF 계열 프레임워크의 document / query / statement)",
		MYBATIS_CONFIG: "MyBatis 설정",
		SPRING_XML: "Spring 설정 XML",
		STRUTS_CONFIG: "Struts 설정",
		WEB_XML: "web.xml",
		CONFIG: "설정 파일 (.properties, YAML)",
		BUILD: "빌드 파일 (pom.xml, build.gradle)",
		LOG_CONFIG: "로그 설정",
		XML: "그 밖의 XML"
	};

	var TYPE_KIND = {
		CLASS: "클래스",
		INTERFACE: "인터페이스",
		ENUM: "enum",
		RECORD: "record",
		ANNOTATION: "애노테이션",
		ANONYMOUS: "익명 클래스 (new Runnable() { … } 처럼 이름 없이 만든 것)"
	};

	var DOC_TYPE = {
		FILE: "파일 하나",
		TYPE: "클래스 / 인터페이스 하나",
		METHOD: "메소드 하나",
		MAPPER: "SQL statement 하나",
		VIEW: "화면 하나 (JSP · WebSquare · Nexacro)",
		UPLOAD: "올린 일반 문서"
	};

	var METRIC = {
		parseSuccessRate: "Java 파일 가운데 문법을 읽는 데 성공한 비율(%). 너무 커서 건너뛴 파일은 계산에서 뺀다. 100 이 정상",
		callCount: "소스에서 찾은 메소드 호출의 수",
		callResolutionRate: "호출 가운데 대상이 정해진 비율(%) = (프로젝트 안에서 풀린 것 + 프로젝트 밖의 것으로 풀린 것) ÷ 전체 호출. 가장 중요한 숫자",
		callHeuristicRate: "호출 가운데 이름으로 짐작해서 이은 비율(%). 낮을수록 좋다",
		callUnresolvedRate: "호출 가운데 끝내 풀지 못한 비율(%). 낮을수록 좋다",
		typeResolutionRate: "타입을 가리키는 참조(타입 사용, 상속, 구현, 예외, new) 가운데 풀린 비율(%)",
		relationCount: "관계의 수. LINK 단계가 계산한 시점의 값이라, 그 뒤 SEMANTIC 단계가 더하는 SQL · 테이블 · 화면 관계는 들어 있지 않다(위 '관계' 카드보다 작다)"
	};

	var SYNTHETIC_ORIGIN = {
		DEFAULT_CONSTRUCTOR: "생성자를 하나도 적지 않은 클래스에 컴파일러가 넣어 주는 기본 생성자",
		LOMBOK_GETTER: "Lombok 이 만드는 getter",
		LOMBOK_SETTER: "Lombok 이 만드는 setter",
		LOMBOK_BUILDER: "Lombok @Builder 가 만드는 것",
		LOMBOK_NO_ARGS_CONSTRUCTOR: "Lombok 이 만드는 기본 생성자",
		LOMBOK_ALL_ARGS_CONSTRUCTOR: "Lombok 이 만드는 전체 필드 생성자",
		LOMBOK_REQUIRED_ARGS_CONSTRUCTOR: "Lombok 이 만드는 필수 필드 생성자",
		RECORD_ACCESSOR: "record 의 구성 요소 조회 메소드",
		RECORD_CONSTRUCTOR: "record 의 생성자"
	};

	var RESOURCE_KIND = {
		CONFIG: "설정 파일의 키 = 값 한 줄",
		"RESOURCE:SPRING_BEAN": "Spring 설정 XML 에 적은 빈",
		"RESOURCE:COMPONENT_SCAN": "컴포넌트 스캔 범위",
		"RESOURCE:SPRING_IMPORT": "Spring 설정 XML 의 import",
		"RESOURCE:DEPENDENCY": "빌드 파일의 의존성 (라이브러리)",
		"RESOURCE:SERVLET_MAPPING": "web.xml 의 서블릿 매핑",
		"RESOURCE:FILTER_MAPPING": "web.xml 의 필터 매핑",
		"RESOURCE:LISTENER": "web.xml 의 리스너"
	};

	var MEMBER = {
		methods: "소스에 적힌 메소드 (생성자와 자동 생성 멤버 제외)",
		constructors: "소스에 적힌 생성자",
		syntheticMethods: "소스에는 없지만 컴파일하면 생기는 메소드 · 생성자 (기본 생성자, Lombok 의 getter / setter 등). 이것을 부르는 호출을 풀기 위해 넣어 둔다",
		fields: "소스에 적힌 필드",
		syntheticFields: "소스에는 없지만 생기는 필드 (Lombok @Slf4j 의 log 등)",
		annotations: "클래스 · 메소드 · 필드 등에 붙은 애노테이션의 수"
	};

	var CARRY_FILE = {
		files: "이 리비전의 전체 파일 수",
		unchangedFiles: "기준 리비전과 내용이 같은 파일. 다시 분석하지 않고 결과를 옮겨 왔다",
		changedFiles: "경로는 같은데 내용이 달라진 파일. 다시 분석했다",
		addedFiles: "새로 생긴 파일",
		removedFiles: "기준 리비전에는 있었는데 없어진 파일"
	};

	/** SQL 자원의 kind 는 "MAPPER:프레임워크:종류" 모양이라 풀어서 설명한다 */
	function resourceKind(code) {
		if (RESOURCE_KIND[code]) {
			return RESOURCE_KIND[code];
		}
		var parts = String(code).split(":");
		if (parts[0] === "MAPPER" && parts.length === 3) {
			var framework = parts[1] === "IBATIS" ? "iBATIS 매퍼" : (parts[1] === "QUERY_XML" ? "쿼리 XML(JEF 계열)" : "MyBatis 매퍼");
			return framework + "의 " + (parts[2] === "SQL_FRAGMENT" ? "SQL 조각(<sql>. 다른 SQL 이 끼워 넣어 쓰는 부분)" : parts[2] + " 문");
		}
		return "";
	}
	// 사용 안내 화면에서 보여 줄 대표 값들
	resourceKind.samples = ["MAPPER:MYBATIS:SELECT", "MAPPER:MYBATIS:INSERT", "MAPPER:MYBATIS:SQL_FRAGMENT", "MAPPER:IBATIS:SELECT", "CONFIG", "RESOURCE:SPRING_BEAN",
		"RESOURCE:COMPONENT_SCAN", "RESOURCE:DEPENDENCY", "RESOURCE:SERVLET_MAPPING", "RESOURCE:FILTER_MAPPING", "RESOURCE:LISTENER"];

	/* ---------- 리비전 요약의 표들 ----------
	 * path     응답에서 값을 꺼내는 경로
	 * title    표의 제목
	 * what     이 표가 무엇을 보여 주는지
	 * columns  [열 이름, 한글 이름, 뜻, 값의 뜻 사전(없으면 생략)]
	 * read     이 표를 어떻게 읽고, 어떤 숫자가 나오면 무엇을 해야 하는지
	 * pairs    true 면 "항목 | 값" 모양의 표(열이 아니라 행마다 뜻이 다르다). rows 에 행의 뜻을 준다
	 */
	var SECTIONS = [
		{
			path: "relations.metrics", title: "품질 지표",
			what: "분석이 얼마나 잘 됐는지를 숫자 몇 개로 줄인 것입니다. 요약에서 가장 먼저 볼 표입니다.",
			columns: [
				["name", "지표", "지표의 이름", METRIC],
				["value", "값", "비율은 0 ~ 100 의 %, 나머지는 건수"],
				["analysisId", "계산한 Job", "이 지표를 계산한 분석 Job. 지표는 LINK 단계에서 계산하므로, LINK 를 다시 돌리지 않은 재실행(예: DOCUMENT 부터 다시)에서는 그대로 남는다"]
			],
			read: "대략의 기준으로, callResolutionRate(호출이 풀린 비율)가 95 이상이면 호출 관계 · 영향도 결과를 믿고 써도 됩니다. 80 아래면 라이브러리(jar)를 찾지 못한 경우가 많습니다. "
				+ "프로젝트 등록 칸에 라이브러리 위치를 적고 다시 분석하세요. parseSuccessRate 가 100 이 아니면 읽지 못한 Java 파일이 있다는 뜻이고, 어느 파일인지는 Job 의 오류 표에 나옵니다."
		},
		{
			path: "relations.byType", title: "관계 (종류 · 신뢰도별)",
			what: "분석이 알아낸 \"A 가 B 를 ○○한다\"의 수입니다. 호출 관계 · 영향도 · 리비전 비교가 모두 이 관계를 따라갑니다.",
			columns: [
				["relationType", "관계", "무슨 관계인지", RELATION_TYPE],
				["toKind", "가리키는 것", "관계의 끝이 무엇인지. EXTERNAL 로 시작하면 프로젝트 밖(JDK, 라이브러리)", TO_KIND],
				["confidence", "신뢰도", "얼마나 확실한지", CONFIDENCE],
				["count", "건수", "그런 관계의 수"]
			],
			read: "CALLS → METHOD 의 HIGH 가 대부분이면 좋습니다. LOW 가 많으면 호출 관계를 조심해서 봅니다. "
				+ "READS_TABLE / WRITES_TABLE 의 MEDIUM 은 SQL 이 복잡해서 정식 SQL 분석 대신 글자 모양(FROM, JOIN 뒤의 이름)으로 테이블을 찾은 것입니다. 테이블 이름은 대체로 맞지만 읽기 / 쓰기 구분이 덜 정확할 수 있습니다. "
				+ "EXECUTES_SQL 의 UNRESOLVED 는 코드가 부르는 SQL 이름이 매퍼에 없는 것입니다(이름이 틀렸거나 매퍼 파일이 분석 대상에 없음)."
		},
		{
			path: "relations.unresolvedReasons", title: "풀지 못한 참조의 이유",
			what: "\"누구를 가리키는지\" 끝내 정하지 못했거나 일부러 뺀 참조를 이유별로 모은 것입니다.",
			columns: [
				["refKind", "참조의 종류", "무엇을 하려던 참조인지", REF_KIND],
				["status", "상태", "못 푼 것인지, 뺀 것인지", REFERENCE_STATUS],
				["reason", "이유", "그렇게 된 이유"],
				["count", "건수", "같은 이유의 수"],
				["example", "예", "그 가운데 하나"]
			],
			read: "상태가 IGNORED 인 줄은 문제가 아닙니다(배열의 length 처럼 관계로 만들 것이 아닌 것). 볼 것은 UNRESOLVED 입니다. "
				+ "\"프로젝트에 같은 이름의 메소드가 없습니다\"가 많으면 라이브러리(jar)가 빠진 것이고, \"프로젝트 밖의 상위 타입에서 물려받은 메소드로 보입니다\"는 프레임워크 클래스를 물려받아 쓰는 메소드라 jar 가 있으면 풀립니다."
		},
		{
			path: "semantic.endpoints", title: "진입점",
			what: "바깥에서 이 프로그램으로 들어오는 입구의 수입니다. 영향도 분석이 \"어느 주소가 영향을 받는지\"를 말할 때 쓰는 것이 이 진입점입니다.",
			columns: [
				["endpointType", "종류", "어떤 입구인지", ENDPOINT_TYPE],
				["count", "건수", "그 종류의 입구 수. 메소드 하나에 주소를 둘 적으면 둘로 센다"]
			],
			read: "웹 프로그램인데 HTTP 가 0 이면 주소를 알아내지 못한 것입니다(지원하지 않는 프레임워크이거나 설정 파일이 분석 대상에 없음). 그러면 영향도 분석의 '진입점' 표가 비어 나옵니다."
		},
		{
			path: "semantic.layers", title: "계층",
			what: "클래스를 역할별로 나눈 것입니다. 검색에서 \"컨트롤러만\"처럼 좁히거나, 영향도 결과를 계층별로 볼 때 씁니다.",
			columns: [
				["layer", "계층", "클래스의 역할", LAYER],
				["confidence", "근거", "무엇을 보고 그렇게 나눴는지", LAYER_CONFIDENCE],
				["count", "클래스 수", "그 계층의 클래스 수 (익명 클래스 제외)"]
			],
			read: "LOW 는 이름만 보고 나눈 것이라 틀릴 수 있고, 이름 규칙에 맞지 않는 클래스는 '(없음)'이 됩니다. 계층이 틀려도 호출 관계 · 영향도에는 영향이 없습니다."
		},
		{
			path: "semantic.resources", title: "Java 밖의 자원 (SQL, 설정, 빈 …)",
			what: "Java 소스가 아닌 파일에서 읽어 낸 것들입니다. SQL statement 가 여기 잡혀야 \"메소드 → SQL → 테이블\"이 이어집니다.",
			columns: [
				["kind", "종류", "무엇을 읽었는지", resourceKind],
				["count", "건수", "그 종류의 수"]
			],
			read: "MyBatis / iBATIS 를 쓰는 프로젝트인데 MAPPER 로 시작하는 줄이 없으면 매퍼 XML 이 소스 폴더 안에 없는 것입니다. 그러면 테이블에서 출발하는 영향도 분석을 할 수 없습니다."
		},
		{
			path: "files.byType", title: "파일 (종류별)",
			what: "소스 폴더에서 찾은 파일을 언어와 쓰임새로 나눈 것입니다.",
			columns: [
				["language", "언어", "파일의 언어 (JAVA, JSP, XML, PROPERTIES, YAML …)"],
				["fileType", "쓰임새", "무슨 파일인지", FILE_TYPE],
				["fileCount", "파일 수", ""],
				["lineCount", "줄 수", "그 파일들의 줄 수 합"],
				["sizeBytes", "크기(바이트)", "그 파일들의 크기 합"]
			],
			read: "생각한 것보다 Java 파일이 적으면 소스 폴더를 잘못 가리킨 것입니다. target, build, node_modules, .git 같은 폴더는 일부러 훑지 않습니다."
		},
		{
			path: "files.byEncoding", title: "파일 (인코딩별)",
			what: "파일마다 알아낸 글자 인코딩입니다. 오래된 프로젝트는 EUC-KR 과 UTF-8 이 섞여 있어서 파일마다 따로 알아냅니다.",
			columns: [
				["encoding", "인코딩", "알아낸 인코딩. US-ASCII 는 한글이 없는 파일"],
				["fileCount", "파일 수", ""]
			],
			read: "검색 결과의 한글 주석이 깨져 보이면 그 파일의 인코딩을 잘못 알아낸 것입니다."
		},
		{
			path: "files.javaSourceRoots", title: "Java 소스 루트",
			what: "Java 소스가 들어 있는 폴더(패키지가 시작되는 곳)입니다. 예: src/main/java, WEB-INF/classes.",
			columns: [
				["module", "모듈", "멀티모듈 프로젝트에서 어느 모듈인지. 모듈이 하나면 비어 있다"],
				["sourceRoot", "소스 루트", "패키지가 시작되는 폴더"],
				["fileCount", "파일 수", "그 아래의 Java 파일 수"]
			],
			read: "테스트 코드(src/test/java)가 따로 한 줄로 나오면 정상입니다."
		},
		{
			path: "declarations.javaFiles", title: "Java 파일의 문법 읽기 결과",
			what: "Java 파일을 문법대로 읽는 데 성공했는지와, 어느 Java 버전의 문법으로 읽혔는지입니다.",
			columns: [
				["parseStatus", "결과", "OK 는 성공, FAILED 는 문법을 읽지 못함, SKIPPED 는 너무 커서 건너뜀"],
				["languageLevel", "문법 수준", "읽는 데 성공한 Java 버전. 최신(21)부터 시도하고, 안 되면 8, 1.4 순으로 낮춘다. enum 을 변수 이름으로 쓰는 옛 소스는 1.4 로 읽힌다"],
				["fileCount", "파일 수", ""]
			],
			read: "FAILED 가 있으면 그 파일의 메소드는 분석 결과에 없습니다. 컴파일되지 않는 소스이거나 Java 가 아닌 내용이 든 파일입니다."
		},
		{
			path: "declarations.types", title: "타입",
			what: "찾아낸 클래스 · 인터페이스 등의 수입니다.",
			columns: [
				["kind", "종류", "", TYPE_KIND],
				["count", "건수", ""]
			],
			read: "이름 있는 타입 + 익명 클래스의 수는 그 프로젝트를 컴파일했을 때 생기는 .class 파일 수와 같습니다. 분석이 타입을 빠뜨리지 않았는지 확인하는 쉬운 방법입니다."
		},
		{
			path: "declarations.members", title: "멤버", pairs: true,
			what: "찾아낸 메소드 · 필드 · 애노테이션의 수입니다.",
			rows: MEMBER,
			read: "syntheticMethods(자동 생성 멤버)는 소스에 없는 것이라 검색 문서로는 만들지 않습니다. Lombok 을 쓰는 프로젝트에서 이 숫자가 크게 나오는 것은 정상입니다."
		},
		{
			path: "declarations.synthetic", title: "자동 생성 멤버의 출처",
			what: "소스에는 없지만 컴파일하면 생기는 멤버를 어디서 왔는지로 나눈 것입니다.",
			columns: [
				["origin", "출처", "", SYNTHETIC_ORIGIN],
				["count", "건수", ""]
			],
			read: ""
		},
		{
			path: "declarations.references", title: "참조",
			what: "소스에서 찾은 \"다른 것을 가리키는 자리\"(호출, new, 타입 사용 …)와 그것이 풀린 결과입니다. 위의 '관계'는 이 참조를 풀어서 만든 것입니다.",
			columns: [
				["refKind", "참조의 종류", "", REF_KIND],
				["status", "풀린 결과", "", REFERENCE_STATUS],
				["count", "건수", ""]
			],
			read: "EXTERNAL 이 많은 것은 정상입니다(String, List 같은 JDK 와 프레임워크를 쓰는 자리가 가장 많다). CALL 의 UNRESOLVED 와 HEURISTIC 이 적을수록 좋습니다."
		},
		{
			path: "declarations.filePasses", title: "파일별 단계 결과",
			what: "단계마다 파일을 몇 개 처리했고 몇 개를 건너뛰거나 실패했는지입니다.",
			columns: [
				["pass", "단계", "", PASS],
				["status", "결과", "", STATUS],
				["fileCount", "파일 수", ""]
			],
			read: "SKIPPED 는 대부분 정상입니다(DECLARE 의 SKIPPED = Java 코드가 없는 JSP, RESOURCE 의 SKIPPED = 읽을 필요 없는 XML). FAILED 가 있으면 그 파일은 그 단계의 결과가 없습니다. 어느 파일인지는 Job 의 오류 표에 나옵니다."
		},
		{
			path: "passes", title: "단계별 진행",
			what: "분석은 아래 단계를 차례로 지나갑니다. 단계마다 언제 돌았고 끝났는지입니다.",
			columns: [
				["pass", "단계", "", PASS],
				["status", "상태", "", STATUS],
				["analysisId", "돌린 Job", "이 단계를 마지막으로 돌린 분석 Job. 이어서 분석하거나 일부 단계만 다시 돌리면 단계마다 Job 이 다를 수 있다"],
				["totalCount", "처리할 건수", "이 단계가 처리할 파일 수. SQL 만으로 도는 단계(LINK, SEMANTIC, CARRY)는 0"],
				["doneCount", "처리한 건수", ""],
				["startedAt", "시작", ""],
				["endedAt", "끝", ""]
			],
			read: "모든 단계가 DONE 이어야 리비전이 READY 가 됩니다. 중간에 실패하거나 취소했으면, 같은 라벨로 다시 분석을 시작해 끝나지 않은 단계부터 이어 갈 수 있습니다."
		},
		{
			path: "incremental.files", title: "증분 분석: 파일", pairs: true,
			what: "증분 분석으로 만든 리비전에만 나옵니다. 기준(앞) 리비전과 견주어 파일이 얼마나 달라졌는지입니다.",
			rows: CARRY_FILE,
			read: "unchangedFiles 가 대부분이면 증분 분석의 효과가 큽니다."
		},
		{
			path: "incremental.passes", title: "증분 분석: 단계별",
			what: "단계마다 결과를 옮겨 온 파일과 실제로 다시 분석한 파일의 수입니다.",
			columns: [
				["pass", "단계", "", PASS],
				["carriedFiles", "옮겨 온 파일", "다시 분석하지 않고 앞 리비전의 결과를 복사한 파일"],
				["analyzedFiles", "분석한 파일", "실제로 처리한 파일 (건너뛴 것 포함)"]
			],
			read: "RESOLVE 의 '분석한 파일'이 바뀐 파일 수보다 많은 것은 정상입니다. 파일이 그대로여도 그 파일이 부르는 메소드가 바뀌었으면 다시 풀어야 하기 때문입니다."
		},
		{
			path: "rag.documents", title: "검색 문서",
			what: "검색에 쓰려고 분석 결과로 만든 글입니다. 검색 결과 한 건이 이 문서의 조각(청크) 하나입니다.",
			columns: [
				["docType", "문서의 단위", "", DOC_TYPE],
				["documents", "문서 수", ""],
				["chunks", "청크 수", "문서를 검색 단위로 자른 조각의 수. 긴 메소드는 여러 조각이 된다"],
				["chars", "글자 수", ""]
			],
			read: "METHOD 문서 수가 메소드 수보다 적은 것은 정상입니다. 필드 값을 그대로 주고받는 짧은 getter / setter 는 문서를 따로 만들지 않습니다(클래스 문서의 메소드 목록에는 들어 있다)."
		},
		{
			path: "rag.embedding", title: "임베딩 진행",
			what: "\"뜻으로 찾기\"를 하려면 청크를 숫자(벡터)로 바꿔 둬야 합니다. 그 일이 얼마나 됐는지입니다. 분석이 끝난 뒤에도 뒤에서 계속 진행됩니다.",
			columns: [
				["status", "상태", "DONE 끝남 / PENDING 대기 중 / FAILED 세 번 시도해도 실패 / NONE 대기열에 없음"],
				["chunks", "청크 수", ""]
			],
			read: "PENDING 이 남아 있는 동안에는 뜻으로 찾는 검색이 DONE 인 청크에서만 됩니다. 이름으로 찾는 검색, 호출 관계, 영향도, 리비전 비교는 임베딩과 상관없이 바로 됩니다. 이 서버는 1분에 약 100 청크를 처리합니다."
		},
		{
			path: "jobs", title: "Job 이력",
			what: "이 리비전을 만들거나 다시 돌린 분석 실행의 목록입니다(최근 것이 위).",
			columns: [
				["analysisId", "Job", "분석 실행의 번호. A + 날짜 + 그날의 일련번호"],
				["status", "상태", "", STATUS],
				["currentPass", "마지막 단계", "끝난 Job 이면 마지막으로 돈 단계, 도는 중이면 지금 단계"],
				["totalFiles", "전체 파일", ""],
				["doneFiles", "처리한 파일", ""],
				["errorMessage", "실패 이유", "Job 전체가 실패했을 때의 이유"],
				["startedAt", "시작", ""],
				["endedAt", "끝", ""]
			],
			read: ""
		}
	];

	/** 요약 맨 위의 숫자 카드 */
	var CARDS = [
		["파일", "소스 폴더에서 찾은 모든 파일 (Java, JSP, XML, 설정 …)"],
		["타입", "클래스 · 인터페이스 · enum · 익명 클래스를 합친 수"],
		["관계", "알아낸 모든 관계의 수 (호출, 상속, SQL 실행, 테이블 사용, 화면 연결 …)"],
		["진입점", "바깥에서 들어오는 입구의 수 (웹 주소, 스케줄, main …)"],
		["검색 문서", "검색에 쓰려고 만든 글의 수"]
	];

	/** 값의 뜻을 찾습니다. 사전이 함수면 불러서 얻습니다 */
	function meaningOf(dictionary, value) {
		if (!dictionary) {
			return "";
		}
		var key = value === null || value === undefined ? "" : String(value);
		return typeof dictionary === "function" ? dictionary(key) : (dictionary[key] || "");
	}

	return {
		SECTIONS: SECTIONS,
		CARDS: CARDS,
		PASS: PASS,
		CONFIDENCE: CONFIDENCE,
		meaningOf: meaningOf
	};
})();

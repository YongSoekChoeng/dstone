# localcommandcaveatTh

> 생성일시: 2026-11-11 16:28

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="ba60">
dstone-knowledge 에서 SEMANTIC 스텝 처리를 정리하고 있어. 내용을 수정해줘.

<SEMANTIC 스텝 처리 순서 및 성격> - rule 이외의 작업은 framework 에서 처리.
----------------------------------------------------------------------------------------------------------------------------
| #   | 처리기                   | rule            | 하는 일                                                                   |
| --- | ---------------------- | -------------------------------------------------------------------------------------------
| 1   | ServletEntries         |                 | web.xml의 서블릿·필터·리스너, @WebServlet. (규칙 없이 확실한 입구)
| 2   | StrutsEntries          |                 | struts-config.xml·struts.xml의 액션 → 입구, 액션 클래스 계층, 이동할 JSP. 액션 클래스를 규칙보다 먼저 CONTROLLER로 정한다.
| 3   | LayerResolver          | layer           | 클래스의 계층. 입구보다 먼저. 상위 클래스의 주소를 물려받는 컨트롤러를 알아보려고 
| 4   | EntryFinder            | entry           | 애노테이션·상속·설정 XML로 입구, main(), 바로 열리는 JSP. 화면 연결보다 먼저(화면 글자와 견줄 주소가 있어야 한다)
| 5   | WiringMarker           | inject, proxy   | 주입 관계, 프록시가 끼는 메소드.
| 6   | JpaQueryReader         | jpa             | JPA 쿼리를 statement로. MapperLinker보다 먼저(이름 있는 쿼리를 이름으로 찾게)
| 7   | MapperLinker           |                 | SQL 관계를 모두 지우고, 매퍼 인터페이스 방식 연결과 매퍼 SQL → 테이블.
| 8   | SqlLinker              | sql             | 호출 인자로 SQL 이름을 계산해 메소드 → statement. MapperLinker 다음.
| 9   | JavaSqlLinker          | jpa > jpql      | Java 문자열 SQL·JPA를 메소드·테이블에. SqlLinker 다음.
| 10  | ViewLinker             | view            | 메소드 → JSP(RENDERS). 화면 연결은 입구가 다 만들어진 뒤 
| 11  | JspLinker              |                 | JSP → 메소드(REQUESTS), JSP → JSP(INCLUDES), JSP 화면명.
| 12  | ScreenLinker           | screen          | WebSquare·Nexacro 화면 → 메소드·화면, 거래 입구, 화면명.
| 13  | ScreenMenuReader       | screen > menu   | 메뉴 목록에서 화면명. 화면 파일의 제목이 먼저
| 14  | SourceNamePlugin       |                 | Java 소스에서 테이블·컬럼 한글명. SQL에 쓰인 컬럼이 다 모인 뒤
| 15  | MethodTitlePlugin      |                 | 메소드 기능명.
| 16  | ScreenDictionaryPlugin |                 | 화면 사전 CSV의 한글명.
| 17  | TableDictionaryPlugin  |                 | 테이블·컬럼 사전 CSV.
----------------------------------------------------------------------------------------------------------------------------

==>
----------------------------------------------------------------------------------------------------------------------------
| #   | 처리기                   | rule  | 처리순서                      | 하는 일                                                 |
| --- | ---------------------- | -------------------------------------------------------------------------------------------
... 
----------------------------------------------------------------------------------------------------------------------------
| 2   | StrutsEntries          |       | aaa.Aaa클래스.aaa메소드() - 작업내용             | web.xml의 서블릿·필터·리스너, @WebServlet. (규칙 없이 확실한 입구)
|     |                        |       |    aaa.Abb클래스.bbb메소드() - 작업내용          |
|      |                           |       |      ....                                 |
----------------------------------------------------------------------------------------------------------------------------
| 3   | LayerResolver          |layer  | aaa.Aaa클래스.aaa메소드() - 작업내용             | 클래스의 계층. 입구보다 먼저. 상위 클래스의 주소를 물려받는 컨트롤러를 알아보려고 ...
|     |                        |       |    aaa.Abb클래스.bbb메소드() - 작업내용          |
|      |                           |       |      ....                                 |
----------------------------------------------------------------------------------------------------------------------------
....
</pasted_content id="ba60">

---

## 답변

SEMANTIC 처리기 코드를 먼저 확인하겠습니다.

---

## 답변

Now let me read each step's `run()` and the methods it calls.

---

## 답변

Now ScreenLinker (largest) — I'll look at its `run()` and main private methods.

---

## 답변

Now the remaining five: ScreenMenuReader and the four dictionary plugins.

---

## 답변

SEMANTIC 처리기 17개의 `run()`과 거기서 부르는 메소드, SQL까지 확인해서 표를 다시 정리했습니다. 처리 순서 칸은 실제 호출 순서대로 적었고, 들여쓰기는 "이 메소드 안에서 부른다"는 뜻입니다. 0번 행에는 처리기를 돌리는 프레임워크 부분(`SemanticPass`)을 따로 넣었습니다.

```
<SEMANTIC 스텝 처리 순서 및 성격> - rule 이외의 작업은 framework 에서 처리.
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| #   | 처리기                   | rule          | 처리순서                                                                       | 하는 일
| --- | ---------------------- | ------------- | ------------------------------------------------------------------------------ | -----------------------------------------------
| 0   | SemanticPass           | (규칙 읽기)     | SemanticPass.run() - 규칙 폴더를 읽어 context에 싣고 트랜잭션을 하나 연다                       | 처리기 실행 틀. 전체가 한 트랜잭션이라 실패하면 지운 것까지 되돌아간다.
|     | (framework)            |               |   RuleFiles.rulesFor() - projects/{ID} > common > guess 순으로 규칙을 읽는다            |
|     |                        |               |   SemanticPass.runSteps()                                                      |
|     |                        |               |     SemanticDao.clear() - 이 리비전의 endpoint, SEMANTIC이 만든 resource·relation 삭제, |
|     |                        |               |                           layer·proxy_related 초기화                             |
|     |                        |               |     SemanticPass.recordRuleFiles() - 쓰는 규칙 파일마다 RULE_SET 행(파일 해시)            |
|     |                        |               |     SemanticPass.steps() - 아래 1~17을 차례로 run()                                  |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 1   | ServletEntries         |               | ServletEntries.run()                                                           | web.xml의 서블릿·필터·리스너, @WebServlet. 규칙 없이도 확실한 입구.
|     |                        |               |   SemanticDao.selectFilesByType("WEB_XML") - web.xml 목록                        |
|     |                        |               |   WebXmlReader.read() - 서블릿·매핑·필터·리스너 읽기 (실패하면 reporter.error() 후 다음 파일)  |
|     |                        |               |   ServletEntries.addServletEndpoints() - <servlet-mapping>마다                    |
|     |                        |               |     addResource() → SemanticDao.insertResource() - SERVLET_MAPPING              |
|     |                        |               |     SymbolDao.selectTypeLocation() - 서블릿 클래스 찾기 (없으면 external, 메소드 없는 입구)   |
|     |                        |               |     SemanticDao.selectServletHandlers() - doGet·doPost·service 찾기               |
|     |                        |               |     addEndpoint() → SemanticDao.insertEndpoint() - SERVLET 입구                  |
|     |                        |               |                     (doGet → GET, service → ALL, 메소드 없으면 타입만)               |
|     |                        |               |   ServletEntries.addResources() → insertResource() - FILTER_MAPPING, LISTENER   |
|     |                        |               |   SemanticDao.insertWebServletEndpoints() - @WebServlet 입구 (SQL 한 번)          |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 2   | StrutsEntries          |               | StrutsEntries.run()                                                            | struts-config.xml·struts.xml의 액션 → 입구, 액션 클래스 계층, 이동할 JSP.
|     |                        |               |   SemanticDao.selectFilesByType("STRUTS_CONFIG") - 없으면 바로 끝                 | 액션 클래스를 규칙보다 먼저 CONTROLLER로 정한다.
|     |                        |               |   StrutsEntries.strutsUrlPatterns() → SemanticDao.selectServletMappings()      |
|     |                        |               |       - 1번이 넣은 ActionServlet의 url-pattern (없으면 *.do)                      |
|     |                        |               |   StrutsConfigReader.read() - 액션·forward 읽기                                  |
|     |                        |               |   StrutsEntries.addAction() - 액션마다                                          |
|     |                        |               |     applyPattern() / struts2Extension() - 주소 만들기 (Struts1: url-pattern,      |
|     |                        |               |                                           Struts2: .action 또는 설정된 확장자)       |
|     |                        |               |     SymbolDao.selectTypeLocation() - 액션 클래스                                  |
|     |                        |               |     SemanticDao.updateLayerBySymbol(.., "CONTROLLER") - 계층 지정                 |
|     |                        |               |     SemanticDao.selectMethodsByName() - execute·perform (Struts2는 method 속성)   |
|     |                        |               |     addEndpoint() → SemanticDao.insertEndpoint() - HTTP 입구                     |
|     |                        |               |     jspPathOf() → SemanticDao.selectJspByPathEnd() - forward JSP가 하나일 때만       |
|     |                        |               |     SemanticDao.insertRelation() - 메소드 → JSP (RENDERS)                         |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 3   | LayerResolver          | layer         | LayerResolver.run()                                                            | 클래스의 계층. 입구보다 먼저 돈다.
|     |                        |               |   SemanticDao.updateJspLayer() - JSP에서 만든 타입은 규칙과 상관없이 VIEW               | 상위 클래스의 주소를 물려받는 컨트롤러를 알아보려면 계층이 있어야 한다.
|     |                        |               |   ProjectRules.layers() - 규칙 목록 (프로젝트 > 공통 > 짐작)                         |
|     |                        |               |   SemanticDao.updateLayerByRule() - 규칙마다 UPDATE 한 번                          |
|     |                        |               |       (@애노테이션 / 상위 타입 / 이름 정규식. layer IS NULL만 → 앞선 규칙이 이긴다)       |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 4   | EntryFinder            | entry         | EntryFinder.run()                                                              | 애노테이션·상속·설정 XML로 입구, main(), 바로 열리는 JSP.
|     |                        |               |   EntryFinder.insertHttpEntries() - entry의 http 규칙                           | 화면 연결보다 먼저 돈다(화면 글자와 견줄 주소가 있어야 한다).
|     |                        |               |     SemanticDao.selectHttpMappingAnnotations() - @RequestMapping 등             |
|     |                        |               |     EntryFinder.typeGraph() → selectTypeEdges()·selectTypeFlags() - 상속 그래프     |
|     |                        |               |     SemanticDao.selectTypeFlags()·selectMethodsOfTypes() - 관련 타입·메소드         |
|     |                        |               |     HttpEndpointBuilder.build() - 클래스+메소드 주소 합치기, 상위 타입에서 물려받기,        |
|     |                        |               |       ConstantEvaluator.evaluate()로 상수로 적은 주소 계산                           |
|     |                        |               |     SemanticDao.insertEndpoint() - HTTP 입구                                     |
|     |                        |               |   SemanticDao.insertAnnotationEntries() - 애노테이션 입구 (스케줄, 리스너 등)          |
|     |                        |               |   EntryFinder.insertXmlEntries() - entry의 xml 규칙                             |
|     |                        |               |     SemanticDao.selectFilesByLanguage("XML") + 규칙의 파일 패턴                     |
|     |                        |               |     XmlEndpointReader.read() - 규칙의 요소·속성에서 주소·클래스·메소드                    |
|     |                        |               |     addXmlEntry() → findType() → selectMethodsByName() → insertXmlEndpoint()    |
|     |                        |               |   SemanticDao.insertMainEndpoints() - main()                                   |
|     |                        |               |   SemanticDao.insertJspEndpoints() - 바로 열리는 JSP                              |
|     |                        |               |   EntryFinder.insertExtendsEntries() → SemanticDao.insertExtendsEntries()      |
|     |                        |               |       - 상위 타입 입구 (Thread.run 등), 메소드 조건이 같은 규칙끼리 묶어 SQL 한 번        |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 5   | WiringMarker           | inject, proxy | WiringMarker.run()                                                             | 주입 관계, 프록시가 끼는 메소드.
|     |                        |               |   SemanticDao.insertInjects() - 규칙 애노테이션이 붙은 필드의 타입 → INJECTS            |
|     |                        |               |   SemanticDao.markProxyRelated() - 메소드나 그 클래스에 규칙 애노테이션                  |
|     |                        |               |                                    (@Transactional 등) → proxy_related = TRUE  |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 6   | JpaQueryReader         | jpa           | JpaQueryReader.run()                                                           | JPA 쿼리를 statement로.
|     |                        |               |   JavaSqlDao.deleteJpaStatements() - 지난번 JPA statement 삭제 (규칙 없으면 여기서 끝) | MapperLinker보다 먼저 돈다(이름 있는 쿼리를 이름으로 찾게).
|     |                        |               |   JpaEntities.load() - 엔티티 → 테이블                                           |
|     |                        |               |   JpaQueryReader.addQueries() - 메소드의 @Query                                  |
|     |                        |               |     JavaSqlDao.selectAnnotations("METHOD") → AnnotationValues.parse()          |
|     |                        |               |     queryText() - 리터럴, 텍스트 블록, 상수(ConstantEvaluator)                        |
|     |                        |               |     insert() → JavaSqlDao.insertJpaStatement()                                 |
|     |                        |               |   JpaQueryReader.namedQueries() - 타입의 @NamedQuery, 묶음(@NamedQueries)은       |
|     |                        |               |                                   innerAnnotation()으로 풀어서                    |
|     |                        |               |   JpaQueryReader.addRepositoryMethods() - 저장소 인터페이스 메소드                    |
|     |                        |               |     JavaSqlDao.selectRepositories() → repositoryEntity() (타입 인자로 엔티티)        |
|     |                        |               |     JavaSqlDao.selectDeclaredMethods()                                         |
|     |                        |               |       - 엔티티.메소드 이름 쿼리가 있으면 addMethod()로 그 쿼리에 메소드를 붙인다            |
|     |                        |               |       - findBy·countBy… 모양이면 DERIVED statement insert                        |
|     |                        |               |   이름 있는 쿼리 insert() - 메소드 목록까지 채운 뒤 마지막에 넣는다                        |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 7   | MapperLinker           |               | MapperLinker.run()                                                             | SQL 관계를 모두 지우고, 매퍼 인터페이스 방식 연결과 매퍼 SQL → 테이블.
|     |                        |               |   ResourceDao.clearSqlRelations() - EXECUTES_SQL·READS_TABLE·WRITES_TABLE 삭제  |
|     |                        |               |   StatementIndex.load() - statement·조각 색인 (없으면 끝)                         |
|     |                        |               |   ResourceDao.insertInterfaceMapperRelations() - 매퍼 인터페이스 메소드 →              |
|     |                        |               |                                                  같은 이름 statement (EXECUTES_SQL) |
|     |                        |               |   MapperLinker.extractTables()                                                 |
|     |                        |               |     ResourceDao.selectMapperPage() - 500건씩                                     |
|     |                        |               |     expandIncludes() - <include refid> 풀기 (fragmentOf(), selectMapperBody())   |
|     |                        |               |     SqlTableExtractor.extract() - 테이블·CRUD·컬럼 (파서, 실패하면 정규식)              |
|     |                        |               |     ResourceDao.insertRelation() - SQL → 테이블 (READS_TABLE / WRITES_TABLE)      |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 8   | SqlLinker              | sql           | SqlLinker.run()                                                                | 호출 인자로 SQL 이름을 계산해 메소드 → statement.
|     |                        |               |   StatementIndex.load() - 없으면 끝                                               | MapperLinker 다음에 돈다.
|     |                        |               |   SqlLinker.groups() - 수신 타입·인자 위치·prefix·suffix가 같은 규칙끼리 묶는다          |
|     |                        |               |   SqlLinker.linkCalls() - 묶음마다                                              |
|     |                        |               |     ResourceDao.selectMapperCallPage() - 그 메소드 호출 500건씩                     |
|     |                        |               |     ConstantEvaluator.evaluate() - 인자를 문자열로 계산, prefix + 값 + suffix         |
|     |                        |               |     looksLikeStatementName()·skip 패턴 - SQL 이름이 아니면 버린다                   |
|     |                        |               |     StatementIndex.statementOf() - 없으면 id만으로 찾되 하나뿐일 때만                    |
|     |                        |               |     ResourceDao.insertRelation() - 메소드 → SQL (EXECUTES_SQL),                  |
|     |                        |               |                                    statement가 없으면 UNRESOLVED로 남긴다            |
|     |                        |               |   ResourceDao.updateMapperMethods() - statement에 대표 메소드 기록                   |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 9   | JavaSqlLinker          | jpa > jpql    | JavaSqlLinker.run()                                                            | Java 문자열 SQL·JPA를 메소드·테이블에 잇는다.
|     |                        |               |   JpaEntities.load(), ProjectRules.jpqlMethods()                               | SqlLinker 다음에 돈다.
|     |                        |               |   JavaSqlDao.selectStatementPage() - Java 문자열 SQL + JPA statement 500건씩      |
|     |                        |               |   JavaSqlLinker.languageOf() - JPA는 저장된 언어, 문자열은 jpql 규칙 메소드면 JPQL       |
|     |                        |               |   JavaSqlLinker.linkTables() - SQL → 테이블                                     |
|     |                        |               |     DERIVED: 엔티티의 테이블 / JPQL: JpqlTableExtractor.extract() /               |
|     |                        |               |     SQL: SqlTableExtractor.extract() → ResourceDao.insertRelation()            |
|     |                        |               |   JavaSqlLinker.linkMethods() - 메소드 → SQL (EXECUTES_SQL)                      |
|     |                        |               |     SQL을 쓴 메소드, 이름 있는 쿼리를 쓰는 메소드,                                       |
|     |                        |               |     SQL 상수 필드를 읽는 메소드 (JavaSqlDao.selectFieldReaders(), MEDIUM)             |
|     |                        |               |   ResourceDao.updateMapperMethods()                                            |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 10  | ViewLinker             | view          | ViewLinker.run()                                                               | 메소드 → JSP (RENDERS).
|     |                        |               |   ProjectRules.viewCalls() - 규칙마다                                            | 화면 연결은 입구가 다 만들어진 뒤에 한다.
|     |                        |               |   SemanticDao.insertViewArgumentRelations() - 호출 인자가 따옴표 리터럴이고           |
|     |                        |               |       prefix+이름+suffix로 JSP가 하나만 맞으면 RENDERS (SQL 한 번)                  |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 11  | JspLinker              |               | JspLinker.run()                                                                | JSP → 메소드 (REQUESTS), JSP → JSP (INCLUDES), JSP 화면명.
|     |                        |               |   SemanticDao.updateJspEndpointMethods() - JSP 입구에 _jspService 메소드 채우기     |
|     |                        |               |   SemanticDao.selectEndpointHandlers() - 주소 → 메소드 표                          |
|     |                        |               |   SemanticDao.selectFilePage("JSP") - 200건씩, 파일마다                           |
|     |                        |               |     saveScreenName() - ScreenTitle.of() → SemanticDao.insertScreenName()       |
|     |                        |               |     linkRequests() - 본문의 경로 글자가 입구 주소와 같으면 REQUESTS (MEDIUM)              |
|     |                        |               |     linkScriptRequests() - ScriptSources.find()로 <script src>를 찾고            |
|     |                        |               |       scriptPathsOf() (selectScriptByPathEnd(), ScreenScript.findPaths())       |
|     |                        |               |       → 그 .js에 적힌 주소로 REQUESTS                                              |
|     |                        |               |     linkIncludes() - jsp:include, @include → selectJspByPathEnd() → INCLUDES   |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 12  | ScreenLinker           | screen        | ScreenLinker.run()                                                             | WebSquare·Nexacro 화면 → 메소드·화면, 거래 입구, 화면명.
|     |                        |               |   ProjectRules.screenCalls()·screenOpens() - 거래 호출 규칙, 화면 열기 규칙            |
|     |                        |               |   SemanticDao.selectFilePageByTypes(WEBSQUARE, NEXACRO) - 200건씩                 |
|     |                        |               |   (첫 페이지에서 한 번) loadHandlers() - 주소 → 메소드 표                             |
|     |                        |               |                       loadCommonFunctions() - 공통 스크립트에서 화면을 열거나            |
|     |                        |               |                         서버를 부르는 함수 (ScriptFunctions.findFunctions())         |
|     |                        |               |   파일마다                                                                       |
|     |                        |               |     saveScreenName() - ScreenTitle.of() → insertScreenName()                   |
|     |                        |               |     commonUsesOf() - 이 화면이 부르는 공통 함수                                       |
|     |                        |               |     linkPaths() - ScreenScript.findPaths() → 입구 주소와 같으면 REQUESTS             |
|     |                        |               |     linkCalls() - ScreenScript.findCalls(rules) → methodNameOf() (IdTransform)  |
|     |                        |               |       → MethodIndex.load() → REQUESTS (메소드가 없으면 UNRESOLVED)                |
|     |                        |               |     linkScreens() - findScreenRefs() + findCalls(openRules) → findScreen()     |
|     |                        |               |       → 화면 → 화면 INCLUDES                                                     |
|     |                        |               |     linkCommonScreens() - 공통 함수가 여는 화면 → INCLUDES (MEDIUM)                  |
|     |                        |               |   ScreenLinker.insertEndpoints() - 실제로 쓰인 규칙마다 거래 입구                      |
|     |                        |               |     SemanticDao.insertCallRuleEndpoints() / insertMapEndpoints()               |
|     |                        |               |       (ID 표 규칙 → MethodIndex → insertTransactionEndpoint())                   |
|     |                        |               |   ScreenLinker.saveRuleStatus() - ID를 글자로 적지 않은 호출 통계                      |
|     |                        |               |       → insertSemanticResource(SEMANTIC_STATUS, SCREEN_RULES)                  |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 13  | ScreenMenuReader       | screen > menu | ScreenMenuReader.run()                                                         | 메뉴 목록에서 화면명.
|     |                        |               |   ProjectRules.screenMenus() - 없으면 끝                                         | 화면 파일의 제목이 먼저다.
|     |                        |               |   SemanticDao.selectScreenNamedFileIds() - 이미 화면명이 있는 화면 (11·12번이 넣음)      |
|     |                        |               |   SemanticDao.selectFilePageByLanguages(JSP, XML) - 200건씩                       |
|     |                        |               |     ScreenMenu.findEntries() - 메뉴 항목 (경로, 이름)                               |
|     |                        |               |     ScreenLinker.findScreen() - 경로로 화면 파일 찾기                                |
|     |                        |               |     SemanticDao.insertScreenName() - 화면명이 아직 없는 화면만 (via MENU)              |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 14  | SourceNamePlugin       |               | SourceNamePlugin.run()                                                         | Java 소스에서 테이블·컬럼 한글명.
|     |                        |               |   SemanticDao.selectEntityTypes() → describe() → SOURCE_TABLE_NAME             | SQL에 쓰인 컬럼이 다 모인 뒤에 돈다.
|     |                        |               |   SemanticDao.selectEntityFields() → describe() → SOURCE_NAME (ENTITY)         |
|     |                        |               |   SemanticDao.selectNameCandidateFields() - SQL·DDL 컬럼과 이름이 같은 필드           |
|     |                        |               |     → describe() → SOURCE_NAME (NAME)                                          |
|     |                        |               |   describe() - JavaDoc 설명 → 줄 끝 주석 → 바로 위 주석 순으로 찾는다                  |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 15  | MethodTitlePlugin      |               | MethodTitlePlugin.run()                                                        | 메소드 기능명.
|     |                        |               |   SemanticDao.selectMethodFilePage() - 메소드가 있는 파일 200건씩                    |
|     |                        |               |     readLines() - 체크섬이 다르거나 못 읽으면 METHOD_TITLE_SKIP                       |
|     |                        |               |     SemanticDao.selectMethodLinesByFile() → MethodTitle.of(lines, lineStart)    |
|     |                        |               |     insertSemanticResource(METHOD_TITLE)                                       |
|     |                        |               |   insertSemanticResource(SEMANTIC_STATUS, METHOD_TITLE) - 건수 기록               |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 16  | ScreenDictionaryPlugin | (설정)          | ScreenDictionaryPlugin.run()                                                   | 화면 사전 CSV의 한글명.
|     |                        |               |   ScreenDictionaryReader.load() - 설정된 화면 사전 CSV (없으면 NOT_CONFIGURED)       |
|     |                        |               |   SemanticDao.selectFilePageByTypes(JSP, WEBSQUARE, NEXACRO) - 200건씩            |
|     |                        |               |     ScreenDictionaryReader.find() → insertSemanticResource(DICT_SCREEN_NAME)   |
|     |                        |               |   insertSemanticResource(SEMANTIC_STATUS, SCREEN_DICTIONARY)                   |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| 17  | TableDictionaryPlugin  | (설정)          | TableDictionaryPlugin.run()                                                    | 테이블·컬럼 사전 CSV.
|     |                        |               |   DictionaryFileReader.load() - 설정된 테이블·컬럼 사전 CSV                          |
|     |                        |               |   insertSemanticResource(DICT_TABLE_NAME) - 테이블마다                            |
|     |                        |               |   insertSemanticResource(DICT_COLUMN_NAME) - 컬럼마다                             |
|     |                        |               |   insertSemanticResource(SEMANTIC_STATUS, TABLE_DICTIONARY)                    |
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------
```

**초안에서 바꾼 점**
- **2번 `StrutsEntries`:** "하는 일" 칸에 1번 `ServletEntries`의 설명이 그대로 복사돼 있었습니다. Struts 액션 설명으로 고쳤습니다.
- **16·17번 rule 칸:** `(설정)`이라고 적었습니다. 규칙 파일이 아니라 프로젝트 설정의 사전 CSV 경로를 읽기 때문입니다. 원문서(`docs/11` 표)의 "(없음, 설정)"과 같은 뜻입니다.
- **0번 행을 새로 넣었습니다.** "rule 이외의 작업은 framework에서 처리한다"는 머리말이 가리키는 부분입니다. 시작할 때 지우는 것, `RULE_SET`을 기록하는 것, 트랜잭션을 여는 것이 모두 여기서 일어납니다.

**다시 확인할 필요가 있는 점**
- **`ResourceDao`/`SemanticDao` 메소드:** 대부분 SQL 한 번으로 끝납니다. 이런 메소드는 그 SQL이 무엇을 하는지만 짧게 적었습니다.
- **글자 폭 정렬:** 한글과 영문의 폭이 달라서 고정폭 글꼴이라도 줄이 조금씩 어긋날 수 있습니다.

이 표를 `docs/11.dstone-knowledge.md`의 "처리기와 순서" 절에 넣을 수도 있습니다. 다만 Markdown 표는 한 칸 안에서 줄을 나눌 수 없으니, 그때는 처리기마다 하위 목록으로 바꾸는 편이 낫습니다. 원하시면 그렇게 반영하겠습니다.

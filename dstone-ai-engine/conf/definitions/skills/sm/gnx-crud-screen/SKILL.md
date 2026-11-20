---
# testApp처럼 kr.co.gnx 패키지를 쓰는 프로젝트(Spring MVC + MyBatis + JSP/Kendo UI)의 관례입니다.
# 영향도·설계·개발·문서 수정 Agent가 skills.allowed로 갖고 있다가, 대상 소스가 이 모양일 때만 불러 읽습니다.
# 다른 기술의 프로젝트를 다루게 되면 그 프로젝트의 Skill을 같은 방식으로 하나 더 만듭니다(Agent 프롬프트는 고치지 않습니다).
name: gnx-crud-screen
description: 소스의 패키지가 kr.co.gnx로 시작하는 프로젝트(testApp 등)에서 화면·Controller·Service·DAO·MyBatis 매퍼를 찾거나 만들거나 고칠 때 쓴다. 층별 파일 위치, 이름 규칙, 파일 업로드, 그리드 화면의 관례.
---
기본 참고 기능은 샘플게시판이다. 새 화면을 만들 때는 이 층별 파일을 참고 파일로 삼고, 달라지는 점만 적는다.

| 층 | 위치(소스경로 기준) | 관례 |
|---|---|---|
| 화면 | src/main/webapp/WEB-INF/views/{업무}/{기능}/{화면}.jsp | Kendo UI 그리드. 공통 함수 genexon.initKendoUI_grid_inlineEdit(...) 등을 쓴다. 데이터 주소는 transport의 read/create/update/destroy에 .ajax 주소로 적는다 |
| Controller | src/main/java/kr/co/gnx/{업무}/{기능}/{이름}Controller.java | @Controller(value="이름Controller"), BaseController 상속. 화면 이동은 /{업무}/{기능}/{화면}.go → ModelAndView("/{업무}/{기능}/{화면}"), 데이터는 *.ajax → ModelAndView("jsonView") |
| Service | .../{이름}Service.java | @Service, BaseService 상속. 이름이 *Service라서 메서드 하나가 트랜잭션 하나다(context-transaction.xml) |
| DAO | .../{이름}DAO.java | @Repository(value="이름DAO"), BaseDAO 상속. getSqlSession().selectList("네임스페이스.쿼리ID", vo) |
| VO | .../{이름}VO.java | BaseVO 상속. 필드는 스네이크 표기(mb_id, search_word) |
| 매퍼 | src/main/resources/sqlmap/mapper/{업무}/{이름}-mapper.xml | namespace는 짧은 이름(예: SampleBoard). resultType="bmap". 조건은 `@kr.co.gnx.comm.util.CommUtil@isNotEmpty(값)` |

샘플게시판 파일: kr/co/gnx/sample/board/SampleBoard{Controller,Service,DAO,VO}.java, sqlmap/mapper/sample/sampleboard-mapper.xml, views/sample/board/sampleBoard.jsp.

## 자주 쓰는 공통
- 로그인 사용자: Controller에서 SessionUtil.getSessionVO(request.getSession()).getUser() → getMb_id(). 데이터는 mb_id로 나눈다.
- 그리드 목록 조회는 검색 조건을 json_string 하나로 보낸다. Controller가 JSONObject.fromObject(vo.getJson_string())로 풀어 VO로 바꾼다.
- 그리드 저장(등록/수정/삭제)은 {"models":[...]} JSON 본문(@RequestBody JSONObject)이다. Service는 models를 돌며 DAO를 부른다.
- 삭제는 실제 DELETE 대신 use_yn='N'으로 바꾸는 방식을 쓰는 테이블이 많다. 참고 기능의 쿼리를 보고 따른다.
- 파일 업로드: kr.co.gnx.system.file.FileService의 fileSave(MultipartFile, UPLOADS[, FileVO]) / imageSave(MultipartFile, UPLOADS). 저장 위치 종류는 kr.co.gnx.config.Constants.UPLOADS enum(IMAGE, UPIMAGE 등)이다. 다른 화면에서 이미 쓰는 호출(searchInFiles로 imageSave|fileSave)을 보고 따른다.
- 업로드 최대 크기는 src/main/resources/spring/context-common.xml의 multipartResolver maxUploadSize다(전체 공통). 화면마다 다른 제한은 Controller나 화면 스크립트에서 검사한다.
- 이미지 처리: kr.co.gnx.comm.util.ImageUtil. 메서드 이름과 인자는 선언을 찾아 확인하고 쓴다.
- 메뉴는 DB 테이블(tbsy_resource 등)에 등록한다. 소스가 아니므로 등록 SQL을 설계서에 적고, 실제 DB 반영은 사람이 한다.

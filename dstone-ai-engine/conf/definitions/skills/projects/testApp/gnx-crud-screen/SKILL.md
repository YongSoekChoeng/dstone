---
# 프로젝트 testApp(패키지 kr.co.gnx, Spring MVC + MyBatis + JSP/Kendo UI)의 관례입니다.
# skills/projects/{프로젝트ID}/ 아래 Skill은 그 프로젝트에만 맞는 관례입니다. Agent는 skills.allowed의 'projects/${input.project_id}/**' 한 줄로 받고,
# 설명을 보고 지금 소스에 맞는 것만 불러 읽습니다. 범용 Skill(skills/sm)과 Agent 프롬프트에는 이런 이름을 적지 않습니다.
# 2026-11-23 실행 0bc967d2: 그리드 버튼 핸들러를 링크 onclick으로 옮기며 this가 어긋남, 팝업 Controller가 json_string을 풀지 않음,
#   검증이 화면 주소로 바로 들어가 업로드를 못 함 - 세 가지를 아래 "그리드 행 클릭과 팝업", "동작 검증" 절에 적었습니다.
# 2026-11-24(실행 6d1784d8의 자동 수정 원인, 원본으로 확인): 팝업 저장을 그리드 저장 방식(@RequestBody)으로 받아 415,
#   팝업에 세션 값(mb_id, 등록자)을 채우지 않아 업로드가 세션 mb_id를 빈 값으로 덮음, selectable만 두어 체크박스 열이 없음
#   - "팝업 저장과 세션 값", "그리드 체크박스" 절에 적었습니다.
# 2026-11-28: 화면 들어가는 법과 Kendo UI 문서 안내는 항상 읽는 testapp-basics로 옮겼습니다.
# 2026-11-28(실행 5def8189): 예약 상세 팝업이 NULL 컬럼에서 ClassCastException, NumberFormatException으로 차례로 실패, DropDownList에
#   undefined 표시, 그리드 template의 this.field 오류 - "bmap 값 꺼내기", "드롭다운과 그리드 template" 절에 적었습니다(원본 BaseMap, genexon.js, member.jsp로 확인).
name: gnx-crud-screen
description: 소스의 패키지가 kr.co.gnx로 시작하는 프로젝트에서 화면·Controller·Service·DAO·MyBatis 매퍼를 찾거나 만들거나 고치거나 시험할 때 쓴다. 층별 파일 위치, 이름 규칙, 그리드 행 클릭과 팝업, 팝업 저장과 세션 값, 그리드 체크박스, 파일 업로드, bmap 조회 결과 꺼내기, 드롭다운과 그리드 template, 동작 검증의 관례.
---
기본 참고 기능은 샘플게시판이다. 새 화면을 만들 때는 이 층별 파일을 참고 파일로 삼고, 달라지는 점만 적는다.

| 층 | 위치(소스경로 기준) | 관례 |
|---|---|---|
| 화면 | src/main/webapp/WEB-INF/views/{업무}/{기능}/{화면}.jsp | Kendo UI 그리드. 공통 함수 genexon.initKendoUI_grid_inlineEdit(...) 등을 쓴다. 데이터 주소는 transport의 read/create/update/destroy에 .ajax 주소로 적는다 |
| Controller | src/main/java/kr/co/gnx/{업무}/{기능}/{이름}Controller.java | @Controller(value="이름Controller"), BaseController 상속. 화면 이동은 /{업무}/{기능}/{화면}.go → ModelAndView("/{업무}/{기능}/{화면}"), 팝업은 *.pop, 데이터는 *.ajax → ModelAndView("jsonView") |
| Service | .../{이름}Service.java | @Service, BaseService 상속. 이름이 *Service라서 메서드 하나가 트랜잭션 하나다(context-transaction.xml) |
| DAO | .../{이름}DAO.java | @Repository(value="이름DAO"), BaseDAO 상속. getSqlSession().selectList("네임스페이스.쿼리ID", vo) |
| VO | .../{이름}VO.java | BaseVO 상속. 필드는 스네이크 표기(mb_id, search_word) |
| 매퍼 | src/main/resources/sqlmap/mapper/{업무}/{이름}-mapper.xml | namespace는 짧은 이름(예: SampleBoard). resultType="bmap". 조건은 `@kr.co.gnx.comm.util.CommUtil@isNotEmpty(값)` |

샘플게시판 파일: kr/co/gnx/sample/board/SampleBoard{Controller,Service,DAO,VO}.java, sqlmap/mapper/sample/sampleboard-mapper.xml, views/sample/board/sampleBoard.jsp.
샘플게시판에는 팝업이 없다. 팝업(등록·수정·조회)이 있는 참고 기능은 회원사 관리다: views/system/member/member.jsp(목록) + memberRegi.jsp(팝업) + kr/co/gnx/system/member/MemberController.java.

## bmap 값 꺼내기

`resultType="bmap"`은 `kr.co.gnx.comm.util.BaseMap`이다. 키는 소문자이고, **값이 NULL이면 컬럼 타입과 상관없이 빈 문자열 `""`**이 들어 있다
(`put`이 null을 `""`로 바꾸고, `callSettersOnNulls=true`라 NULL 컬럼도 put한다). NULL이 아닌 값은 JDBC가 준 타입 그대로다.

- 숫자 컬럼도 `(Integer) map.get("x")`는 NULL 행에서 ClassCastException이 난다.
- `map.get("x") != null` 검사는 NULL 행을 거르지 못한다. 그 뒤 `Integer.parseInt(String.valueOf(...))`는 NumberFormatException이 난다.
- 숫자는 값을 `String.valueOf`로 바꾼 뒤 비었는지 먼저 보고 변환한다.

```java
Object cnt = detail.get("attendee_cnt");
if(cnt != null && !"".equals(String.valueOf(cnt))) vo.setAttendee_cnt(Integer.parseInt(String.valueOf(cnt)));
```

- 확인할 때는 NULL을 허용하는 컬럼이 비어 있는 행으로도 해 본다.

## 드롭다운과 그리드 template

- 공통코드 같은 서버 목록은 `<input class="kddl" ddcode="USE_YN" optionLabel="...">`로 적고 `genexon.initKendoUI_ddl`이 만든다(member.jsp). 항목 이름은 ddcode마다 `genexon.getDDLConf`에 있다.
- 화면에서 직접 만든 목록을 `kendoDropDownList`에 줄 때, `dataTextField`/`dataValueField`를 쓰면 `dataSource`는 그 이름의 속성을 가진 객체 배열이어야 한다
  (`[{text:"09:00", value:"09:00"}]`). 문자열 배열에 두 옵션을 주면 항목이 `undefined`로 보인다. 원본 화면에는 직접 만든 목록의 사용 예가 없다.
- 그리드 컬럼 `template: function(e) {...}`의 `e`가 그 행의 데이터다. `e.필드명`으로 읽고 `this.field`처럼 `this`에서 읽지 않는다(member.jsp의 ci_url 열). 빈 값은 `genexon.nvl(값, "")`로 본다.

## 그리드 행 클릭과 팝업

- 그리드 `command` 버튼의 `click` 핸들러는 Kendo가 부르며 `this`가 그리드다. 그래서 `this.dataItem($(e.target).closest("tr"))`로 행을 얻는다(member.jsp의 memberRegi).
- 컬럼 `template`에 HTML `onclick`을 직접 쓰면 `this`는 그 요소(a, img)다. 이때는 `$("#grid").data("kendoGrid").dataItem($(e.target).closest("tr"))`로 행을 얻는다.
- `genexon.PopWindowOpen({pID, pTitle, pURL, data, ...})`은 `data`를 `json_string` 하나로 묶어 팝업 주소에 붙여 보낸다.
  팝업 Controller(*.pop)는 `vo.getJson_string()`이 있으면 `JSONObject.fromObject(...)` → `JSONObject.toBean(..., 이름VO.class)`로 풀어 VO를 채운다. 참고: MemberController의 `/system/member/memberRegi.pop`.
  풀지 않으면 VO가 비어 팝업이 늘 등록모드로 열린다.

## 팝업 저장과 세션 값

저장 방식이 두 가지다. 화면과 Controller가 같은 방식이어야 한다. 섞으면 415(Unsupported Media Type)가 나거나 값이 비어 들어간다.

| 어디서 저장하나 | 화면 | Controller(*.ajax) |
|---|---|---|
| 그리드 인라인 편집 | transport create/update/destroy가 `{"models":[...]}` JSON을 보낸다 | `@RequestBody JSONObject`로 받아 models를 돈다 |
| 팝업 폼 | `$('#myForm').ajaxForm({...})` + `$('#myForm').submit()`, form은 `enctype="multipart/form-data"`(memberRegi.jsp) | `이름VO vo` 매개변수 바인딩으로 받는다(MemberController.insertMember). `@RequestBody`를 쓰지 않는다 |

- 로그인 사용자에게서 와야 하는 값(mb_id, 등록자 이름 등)은 화면 hidden 필드에 맡기지 않는다. Controller에서 `SessionUtil.getSessionVO(request.getSession()).getUser()`로 읽어 VO에 넣는다(MemberController의 목록 조회 `memberVO.setMb_id(UserSession.getMb_id())`).
  팝업을 등록모드로 열 때 화면에 보여 줄 값(등록자 등)도 팝업 Controller(*.pop)가 세션에서 채워 `mv.addObject`로 넘긴다.
- MemberController의 업로드(`infoUrl.ajax`, `deleteUrl.ajax`)는 `UserSession.setMb_id(화면이 보낸 mb_id)`로 **세션 사용자의 mb_id를 바꾼다**. 회원사 관리는 관리자가 다른 회사 정보를 고치는 화면이라 그렇게 한다.
  다른 기능에서 이 업로드를 참고할 때는 그 줄을 옮기지 않는다. 옮기면 화면이 빈 mb_id를 보냈을 때 세션 mb_id가 빈 값으로 덮이고, 그 뒤 저장한 행이 목록(WHERE mb_id=...)에서 사라진다.

## 그리드 체크박스

- 원본 화면에는 그리드 체크박스 열을 쓰는 곳이 없다. 참고할 기존 코드가 없으니 설계서에 열 정의를 글자 그대로 적는다.
- Kendo 그리드의 `selectable: "multiple"`은 행 선택만 켠다. 체크박스 열은 생기지 않는다. 체크박스가 필요하면 columns에 template 열(`<input type="checkbox" .../>`)을 넣고, 전체선택·선택삭제는 그 체크박스의 checked로 행을 모은다.

## 자주 쓰는 공통
- 로그인 사용자: Controller에서 SessionUtil.getSessionVO(request.getSession()).getUser() → getMb_id(). 데이터는 mb_id로 나눈다.
- 그리드 목록 조회도 검색 조건을 json_string 하나로 보낸다. Controller가 JSONObject.fromObject(vo.getJson_string())로 풀어 VO로 바꾼다.
- 그리드 저장(등록/수정/삭제)은 {"models":[...]} JSON 본문(@RequestBody JSONObject)이다. Service는 models를 돌며 DAO를 부른다.
- 삭제는 실제 DELETE 대신 use_yn='N'으로 바꾸는 방식을 쓰는 테이블이 많다. 참고 기능의 쿼리를 보고 따른다.
- 파일 업로드: kr.co.gnx.system.file.FileService의 fileSave(MultipartFile, UPLOADS[, FileVO]) / imageSave(MultipartFile, UPLOADS). 저장 위치 종류는 kr.co.gnx.config.Constants.UPLOADS enum(IMAGE, UPIMAGE 등)이다. 화면은 kendoUpload(saveUrl = *.ajax, autoUpload)이고, 참고는 memberRegi.jsp + MemberController.infoUrl.
- 업로드 최대 크기는 src/main/resources/spring/context-common.xml의 multipartResolver maxUploadSize다(전체 공통). 화면마다 다른 제한은 Controller나 화면 스크립트에서 검사한다.
- 이미지 처리: kr.co.gnx.comm.util.ImageUtil. 메서드 이름과 인자는 선언을 찾아 확인하고 쓴다.
- 메뉴는 DB 테이블(tbsy_resource 등)에 등록한다. 소스가 아니므로 등록 SQL을 설계서에 적고, 실제 DB 반영은 사람이 한다.

## 동작 검증

- 화면은 testapp-basics의 "화면 들어가는 법"대로 들어간다. 팝업은 메뉴 틀(genexon.body) 안에 열린다.
- 2026-11-23 실행 0bc967d2: 화면 주소(*.go)로 바로 연 팝업에서는 파일을 넣어도 업로드 요청(*.ajax)이 나가지 않았다. index.go 메뉴로 들어가 팝업의 Choose File을 누른 뒤 파일을 넣었을 때는 업로드가 됐다. 업로드가 안 되면 이 경로로 다시 해 본다.
- 목록 페이지 크기는 화면 아래의 "N건 출력"이다. 페이지 넘김을 보려면 그보다 많은 행이 있어야 한다.

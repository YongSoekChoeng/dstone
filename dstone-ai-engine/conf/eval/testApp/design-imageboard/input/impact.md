# 영향 분석서 - 이미지 게시판 개발

- **프로젝트ID:** testApp
- **소스경로:** ${param.sourceDir}
- **DB:** testApp.testApp (jdbc:mysql://localhost:3306/testApp, schema=testApp)

## 수정사항

없음 (분석서에 수정사항 없음)

## 신규기능

### 공통 현행 확인 (TASK-01 ~ 04)

요구는 신규지만, 메뉴와 테이블은 이미 DB에 존재한다. 소스에는 구현 코드가 없다.

| 구분 | 있음/없음 | 파일 경로 또는 확인한 조회 |
|------|-----------|---------------------------|
| 메뉴 "테스트 > 이미지게시판" | 있음(DB) | tbsy_resource: resource_id=TS_300, prnt=TS_000(테스트), resource_name=이미지게시판, resource_url=/sample/imageboard/imageBoard.go, sort_no=30, use_yn='Y' |
| 테이블 tbts_image_board | 있음(DB) | testApp.tbts_image_board ([TS][테스트] 이미지 게시판), PK seq. 컬럼: seq, mb_id, title, file_no, file_url, writer_nm, use_yn, in_emp_cd, in_dtm, up_emp_cd, up_dtm. 현재 데이터 0건 |
| 이미지게시판 소스 코드 | 없음 | src 전체에서 imageboard / imageBoard / image_board / ImageBoard 검색 결과 0건 (1,242개 파일) |
| tbts_image_board를 건드리는 SQL | 없음 | knowledgeTableUsage(tbts_image_board): "그 테이블을 건드리는 SQL이 없습니다" |

### TASK-01 이미지 게시물 목록화면 제공

- **참고 기능:** 샘플게시판 (테스트 메뉴의 기존 CRUD 게시판, 같은 "테스트" 메뉴 아래)
  - 화면: ${param.sourceDir}/src/main/webapp/WEB-INF/views/sample/board/sampleBoard.jsp (Kendo 그리드, 검색조건, genexon.initKendoUI_grid_inlineEdit)
  - Controller: ${param.sourceDir}/src/main/java/kr/co/gnx/sample/board/SampleBoardController.java (진입점 /sample/board/sampleBoard.go, 목록 /sample/board/getSampleBoardList.ajax)
  - Service: ${param.sourceDir}/src/main/java/kr/co/gnx/sample/board/SampleBoardService.java
  - DAO: ${param.sourceDir}/src/main/java/kr/co/gnx/sample/board/SampleBoardDAO.java
  - 쿼리: ${param.sourceDir}/src/main/resources/sqlmap/mapper/sample/sampleboard-mapper.xml (namespace SampleBoard, 제목 검색은 LIKE)
  - VO: kr.co.gnx.sample.board.SampleBoardVO (패키지 kr.co.gnx.sample.board)
- **페이징 참고:**
  - ${param.sourceDir}/src/main/java/kr/co/gnx/system/member/MemberDAO.java (25-40줄, page 있으면 Count 쿼리 + CommUtil.getPageOffset + Paging 쿼리)
  - ${param.sourceDir}/src/main/resources/sqlmap/mapper/system/member-mapper.xml (106-120줄, Comm.CountStart/CountEnd, Comm.PagingStart/PagingEnd include 사용)
  - ${param.sourceDir}/src/main/webapp/WEB-INF/views/system/member/member.jsp (342줄, Kendo 그리드 pageable: true)
- **등록할 위치:**
  - 화면: ${param.sourceDir}/src/main/webapp/WEB-INF/views/sample/imageboard/ (신규 폴더, 메뉴 URL /sample/imageboard/imageBoard.go 에 맞춰 view 경로 /sample/imageboard/imageBoard)
  - Java: ${param.sourceDir}/src/main/java/kr/co/gnx/sample/imageboard/ (신규 패키지, SampleBoard 패키지 구조를 따름)
  - 쿼리: ${param.sourceDir}/src/main/resources/sqlmap/mapper/sample/imageboard-mapper.xml (신규. mapperLocations=classpath*:/sqlmap/mapper/**/*-mapper.xml 이므로 이 위치에 만들면 자동 등록, context-sqlMap.xml 24줄)

### TASK-02 목록화면 기능버튼(전체선택, 등록, 삭제)

- **참고 기능:**
  - 팝업(등록/수정) 열기: ${param.sourceDir}/src/main/webapp/WEB-INF/views/system/member/member.jsp (29-58줄, genexon.PopWindowOpen, pURL .pop, callbackFunc, pModal)
  - 팝업 화면: ${param.sourceDir}/src/main/webapp/WEB-INF/views/system/member/memberRegi.jsp (genexon.PopWindowClose2 로 닫기)
  - 삭제(논리삭제 use_yn='N'): ${param.sourceDir}/src/main/resources/sqlmap/mapper/sample/sampleboard-mapper.xml (78-86줄, deleteSampleBoard)
- **등록할 위치:** TASK-01의 목록화면 JSP와 신규 Controller/Service/DAO/매퍼 (TASK-01과 같은 파일에 버튼·이벤트·삭제 처리 추가)

### TASK-03 목록화면 이벤트(제목 클릭, 썸네일 클릭)

- **참고 기능:**
  - 제목 클릭 → 팝업(조회모드): ${param.sourceDir}/src/main/webapp/WEB-INF/views/system/member/member.jsp (29-58줄, genexon.PopWindowOpen + data: selectedItem)
  - 이미지(파일) 열기: ${param.sourceDir}/src/main/java/kr/co/gnx/system/file/FileController.java (36-48줄, POST /files/getFile.do, FileService#getFile)
- **등록할 위치:** TASK-01의 목록화면 JSP (Kendo 그리드 컬럼 템플릿/이벤트)

### TASK-04 이미지 게시물 상세화면(등록/수정/조회)

- **참고 기능:**
  - 팝업 상세화면(폼 + 저장/수정): ${param.sourceDir}/src/main/webapp/WEB-INF/views/system/member/memberRegi.jsp (123줄 form enctype=multipart/form-data, ajaxForm)
  - 파일업로드버튼(로컬 이미지 선택, .jpg/.png 제한): ${param.sourceDir}/src/main/webapp/WEB-INF/views/system/member/memberRegi.jsp (276-322줄, kendoUpload, saveUrl /system/member/infoUrl.ajax, success 시 file_no/file_url hidden 세팅)
  - 업로드 저장 서비스: ${param.sourceDir}/src/main/java/kr/co/gnx/system/file/FileService.java (427-487줄, imageSave(MultipartFile, UPLOADS) → FileVO, FileDAO#insertFileInfo)
  - 업로드 저장/삭제 엔드포인트: ${param.sourceDir}/src/main/java/kr/co/gnx/system/member/MemberController.java (infoUrl.ajax / deleteUrl.ajax, memberRegi.jsp에서 사용)
  - 등록자 자동세팅(로그인 사용자명): ${param.sourceDir}/src/main/java/kr/co/gnx/sample/board/SampleBoardController.java (119-134줄, toModels에서 SessionUtil 세션의 emp_nm/emp_cd/mb_id 세팅)
- **등록할 위치:**
  - 팝업 화면: ${param.sourceDir}/src/main/webapp/WEB-INF/views/sample/imageboard/ (신규, 예: imageBoardRegi.jsp - 설계 단계에서 이름 결정)
  - Java: ${param.sourceDir}/src/main/java/kr/co/gnx/sample/imageboard/ (신규, TASK-01과 같은 패키지)
  - 쿼리: ${param.sourceDir}/src/main/resources/sqlmap/mapper/sample/imageboard-mapper.xml (신규, TASK-01과 같은 파일)

## 결정 필요 사항

- 메뉴와 테이블이 이미 DB에 존재한다(위 현행 확인). 소스에는 구현이 없어 신규 개발이 맞지만, 설계 시 다음을 그대로 따라야 하는지 확인 필요:
  - 메뉴 URL /sample/imageboard/imageBoard.go → 신규 화면·Controller 경로(패키지 kr.co.gnx.sample.imageboard, view /sample/imageboard/imageBoard)를 이 URL에 맞춰야 한다.
  - 테이블 tbts_image_board(컬럼 구성 위)를 그대로 사용해야 한다. 새 테이블을 만들지 않는다.
  - 대안: 메뉴·테이블을 무시하고 새로 만들 경우 DB 데이터(메뉴 1건)와 충돌하므로 비권장.
- 삭제 방식: 샘플게시판은 실제 DELETE 대신 use_yn='N' 논리삭제다. 이미지게시판도 같은 방식인지, 파일(tbsy_file_info)까지 함께 삭제할지 설계 단계에서 정할 사항.

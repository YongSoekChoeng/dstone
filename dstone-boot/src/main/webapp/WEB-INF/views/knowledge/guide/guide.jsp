<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String linkBase = requestUtil.getStrContextPath() + "/defaultLink.do?defaultLink=";
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="../common/head.jsp"></jsp:include>

<body class="ai-body">
	<div id="ai-page-wrapper">

		<jsp:include page="../common/header.jsp"></jsp:include>

		<div id="ai-main" class="kn-guide">

			<section class="ai-panel kn-toc">
				<b>차례</b><br>
				<a href="#what">1. 이 메뉴는 무엇을 하나</a>
				<a href="#how">2. 어떻게 돌아가나</a>
				<a href="#start">3. 처음부터 따라 하기</a>
				<a href="#tutorial">4. 예제로 5분 만에 익히기</a>
				<a href="#words">5. 알아 둘 말</a>
				<a href="#screens">6. 화면별 쓰는 법</a>
				<a href="#summary">7. 리비전 요약 읽는 법 (모든 항목의 뜻)</a>
				<a href="#trouble">8. 잘 안 될 때</a>
			</section>

			<section class="ai-panel" id="what">
				<h3>1. 이 메뉴는 무엇을 하나</h3>
				<p><b>한 줄로:</b> Java 프로젝트의 소스를 컴퓨터가 미리 다 읽어서 "지도"를 만들어 두고, 그 지도에 대고 물어보는 곳입니다.</p>
				<p>남이 만든(또는 오래된) 프로그램을 고쳐야 할 때 제일 먼저 하는 일은 소스를 열어 <i>"이 버튼을 누르면 어느 클래스로 가지? 거기서 뭘 부르지? 어느 테이블을 건드리지?"</i>를
					하나씩 따라가는 것입니다. 이 메뉴는 그 일을 대신 해 둡니다. 한 번 분석해 두면 아래 같은 질문에 몇 초 만에 답이 나옵니다.</p>
				<table class="document-list-table">
					<thead><tr><th>알고 싶은 것</th><th>예전에는</th><th>여기서는</th></tr></thead>
					<tbody>
						<tr><td>"주문 취소는 어디서 처리하지?"</td><td>소스에서 "취소", "cancel" 을 찾아 하나씩 열어 본다</td><td><b>검색 · 문서</b>에 그대로 적는다</td></tr>
						<tr><td><code>OrderService.cancel</code> 을 누가 부르지? 그 안에서 뭘 부르지?</td><td>IDE 의 "호출 계층"을 여러 번 눌러 따라간다</td><td><b>영향도 · 호출 관계</b>에서 메소드를 찾아 "부르는 쪽" / "불리는 쪽"</td></tr>
						<tr><td><code>TB_ORDER</code> 에 컬럼을 더하면 어느 화면을 고쳐야 하지?</td><td>SQL 파일에서 테이블 이름을 찾고 → 그 SQL 을 부르는 DAO → 서비스 → 컨트롤러 → JSP 를 손으로 거슬러 올라간다</td><td><b>영향도 · 호출 관계</b>에 테이블 이름을 적고 "분석" 한 번</td></tr>
						<tr><td>이번 배포에서 뭐가 달라졌지?</td><td>변경 파일 목록을 보고 짐작한다</td><td><b>리비전 비교</b>가 "새로 열린 주소, 없어진 메소드, 테이블을 바꿔 쓰는 SQL"을 알려 준다</td></tr>
						<tr><td>설계서에 뭐라고 돼 있었지?</td><td>문서를 찾아 연다</td><td>문서를 올려 두고 <b>검색 · 문서</b>에서 같이 찾는다</td></tr>
					</tbody>
				</table>
				<div class="kn-note"><b>이것은 답을 지어내는 AI 가 아닙니다.</b> 소스에 실제로 적힌 것을 읽어서 알아낸 <b>사실</b>만 보여 줍니다.
					확실하지 않은 것은 "신뢰도 LOW"처럼 표시하고, 알아내지 못한 것은 보여 주지 않습니다. (이 결과를 가지고 말로 풀어 답해 주는 것은 AI 메뉴의 몫입니다.)</div>
			</section>

			<section class="ai-panel" id="how">
				<h3>2. 어떻게 돌아가나</h3>
				<p>소스를 <b>실행하지 않고 읽기만</b> 합니다. 빌드할 필요도, DB 에 접속할 필요도 없습니다. 읽는 것은 Java 만이 아닙니다.</p>
<div class="kn-diagram">  소스 폴더                        분석 (한 번)                       물어보기 (필요할 때마다)
 ┌──────────────┐          ┌──────────────────────┐          ┌──────────────────────────┐
 │ Java 소스     │          │ ① 클래스 · 메소드를 읽고 │          │ 검색      "어디서 처리하지?"   │
 │ SQL 매퍼(XML) │  ─────▶  │ ② 누가 누구를 부르는지 풀고│  ─────▶  │ 호출 관계  "누가 부르지?"      │
 │ JSP 화면      │          │ ③ 주소 · SQL · 테이블 ·  │          │ 영향도    "고치면 어디가 깨지지?"│
 │ 설정 파일      │          │    화면을 한 줄로 잇는다  │          │ 비교      "뭐가 달라졌지?"     │
 └──────────────┘          └──────────────────────┘          └──────────────────────────┘
                                      │
                                      ▼  분석 결과 한 벌 = "리비전"</div>
				<p>분석이 끝나면 프로그램 전체가 아래처럼 <b>한 줄로 이어진 지도</b>가 됩니다. 어느 지점에서든 앞으로(무엇을 부르나), 뒤로(누가 부르나) 따라갈 수 있습니다.</p>
<div class="kn-diagram">  화면(JSP)  ──요청──▶  주소  ──▶  컨트롤러  ──호출──▶  서비스  ──호출──▶  DAO  ──실행──▶  SQL  ──읽기/쓰기──▶  테이블
  edit.jsp            /board/save.do   BoardSaveAction        (…)             BoardDAO          Board.insertBoard        TB_BOARD

  ◀──────────────────────────  영향도 분석은 이 줄을 오른쪽에서 왼쪽으로 거슬러 올라갑니다  ──────────────────────────
       "TB_BOARD 를 고치면"  →  그 SQL  →  그 DAO  →  …  →  그 컨트롤러  →  "/board/save.do 와 edit.jsp 가 영향을 받는다"</div>
				<p>읽는 대상: Spring / Spring Boot 프로젝트, 그리고 <b>오래된 프로그램</b>(서블릿, Struts, iBATIS, JSP 안의 Java 코드, EUC-KR 소스)도 됩니다.</p>
			</section>

			<section class="ai-panel" id="start">
				<h3>3. 처음부터 따라 하기</h3>
				<p>아래 순서대로 하면 됩니다. 1 ~ 3 은 프로젝트마다 한 번이고, 그 뒤로는 4 의 화면들을 필요할 때 씁니다.</p>
				<div class="kn-steps">
					<a class="kn-step" href="<%=linkBase%>knowledge/index">
						<span class="kn-step-no">1</span><span class="kn-step-title">프로젝트 등록</span>
						<p>"프로젝트 · 분석" 화면 아래쪽에 projectId, 이름, <b>소스 폴더</b>를 적고 "등록". 소스 폴더는 분석 서버가 읽을 수 있는 서버 쪽 경로입니다.</p>
					</a>
					<a class="kn-step" href="<%=linkBase%>knowledge/index">
						<span class="kn-step-no">2</span><span class="kn-step-title">분석 시작</span>
						<p>표에서 projectId 를 눌러 고른 뒤 "분석 시작". 진행 상황이 단계별로 보입니다. 수백 파일이면 몇 분입니다.</p>
					</a>
					<a class="kn-step" href="<%=linkBase%>knowledge/index">
						<span class="kn-step-no">3</span><span class="kn-step-title">결과 확인</span>
						<p>리비전 목록의 "요약"을 눌러 파일 · 타입 · 관계 수와 품질 지표를 봅니다. 상태가 <b>READY</b> 면 쓸 수 있습니다.</p>
					</a>
					<a class="kn-step" href="<%=linkBase%>knowledge/search/search">
						<span class="kn-step-no">4</span><span class="kn-step-title">물어보기</span>
						<p>검색, 영향도, 호출 관계, 리비전 비교 화면에서 프로젝트를 고르고 물어봅니다.</p>
					</a>
				</div>
				<div class="kn-note">바로 눌러 보고 싶으면 이미 분석해 둔 <code>struts-app</code>(게시판 예제, 파일 11개)을 고르세요. 작아서 결과를 눈으로 따라가기 좋습니다.
					이 안내의 예시도 모두 <code>struts-app</code> 기준입니다.</div>
			</section>

			<section class="ai-panel" id="tutorial">
				<h3>4. 예제로 5분 만에 익히기</h3>
				<p>이미 분석해 둔 게시판 예제 <code>struts-app</code> 으로 네 화면을 한 번씩 눌러 봅니다. 파일이 11개뿐이라 결과를 눈으로 다 따라갈 수 있습니다.
					이 예제는 게시글 목록 / 저장 / 삭제 기능이 있고, 화면(JSP) 3개 · 액션 클래스 3개 · DAO 1개 · SQL 4개 · 테이블 2개로 이루어져 있습니다.</p>

				<h4>따라 하기 ① 분석 결과가 있는지 본다 <a href="<%=linkBase%>knowledge/index">(프로젝트 · 분석 화면 열기)</a></h4>
				<ol>
					<li>프로젝트 표에서 <code>struts-app</code> 을 누릅니다.</li>
					<li>아래 "리비전" 표에 상태가 <code>READY</code> 인 줄이 보입니다. 그 줄의 "요약"을 누릅니다.</li>
				</ol>
				<div class="kn-expect">이렇게 나옵니다: 카드에 파일 11 · 타입 5 · 진입점 8 · 검색 문서 24.<br>
					"한눈에 보기"의 <b>첫 줄은 빨간색 ✖</b> 입니다: "메소드 호출 26건 가운데 57.69% 가 풀렸습니다". 나머지 줄은 초록색 ✔ 입니다.<br>
					첫 줄이 빨간 이유는 이 예제가 <b>일부러 라이브러리(jar) 없이</b> 넣어 둔 것이기 때문입니다. Struts 와 Spring 의 jar 가 없어서,
					프레임워크 쪽으로 나가는 호출을 누구인지 확인하지 못했습니다. "라이브러리가 없으면 이렇게 보인다"의 좋은 본보기입니다.
					그래도 프로젝트 <b>안</b>의 호출(액션 → DAO → SQL → 테이블)은 이어져 있어서 아래 따라 하기는 그대로 됩니다.
					실제 프로젝트는 jar 가 같이 있으면 이 숫자가 95% 를 넘습니다(이 서버의 <code>anybiz_prd</code> 는 99.96%).<br>
					(프로젝트가 목록에 없으면 "3. 처음부터 따라 하기"대로 다른 프로젝트를 등록해서 해 보세요. 숫자는 달라도 순서는 같습니다.)</div>

				<h4>따라 하기 ② 기능이 어디 있는지 찾는다 <a href="<%=linkBase%>knowledge/search/search">(검색 · 문서 화면 열기)</a></h4>
				<ol>
					<li>프로젝트에서 <code>struts-app</code> 을 고릅니다.</li>
					<li>찾을 내용에 <code>BoardDAO.deleteBoard</code> 라고 적고 "찾기". (화면 위쪽의 예시 단추를 눌러도 채워집니다.)</li>
				</ol>
				<div class="kn-expect">이렇게 나옵니다: 1위가 메소드 <code>com.sample.board.dao.BoardDAO#deleteBoard(String)</code>, 2위가 SQL <code>Board.deleteBoard</code>.
					본문 상자를 누르면 그 메소드가 "실행하는 SQL", "호출받는 곳"과 실제 소스가 보입니다.
					이번에는 이름을 모른다고 치고 <code>게시글을 삭제하는 곳</code> 이라고 적어 보세요. 게시글과 삭제에 관련된 화면과 메소드
					(<code>board/edit.jsp</code>, <code>BoardAdminAction#delete</code>, <code>board/list.jsp</code> …)가 나옵니다.
					<b>뜻으로만 찾으면 "관련 있는 것들"이 나오고, 이름을 넣으면 "바로 그것"이 맨 위에 옵니다.</b>
					그래서 처음에는 말로 찾아 후보를 보고, 거기서 본 이름으로 다시 찾거나 호출 관계 화면으로 넘어가는 식으로 씁니다.</div>

				<h4>따라 하기 ③ 테이블을 고치면 어디가 영향을 받는지 본다 <a href="<%=linkBase%>knowledge/impact/impact">(영향도 · 호출 관계 화면 열기)</a></h4>
				<ol>
					<li>프로젝트에서 <code>struts-app</code> 을 고릅니다(리비전은 알아서 골라집니다).</li>
					<li>대상은 "테이블" 그대로 두고 <code>TB_BOARD</code> 를 적은 뒤 "분석".</li>
				</ol>
				<div class="kn-expect">이렇게 나옵니다: 직접 건드리는 메소드 4 · 닿는 메소드 7 · 진입점 3 · 화면 3.<br>
					진입점 표: <code>/board/list.do</code> 와 <code>/board/save.do</code> 는 신뢰도 <span class="kn-conf-HIGH">HIGH</span>,
					<code>/board/admin.do</code> 는 <span class="kn-conf-LOW">LOW</span> (이 주소는 요청 값에 따라 실행되는 메소드가 달라져서 클래스까지만 알아냈다는 뜻).<br>
					화면 표: <code>board/edit.jsp</code>, <code>board/list.jsp</code>, <code>common/header.jsp</code>.<br>
					<b>읽는 법:</b> "TB_BOARD 의 구조를 바꾸면 이 세 주소와 세 화면을 확인해야 한다."</div>

				<h4>따라 하기 ④ 메소드 하나를 앞뒤로 따라간다 (같은 화면의 아래 칸)</h4>
				<ol>
					<li>"메소드 이름"에 <code>deleteBoard</code> 를 적고 "메소드 찾기".</li>
					<li>나온 줄에서 "불리는 쪽"을 누릅니다. 그다음 "부르는 쪽"도 눌러 봅니다.</li>
				</ol>
				<div class="kn-expect">이렇게 나옵니다: 불리는 쪽 — 거리 1 <code>EXECUTES_SQL</code> → <code>Board.deleteBoard</code>, 거리 2 <code>WRITES_TABLE</code> → <code>TB_BOARD</code>.
					("이 메소드는 Board.deleteBoard 라는 SQL 을 실행하고, 그 SQL 은 TB_BOARD 에 쓴다.")<br>
					부르는 쪽 — <code>BoardAdminAction#delete(…)</code>. ("관리자 액션의 delete 가 이 메소드를 부른다.")</div>

				<h4>따라 하기 ⑤ 두 번 분석해서 차이를 본다 <a href="<%=linkBase%>knowledge/diff/diff">(리비전 비교 화면 열기)</a></h4>
				<p>이것은 리비전이 둘 이상 있어야 합니다. <code>struts-app</code> 은 하나뿐이라 "비교할 앞 리비전이 없습니다"가 나옵니다.
					직접 해 보려면: 자기 프로젝트를 한 번 분석 → 소스를 조금 고침 → "증분 분석"을 체크하고 한 번 더 분석 → 리비전 비교 화면에서 "비교".</p>
				<div class="kn-expect">예를 들어 DAO 의 메소드 이름 하나와 SQL 의 테이블 이름 하나를 바꿨다면: 파일 · 바뀜 2, 메소드 · 없어짐 1 + 생김 1(이름을 바꿨으므로),
					SQL statement · 바뀜 1, 테이블 사용 · 없어짐 1 + 생김 1(다른 테이블을 쓰게 됐으므로), 호출 · 없어짐 1(옛 이름을 부르던 곳이 끊어졌으므로 — 고쳐야 할 곳!).</div>
			</section>

			<section class="ai-panel" id="words">
				<h3>5. 알아 둘 말</h3>
				<table class="document-list-table">
					<thead><tr><th style="width:140px;">말</th><th>뜻</th></tr></thead>
					<tbody>
						<tr><td><b>프로젝트</b></td><td>분석할 소스 폴더 하나. 한 번 등록해 두고 여러 번 분석합니다.</td></tr>
						<tr><td><b>리비전</b></td><td>분석을 한 번 돌려서 얻은 결과 한 벌. "그 시점의 소스 전체 모습"입니다. 소스가 바뀌어 다시 분석하면 새 리비전이 생기고,
							앞의 것과 비교할 수 있습니다. 화면에서는 <code>#29 r1 (READY)</code> 처럼 번호와 라벨로 보입니다.</td></tr>
						<tr><td><b>증분 분석</b></td><td>앞 리비전과 내용이 같은 파일은 다시 분석하지 않고 결과를 옮겨 오는 방식. 결과는 전체 분석과 같고 시간만 줄어듭니다.
							두 번째 분석부터 체크하면 됩니다.</td></tr>
						<tr><td><b>진입점</b></td><td>바깥에서 프로그램으로 들어오는 입구. 웹 주소(<code>/board/list.do</code>), 스케줄 작업, <code>main</code> 메소드, JSP 등.</td></tr>
						<tr><td><b>신뢰도</b></td><td>"A 가 B 를 부른다" 같은 관계를 얼마나 확실하게 알아냈는지.
							<span class="kn-conf-HIGH">HIGH</span> 는 확실, <span class="kn-conf-MEDIUM">MEDIUM</span> 은 대체로 맞음(타입은 알고 메소드를 이름으로 고른 경우 등),
							<span class="kn-conf-LOW">LOW</span> 는 이름만 보고 짐작한 것이라 <b>참고로만</b> 봅니다.</td></tr>
						<tr><td><b>임베딩</b></td><td>"뜻으로 찾기"를 하려고 분석 결과의 글을 숫자(벡터)로 바꿔 두는 일. 분석이 끝난 뒤에도 뒤에서 계속 진행되고,
							큰 프로젝트는 한두 시간 걸립니다. 끝나기 전에도 <b>이름으로 찾는 검색과 영향도 · 호출 관계는 바로 됩니다</b>.</td></tr>
						<tr><td><b>methodId</b></td><td>메소드 하나를 가리키는 고유한 값(긴 영문 · 숫자). 직접 외울 필요 없이, 메소드 찾기나 검색 결과에서 단추로 이어서 씁니다.</td></tr>
					</tbody>
				</table>
			</section>

			<section class="ai-panel" id="screens">
				<h3>6. 화면별 쓰는 법</h3>

				<h4>① 프로젝트 · 분석</h4>
				<ul>
					<li><b>프로젝트 등록</b>: projectId 는 영문으로 짧게(나중에 바꿀 수 없음), 소스 폴더는 서버의 경로. 예: <code>/app/sampleApps/anybiz_prd</code>.
						Maven 프로젝트 폴더든, 운영 서버에서 받아 온 펼친 WAR 폴더든 됩니다. 빌드할 필요가 없습니다.</li>
					<li><b>분석 시작</b>: 보통은 아무것도 적지 않고 "분석 시작"만 누릅니다.
						<ul>
							<li>리비전 라벨: 비우면 지금 시각이 라벨이 됩니다. 릴리스 이름을 붙이고 싶으면 적습니다(예: <code>2026-10-정기배포</code>).</li>
							<li>증분 분석: 두 번째 분석부터 체크. 바뀐 파일만 다시 분석합니다.</li>
							<li>이 단계부터 다시: 평소에는 쓰지 않습니다. 이미 있는 라벨을 적고, 그 리비전의 뒤쪽 단계만 다시 만들 때 씁니다.</li>
						</ul>
					</li>
					<li><b>진행 상황</b>: 단계가 SCAN(파일 훑기) → DECLARE(선언 읽기) → RESOURCE(SQL · 설정 읽기) → RESOLVE(호출이 누구를 가리키는지 풀기)
						→ LINK → SEMANTIC(주소 · SQL · 화면 잇기) → DOCUMENT(검색용 글 만들기) 순으로 지나갑니다. 가장 오래 걸리는 것은 RESOLVE 입니다.
						Job 상태가 <code>DONE</code> 이면 끝, <code>DONE_WITH_WARNING</code> 은 일부 파일을 읽지 못했지만 나머지는 끝난 것입니다(아래 오류 표 참고).</li>
					<li><b>리비전의 "요약"</b>: 분석이 잘 됐는지 보는 곳입니다. 맨 위 "한눈에 보기"만 봐도 됩니다.
						모든 항목의 정확한 뜻은 <a href="#summary">7. 리비전 요약 읽는 법</a>에 있습니다. 표에서 먼저 볼 것 세 가지:
						<ul>
							<li>"품질 지표" — 호출 가운데 몇 %가 풀렸는지. 낮으면 라이브러리(jar)가 없어서일 수 있습니다.</li>
							<li>"관계 (종류 · 신뢰도별)" — LOW 가 많으면 결과를 조심해서 봅니다.</li>
							<li>"임베딩 진행" — PENDING 이 남아 있으면 뜻으로 찾는 검색은 아직 일부만 됩니다.</li>
						</ul>
					</li>
					<li><b>삭제 / 보관 정책</b>: 리비전을 지우면 그 분석 결과가 모두 없어집니다. 분석할 때마다 오래된 리비전은 자동으로 정리되므로(기본 최근 5개) 보통은 손댈 일이 없습니다.</li>
				</ul>

				<h4>② 검색 · 문서</h4>
				<ul>
					<li>프로젝트를 고르고 <b>평소 말하듯</b> 적으면 됩니다. 클래스 · 메소드 · 테이블 이름이나 주소를 알면 같이 적으세요. 이름이 정확히 맞는 것이 위로 올라옵니다.
						<table class="document-list-table">
							<thead><tr><th>이렇게 물으면</th><th>이런 것이 나온다</th></tr></thead>
							<tbody>
								<tr><td><code>게시글을 삭제하는 곳</code></td><td>게시글 · 삭제와 관련된 화면과 메소드 여럿 (뜻으로 찾음. 후보를 넓게 보여 준다)</td></tr>
								<tr><td><code>BoardDAO.deleteBoard</code></td><td>바로 그 메소드가 1위 (이름으로 찾음)</td></tr>
								<tr><td><code>TB_BOARD 를 고치는 SQL</code></td><td>그 테이블을 쓰는 SQL 과 메소드</td></tr>
								<tr><td><code>/board/save.do 요청을 받는 곳</code></td><td>그 주소를 처리하는 클래스와 메소드</td></tr>
							</tbody>
						</table>
					</li>
					<li><b>결과 읽는 법</b>: 한 건이 "메소드 하나", "SQL 하나", "화면 하나" 입니다. 회색 줄에 파일과 줄 번호, 그리고
						<code>나온 곳 VECTOR+KEYWORD</code>(뜻과 이름 양쪽에서 찾힘 — 가장 믿을 만함), <code>유사도</code>(0 ~ 1, 클수록 뜻이 가까움),
						<code>이름 점수</code>(클수록 이름이 정확히 맞음)가 나옵니다. 본문 상자를 누르면 전체가 펴집니다.
						본문 앞부분의 "진입점 / 호출하는 것 / 실행하는 SQL / 호출받는 곳"은 분석이 알아낸 사실이고, 그 아래가 실제 소스입니다.</li>
					<li><b>옵션</b>: "방법"은 그대로(뜻 + 이름) 두면 됩니다. "종류"를 체크하면 그 종류만 나옵니다(예: SQL 만). 아무것도 체크하지 않으면 전부입니다.</li>
					<li><b>문서 올리기</b>: 파일을 고르고 "올리기". 그다음 위 검색에서 "올린 문서"를 체크하면 같이 찾습니다.
						프로젝트를 고르지 않은 채로 검색하면 올린 문서에서만 찾습니다. 스캔한(글자가 그림인) PDF 는 읽지 못합니다.</li>
				</ul>

				<h4>③ 영향도 · 호출 관계</h4>
				<ul>
					<li><b>영향도 분석</b> — "이것을 고치면 어디까지 닿는가". 대상을 고르고 이름을 적은 뒤 "분석".
						<table class="document-list-table">
							<thead><tr><th style="width:150px;">대상</th><th>적는 것</th><th>예</th></tr></thead>
							<tbody>
								<tr><td>테이블</td><td>테이블 이름 (대소문자 상관없음)</td><td><code>TB_BOARD</code></td></tr>
								<tr><td>SQL statement</td><td>매퍼의 <code>네임스페이스.id</code></td><td><code>Board.deleteBoard</code></td></tr>
								<tr><td>타입</td><td>패키지까지 포함한 클래스 이름</td><td><code>com.sample.board.dao.BoardDAO</code></td></tr>
								<tr><td>메소드</td><td>methodId — 직접 적지 말고 아래 "메소드 찾기"의 "영향도" 단추를 누릅니다</td><td></td></tr>
							</tbody>
						</table>
						결과는 세 표입니다. <b>진입점</b>(이 변경을 사용자가 겪게 되는 주소), <b>화면</b>(그 주소를 부르거나 그 결과를 보여 주는 JSP),
						<b>메소드</b>(중간에 거치는 메소드 전부). "거리"는 대상에서 몇 단계 떨어졌는지이고, 0 은 대상을 직접 건드리는 메소드입니다.
						테이블이면 "쓰기만"을 골라 그 테이블을 <b>고치는</b> 기능만 볼 수 있습니다.</li>
					<li><b>호출 관계</b> — 메소드 이름을 적고 "메소드 찾기" → 나온 줄에서
						<ul>
							<li>"부르는 쪽": 이 메소드를 누가 부르는지 거슬러 올라갑니다(컨트롤러, 그 주소를 부르는 화면까지).</li>
							<li>"불리는 쪽": 이 메소드가 무엇을 부르는지 따라 내려갑니다. <b>실행하는 SQL 과 그 SQL 이 읽고 쓰는 테이블, 여는 화면</b>까지 나옵니다.</li>
							<li>"영향도": 이 메소드를 대상으로 위의 영향도 분석을 돌립니다.</li>
						</ul>
						"따라갈 깊이"를 늘리면 더 멀리까지 따라갑니다(최대 5).</li>
					<li><b>테이블 이름을 모를 때</b>: "테이블 이름의 일부"를 적고 "테이블 찾기" → 나온 이름을 누르면 영향도 분석으로 이어집니다. 비워 두고 누르면 전체 목록입니다.</li>
				</ul>

				<h4>④ 리비전 비교</h4>
				<ul>
					<li>같은 프로젝트를 두 번 이상 분석했을 때 씁니다. 프로젝트와 (새) 리비전을 고르고 "비교". 기준은 비워 두면 바로 앞 리비전입니다.</li>
					<li>소스의 줄 단위 차이가 아니라 <b>구조의 차이</b>가 나옵니다: 생기거나 없어진 파일 · 타입 · 메소드 · 진입점,
						내용이 바뀐 SQL, 테이블을 새로 쓰거나 더는 쓰지 않게 된 SQL, 새로 생기거나 없어진 호출.</li>
					<li>배포 전에 "이번에 주소가 새로 열렸나", "테이블을 새로 건드리게 된 SQL 이 있나"를 확인하는 데 좋습니다.</li>
					<li>메소드 이름을 바꾸면 "없어짐 + 생김" 두 줄로 나옵니다. 메소드 안의 내용만 고친 것은 "파일 · 바뀜"으로만 보이고,
						그 결과 달라진 호출이나 테이블 사용이 있으면 그쪽에 나타납니다.</li>
				</ul>
			</section>

			<section class="ai-panel" id="summary">
				<h3>7. 리비전 요약 읽는 법 (모든 항목의 뜻)</h3>
				<p>"프로젝트 · 분석" 화면에서 리비전의 <b>"요약"</b>을 누르면 나오는 화면입니다. 분석이 <b>얼마나 만들어졌고, 얼마나 믿을 만한지</b>를 보여 줍니다.
					항목이 많지만 다 읽을 필요는 없습니다. 아래 순서로 보면 됩니다.</p>
				<ol>
					<li><b>"한눈에 보기"만 본다.</b> 숫자를 말로 풀어 둔 것입니다. 초록색 ✔ 만 있으면 그대로 쓰면 됩니다.
						주황색 <b>!</b> 나 빨간색 <b>✖</b> 가 있으면 그 줄에 무엇을 하면 되는지 적혀 있습니다.</li>
					<li><b>더 알고 싶으면 "품질 지표"와 "관계" 표를 본다.</b> 호출이 몇 %나 풀렸는지, 짐작으로 이은 것이 얼마나 되는지입니다.</li>
					<li><b>나머지 표는 "무엇이 얼마나 들어 있나"를 확인할 때 본다.</b> 예: "SQL 이 다 잡혔나"는 'Java 밖의 자원', "주소가 다 잡혔나"는 '진입점'.</li>
				</ol>
				<p>화면에서는 표의 영문 값 옆에 뜻이 붙어 있고, 표마다 "이 표 읽는 법"을 누르면 그 표의 설명이 나옵니다. 아래는 그 설명을 한곳에 모은 것입니다.</p>

				<h4>맨 위의 숫자 카드</h4>
				<div id="kn-guide-cards"></div>

				<h4>한눈에 보기 (자동 판정)</h4>
				<table class="document-list-table">
					<thead><tr><th style="width:260px;">줄</th><th>무엇을 보고 판정하나</th><th>✔ 가 아니면</th></tr></thead>
					<tbody>
						<tr><td>메소드 호출 … % 가 풀렸습니다</td><td>품질 지표의 <code>callResolutionRate</code>. 95 이상 ✔, 80 이상 !, 그 아래 ✖ (대략의 눈금)</td>
							<td>호출 관계 · 영향도에 빠진 것이 있을 수 있습니다. 프로젝트 등록 칸에 라이브러리(jar) 위치를 적고 다시 분석합니다.</td></tr>
						<tr><td>Java 파일을 모두 읽었습니다</td><td>품질 지표의 <code>parseSuccessRate</code> 가 100 인지</td>
							<td>읽지 못한 파일의 메소드는 결과에 없습니다. 어느 파일인지는 분석 Job 의 오류 표에 나옵니다.</td></tr>
						<tr><td>진입점 … 개를 찾았습니다</td><td>'진입점' 표의 합이 1 이상인지</td>
							<td>주소를 알아내지 못해 영향도 분석의 진입점 표가 비어 나옵니다.</td></tr>
						<tr><td>메소드와 SQL 이 … 건 이어졌습니다</td><td>'관계' 표의 <code>EXECUTES_SQL</code> (UNRESOLVED 제외) 건수</td>
							<td>매퍼 XML 을 쓰지 않는 프로그램이면 정상입니다. 테이블에서 출발하는 영향도 분석만 못 합니다.</td></tr>
						<tr><td>임베딩이 … % 진행됐습니다</td><td>'임베딩 진행' 표의 DONE ÷ 전체</td>
							<td>기다리면 끝납니다. 그동안 뜻으로 찾는 검색만 일부만 되고 나머지는 다 됩니다.</td></tr>
					</tbody>
				</table>

				<div id="kn-guide-sections"></div>
			</section>

			<section class="ai-panel" id="trouble">
				<h3>8. 잘 안 될 때</h3>
				<table class="document-list-table">
					<thead><tr><th style="width:300px;">이럴 때</th><th>이렇게</th></tr></thead>
					<tbody>
						<tr><td>프로젝트 옆 상태가 <code>DOWN</code> 이거나 "dstone-knowledge를 부르지 못했습니다"</td>
							<td>분석 서버(dstone-knowledge, 포트 4081)가 내려가 있습니다. 서버에서 <code>dstone-knowledge/bin/startApp.sh</code> 로 띄웁니다.</td></tr>
						<tr><td>화면에서 아무 반응이 없거나 목록이 비어 나온다</td>
							<td>로그인이 풀렸을 수 있습니다. 다시 로그인한 뒤 화면을 새로 엽니다.</td></tr>
						<tr><td>등록할 때 "소스 경로가 없거나 폴더가 아닙니다"</td>
							<td>소스 폴더는 내 PC 가 아니라 <b>분석 서버</b>에 있는 경로여야 합니다.</td></tr>
						<tr><td>"이 프로젝트는 이미 분석이 돌고 있습니다"</td>
							<td>한 프로젝트에 분석은 하나씩만 돕니다. 끝나기를 기다리거나 "취소"를 누릅니다.</td></tr>
						<tr><td>검색 결과가 적거나 엉뚱하다</td>
							<td>리비전 "요약"의 "임베딩 진행"에 PENDING 이 남았는지 봅니다. 남아 있으면 뜻으로 찾기는 아직 일부만 됩니다.
								그동안은 이름(클래스 · 메소드 · 테이블)을 질문에 넣어 찾으세요.</td></tr>
						<tr><td>검색이 수 초 넘게 걸린다</td>
							<td>임베딩이 뒤에서 도는 동안에는 질문 처리도 같이 느려집니다. 임베딩이 끝나면 빨라집니다.</td></tr>
						<tr><td>영향도 분석에서 "그 테이블을 건드리는 SQL을 실행하는 메소드가 없습니다"</td>
							<td>테이블 이름을 "테이블 찾기"로 확인합니다. SQL 이 매퍼 XML 이 아니라 Java 코드 안에 문자열로 적혀 있는 프로그램은 테이블과 이어지지 않습니다.
								그럴 때는 검색에서 테이블 이름으로 찾습니다.</td></tr>
						<tr><td>부르는 쪽이 하나도 안 나온다</td>
							<td>진입점이라서 정말 부르는 곳이 없거나, 리플렉션 · 문자열로 만든 주소처럼 분석이 잇지 못하는 방식으로 불리는 것입니다.</td></tr>
						<tr><td>결과에 LOW 가 많다 / 풀지 못한 참조가 많다</td>
							<td>분석 대상의 라이브러리(jar)를 찾지 못해서일 수 있습니다. 소스 폴더 안에 jar 가 없으면 "프로젝트 · 분석" 화면의 등록 칸에 라이브러리 jar 의 위치를 적고 다시 등록한 뒤, 새로 분석합니다.</td></tr>
						<tr><td>리비전 비교에서 "비교할 앞 리비전이 없습니다"</td>
							<td>그 프로젝트를 한 번만 분석한 것입니다. 소스가 바뀐 뒤 한 번 더 분석하면 비교할 수 있습니다.</td></tr>
					</tbody>
				</table>
				<p class="ai-hint">분석이 어떻게 이루어지는지, 무엇을 잇지 못하는지의 자세한 내용은 저장소의 <code>docs/11.dstone-knowledge.md</code> 에 있습니다.</p>
			</section>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-terms.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/guide.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeGuide.init();
	</script>
</body>
</html>

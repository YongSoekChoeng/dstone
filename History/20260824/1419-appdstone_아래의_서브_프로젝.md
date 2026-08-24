# appdstone_아래의_서브_프로젝

> 생성일시: 2026-08-24 14:19

## 질문

/app/dstone 아래의 서브 프로젝트 dstone-common 의 불필요한 자원들을 모두 삭제하고 싶어.
dstone-common 는 dstone-batch, dstone-boot 의 공통모듈로 사용되는 건데 web자원도 있는 것 같아.
큰 문제가 없다면 /app/dstone/dstone-common/src/main/webapp 를 삭제하고 싶은데 문제가 없을 지 확인해줘.

---

## 답변

dstone-common의 packaging 타입과 webapp 디렉토리 사용 여부를 확인하겠습니다.

---

## 답변

확인 결과, **`dstone-common/src/main/webapp` 삭제해도 안전합니다.**

**확인한 내용:**

1. **packaging 문제 없음** — `dstone-common/pom.xml`에 `<packaging>` 태그가 없어 기본값인 `jar`로 빌드됩니다. war가 아니므로 `src/main/webapp`은 빌드 산출물에 원천적으로 포함되지 않습니다.

2. **pom.xml의 `<resources>` 설정도 webapp을 포함하지 않음** — 명시적으로 `conf/*.properties`, `src/main/java/**/*.xml`, `src/main/resources`만 리소스로 지정되어 있고, `src/main/webapp`은 목록에 없습니다.

3. **다른 모듈에서 참조 없음** — `dstone-boot`, `dstone-batch`의 pom.xml(maven-war-plugin 포함)이나 소스 코드 어디에도 `dstone-common/src/main/webapp`을 참조하는 곳이 없습니다. `RequestUtil.java`, `InitServlet.java`의 `getRealPath()` 호출은 런타임 서블릿 컨텍스트 기반이라 dstone-common의 webapp 디렉토리와 무관합니다.

4. **실제 웹 자원은 dstone-boot에 이미 별도로 존재** — `dstone-boot/src/main/webapp`에 `WEB-INF/lib`, `WEB-INF/views` 등을 포함한 완전한 웹 리소스가 이미 있고, 이게 실제 WAR 빌드에 쓰이는 것입니다. `dstone-common`의 webapp은 파일 구조가 거의 동일한 **오래된 중복 복사본**(일부 jsp 파일만 내용이 다름 — analyzation.jsp, optionsetting.jsp, login.jsp, main.jsp, test.jsp)으로 보이며, 개발 과정에서 남은 잔재로 판단됩니다.

5. 크기는 534개 파일, 약 22MB입니다.

삭제 진행할까요?

---

## 질문

응 삭제 진행해줘

---

## 답변

mvn 명령이 이 환경에 설치되어 있지 않아 빌드 검증은 생략합니다. (분석상 webapp은 jar 빌드 산출물에 포함되지 않았으므로 컴파일/빌드에 영향 없습니다.)

삭제 완료했습니다. `dstone-common/src/main/webapp` 하위 534개 파일이 git에서 삭제 상태(D)로 표시되고 있습니다. 아직 커밋은 하지 않았으니, 확인 후 커밋 원하시면 말씀해주세요.

---

## 질문

커밋 해줘

---

## 답변

커밋 완료했습니다 (`edad0ce`): 534개 파일, 155,497줄 삭제. 작업 트리는 깨끗한 상태입니다.

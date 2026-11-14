# localcommandcaveatTh

> 생성일시: 2026-11-15 07:02

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="7cf2">
dstone-boot>knowledge 의 프로젝트 분석 에서 아래의 내용을 확인해줘.
풀지 못한 참조 살펴보기 > 한 건씩 테이블에서 
파일:줄, 종류, 참조, 들어 있는 메소드, ... 이 순서를
파일:줄, 종류, 들어 있는 메소드, 참조, ... 이 순서로 바꿔줘.
</pasted_content id="7cf2">

---

## 답변

Let me check whether any docs describe this column order.

---

## 답변

풀지 못한 참조 화면의 "한 건씩" 테이블에서 열 순서를 바꿨습니다. 이제 **파일:줄 → 종류 → 들어 있는 메소드 → 참조 → 원인 → 이유** 순서로 나옵니다.

- 수정한 파일: `dstone-boot/src/main/webapp/knowledge/assets/js/unresolved.js:139`. `K.table` 열 정의에서 `fromMethod`를 `call` 앞으로 옮겼고, 다른 코드는 건드리지 않았습니다.
- 이 테이블의 열 순서를 적어 둔 문서가 없어서 문서는 고치지 않았습니다.
- 브라우저 화면에서는 아직 확인하지 않았습니다. 지금 떠 있는 `dstone-boot`(kind Pod)에 반영하려면 다시 빌드하고 배포해야 합니다.

---

## 질문

http://localhost:7081/defaultLink.do?defaultLink=ai/workflow/workflow 오류가 나는데?

---

## 답변

7081은 kind Pod가 아니라 WSL의 java 프로세스(pid 2509)입니다. 로그를 확인하겠습니다.

---

## 답변

원인을 찾았습니다. 실행 중인 WAR가 06:57에 다시 빌드되어 클래스를 못 읽는 상태입니다. 확인 후 재기동하겠습니다.

---

## 답변

종료 중에도 클래스를 못 읽어 멈춘 것으로 보입니다. 스크립트 안내대로 강제 종료 후 기동합니다.

---

## 답변

다시 띄운 뒤로는 그 주소가 오류 없이 열립니다.

**원인:** 7081은 kind Pod가 아니라 WSL에서 `bin/startApp.sh`로 띄운 `dstone-boot`(06:19 기동)였습니다. 그런데 06:57에 `target/dstone-boot.war`가 다시 빌드되면서 실행 중인 파일이 바뀌었습니다. 그 뒤로는 클래스를 읽지 못해(`NoClassDefFoundError`) 06:58부터 화면 요청이 500 오류로 끝났습니다. 로그를 남기는 코드도 클래스를 못 읽어서, 원래 예외는 로그에 남지 않았습니다.

**조치:**
1. `stopApp.sh`로 내렸지만 종료 도중에도 클래스를 못 읽어 30초 안에 내려가지 않았습니다. 그래서 스크립트 안내대로 `kill -9`로 끝냈습니다.
2. `startApp.sh`로 다시 띄웠습니다(새 PID 6732).
3. `defaultLink.do?defaultLink=ai/workflow/workflow`에 다시 요청하니 200이 오고, 재기동 뒤 로그에도 오류가 없습니다.

제가 확인한 것은 로그인하지 않은 상태의 `curl` 응답입니다. 로그인 세션은 재기동 전에 맺어진 것이라, 브라우저에서 다시 로그인해야 할 수도 있습니다. 앞 턴에서 바꾼 열 순서도 이번 재기동으로 반영됐을 것입니다.

같은 일이 다시 생기지 않게 하려면, 앱이 떠 있는 동안 `mvn package`를 돌리지 말고 먼저 `stopApp.sh`로 내린 뒤 빌드하면 됩니다.

# pasted_content_id747

> 생성일시: 2026-10-10 11:36

## 질문

<pasted_content id="7472">
지금 로컬PC(Windows)에서 dstone-boot 에 있는 dstone-ai 메뉴를 클릭하면 아래와 같은 에러가 나는데 원인 찾아줘.

||===================================== [net.dstone.boot.ai.controller.admin.WorkFlowAdminController] START ======================================||
2026-10-10 11:03:28  INFO [net.dstone.boot.common.config.ConfigCallLog] +->[CONTROLLER] {WorkFlowAdminController.detail(executionId=[5db703bd-fdf1-4675-a721-5e0e6f03d350])}
2026-10-10 11:03:28  INFO [net.dstone.boot.common.config.ConfigCallLog] +--->[SERVICE ] {WorkFlowAdminService.detail(executionId=[5db703bd-fdf1-4675-a721-5e0e6f03d350])}
2026-10-10 11:03:28  INFO [net.dstone.common.exception.resolver.DsExceptionResolver] net.dstone.common.exception.resolver.DsExceptionResolver.doResolveException() has been called !!!
org.springframework.web.reactive.function.client.WebClientResponseException: 200 OK from GET http://localhost:8081/api/ai/workflow/executions/5db703bd-fdf1-4675-a721-5e0e6f03d350, but response failed with cause: org.springframework.core.io.buffer.DataBufferLimitException: Exceeded limit on max bytes to buffer : 262144
    at org.springframework.web.reactive.function.client.WebClientResponseException.create(WebClientResponseException.java:324)
    at org.springframework.web.reactive.function.client.DefaultClientResponse.lambda$createException$1(DefaultClientResponse.java:214)
    at reactor.core.publisher.FluxMap$MapSubscriber.onNext(FluxMap.java:106)
    at reactor.core.publisher.FluxOnErrorReturn$ReturnSubscriber.onError(FluxOnErrorReturn.java:196)
    at reactor.core.publisher.FluxDefaultIfEmpty$DefaultIfEmptySubscriber.onError(FluxDefaultIfEmpty.java:156)
    at reactor.core.publisher.FluxOnAssembly$OnAssemblySubscriber.onError(FluxOnAssembly.java:545)
    at reactor.core.publisher.FluxMapFuseable$MapFuseableSubscriber.onError(FluxMapFuseable.java:143)
    at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onError(FluxContextWrite.java:123)
    at reactor.core.publisher.FluxMapFuseable$MapFuseableConditionalSubscriber.onError(FluxMapFuseable.java:341)
...
</pasted_content id="7472">

---

## 답변

원인은 `dstone-boot`가 `dstone-ai-engine`의 실행 상세 응답을 받다가 WebClient 기본 버퍼 한도(256KB = 262,144바이트)를 넘긴 것입니다. 엔진은 200 OK로 정상 응답했고, 받는 쪽에서 터졌습니다. Windows 쪽 엔진이 WSL에서 보이지 않아 실제 응답 크기는 재지 못했고, 코드와 에러 메시지로 판단했습니다.

## 왜 넘쳤나

- **받는 쪽**: `WorkFlowAdminService.detail()`(`dstone-boot/.../ai/service/admin/WorkFlowAdminService.java:44`)이 한도를 늘리지 않은 `getWebClient()`를 그대로 씁니다.
- **보내는 쪽**: `GET /api/ai/workflow/executions/{id}`는 실행 컨텍스트 트리(`context`)와 스텝 이력(`history`)을 통째로 내려줍니다(`WorkFlowExecutionDetail.java:31`). `context.state`에는 스텝이 저장한 값이 전부 들어 있습니다.
- **커진 이유(추정)**: 해당 실행이 `pilot-workflow` 신규개발 경로라면 `state.sources`, `state.designTargets` 같은 소스 파일 본문이 state에 그대로 쌓입니다. `output.value`에서는 이 값들을 빼지만 상세 API의 `context`에는 남습니다.

메뉴 클릭만으로 에러가 나는 것은 화면이 열리면서 이 실행(`5db703bd-…`)의 상세를 조회하기 때문으로 보입니다. 목록 API는 요약만 받으므로 문제가 없습니다.

## 고치는 방법

1. **`dstone-boot`에서 한도 늘리기**: `KnowledgeProxyService.client()`(`:117`)가 노드 맵 때문에 이미 32MB로 늘려 쓰고 있으니, `WorkFlowAdminService`의 `detail()`/`decide()`에도 같은 방식을 적용합니다. `WorkFlowTestService.java:86`의 상태 폴링도 같은 URL을 부르므로 함께 고쳐야 합니다.
2. **엔진 응답 줄이기**: 상세 API가 `context.state`의 큰 값을 잘라서 주거나 요청할 때만 주도록 바꿉니다. 화면 응답도 가벼워지지만, 디버깅용으로 state를 그대로 보여주는 지금 동작이 달라집니다.

1번만으로 에러는 사라지므로 1번을 먼저 적용하는 것을 권합니다. 수정은 아직 하지 않았으니, 원하시면 진행하겠습니다.

---

## 질문

둘 다 적용해줘.

---

## 답변

두 가지 모두 `/app/dstone`에 적용했습니다. 다만 실제 서버를 띄워 화면으로 확인하지는 못했고(Windows 쪽 엔진이 WSL에서 보이지 않음), 컴파일과 단위 테스트까지만 확인했습니다.

## 바꾼 내용

**1. `dstone-boot` — 응답 한도 늘리기**
- `BaseService`에 `getLargeResponseWebClient()`(한도 32MB)를 추가했습니다.
- `WorkFlowAdminService`의 `detail()`/`decide()`와 `WorkFlowTestService.status()`가 이 클라이언트를 씁니다.

**2. `dstone-ai-engine` — 응답 줄이기**
- 실행 상세 API와 승인 결정 응답의 `context`에서 2,000자를 넘는 글자는 앞부분만 담고 "전체 N자 중 앞 2000자만…" 안내를 붙입니다(`api.service.ContextShortener`).
- 응답용 복사본만 줄이므로 DB에 저장된 값과 실행 재개에는 영향이 없습니다.
- 전체가 필요하면 `GET /api/ai/workflow/executions/{id}?full=true`로 조회합니다.
- 한도는 `conf/application.yml`의 `dstone.ai.workflow.detail.max-value-chars`로 바꿀 수 있습니다(0이면 줄이지 않음).

문서는 `docs/09.dstone-ai-engine.md`(API 예시, 설정 표, 6.2절)와 `docs/06.dstone-boot.md`(10.5절)에 반영했습니다.

## 알아둘 점

- **관리자 화면의 context 칸**: 이제 긴 값이 잘려 보입니다. 화면에 "전체 보기"는 넣지 않았습니다.
- **테스트**: `dstone-boot`는 컴파일만 통과했습니다. 엔진은 `mvn clean test`에서 66개 중 60개 통과, 새로 넣은 `ContextShortenerTest` 2개는 통과했습니다.
- **기존에 깨져 있던 테스트**: 실패한 6개는 모두 `StepToolsTest`이고 이번 수정과 무관합니다. `FileSetTool`의 필드가 `fileTool`로 바뀌었는데 테스트가 아직 `fileUtil`을 찾습니다. 손대지 않았습니다.
- **Windows 반영**: 로컬 PC는 D: 드라이브의 별도 클론이므로, 이 변경을 가져가서 `dstone-common` → `dstone-ai-engine`/`dstone-boot` 순으로 다시 빌드하고 두 서버를 재기동해야 합니다. 커밋은 하지 않았습니다.

---

## 질문

기억해. 너는 /app 아래에 있는 소스만 작업해. 그리고 git에 올리면 내가 windows 쪽에서 내려받고 확인하는 절차로 갈꺼야.

---

## 답변

기억해 두었습니다. 앞으로 소스 작업은 `/app` 아래에서만 하고, git에 올린 뒤 Windows에서 내려받아 확인하시는 절차로 진행하겠습니다.

한 가지만 확인하겠습니다. "git에 올리면"이 제가 작업을 마칠 때마다 직접 커밋·푸시까지 하라는 뜻인지는 분명하지 않아서, 지금은 올리기 전에 한 번 묻는 것으로 기억해 두었습니다. 매번 묻지 않고 올려도 되면 말씀해 주세요.

이번 수정분(응답 한도 확대 + 엔진 응답 줄이기)은 아직 커밋하지 않은 상태입니다. 지금 `main`에 커밋하고 푸시할까요? 작업 폴더에는 제가 만들지 않은 변경 두 건(`docs/temp/Agent_Workflow_안정화_방안.docx` 삭제, `docs/temp/dstone-workflow-design.md` 추가)도 있는데, 이 둘은 빼고 이번 수정 파일만 올리겠습니다.

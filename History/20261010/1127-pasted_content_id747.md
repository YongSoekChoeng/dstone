# pasted_content_id747

> 생성일시: 2026-10-10 11:27

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

/**
 * 토큰 사용량/비용/지연시간 로깅(Phase 4, observability). {@code ChatClient}의 {@code CallAdvisor}로
 * 붙어({@code ConfigChatClient}) 매 호출의 caller/model/promptTokens/completionTokens/totalTokens/
 * 추정비용(USD)/지연시간(ms)을 로그 한 줄로 남긴다 - 별도 DB 테이블이나 대시보드 없이 로그 기반으로
 * MVP 수준의 관측성을 확보한다. 실패(예: {@link net.dstone.ai.governance.guardrail} REJECT)도 별도로
 * 로깅한다.
 *
 * Eval(품질 평가) 결과 로깅은 아직 다루지 않는다 - 평가 데이터셋/채점 로직 등 이 모듈에 없는 전제가
 * 먼저 정해져야 하므로, 구체적인 요구가 생기면 별도 하위 패키지로 착수한다.
 */
package net.dstone.ai.observability.usage;

/**
 * Guardrail(Phase 4) - 현재는 PII(개인식별정보) 탐지/마스킹({@link net.dstone.ai.governance.guardrail.PiiGuardrailAdvisor})
 * 하나뿐이다. {@code ChatClient}의 {@code CallAdvisor} 체인에서 가장 바깥쪽(주민등록번호/전화번호/이메일/
 * 카드번호 패턴 탐지 후 mask 또는 reject)에 붙어, 민감정보가 LLM provider로 그대로 나가거나
 * {@link net.dstone.ai.session}(Redis 대화 히스토리)에 그대로 저장되는 것을 막는다.
 * enabled=false(기본값)면 이전 Phase와 동일하게 아무 검사 없이 통과한다.
 *
 * sensitive-word 차단 같은 다른 종류의 Guardrail이 필요해지면 이 패키지에 이어서 추가한다
 * (Spring AI가 기본 제공하는 {@code SafeGuardAdvisor}가 그 용도에 가깝다).
 */
package net.dstone.ai.governance.guardrail;

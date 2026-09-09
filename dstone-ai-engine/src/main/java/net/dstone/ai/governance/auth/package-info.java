/**
 * API Key 인증(Phase 4, governance 중 첫 구현). {@link net.dstone.ai.governance.auth.ApiKeyProperties}가
 * dstone.ai.governance.auth.keys(YAML 시퀀스, key/caller 쌍)를 읽어 key -> caller 매핑을 만들고,
 * {@link net.dstone.ai.governance.auth.ApiKeyAuthFilter}가 매 요청마다 헤더(기본 {@code X-API-Key})를
 * 검사해 통과시키거나 401로 막는다. 통과한 요청의 caller는
 * {@link net.dstone.ai.governance.auth.CallerContext}에 담겨, 이후 구현될 rate limit/비용 트래킹이
 * 헤더를 다시 파싱하지 않고 재사용한다.
 *
 * dstone.ai.governance.auth.enabled=false(기본값)면 이전 Phase(0~3)와 동일하게 인증 없이 전부
 * 통과한다 - RAG(dstone.ai.rag.enabled)와 동일한 옵트인 철학. OAuth2 client-credentials 대신 API Key를
 * 고른 이유는 모노레포에 Authorization Server가 없어(dstone-boot는 OAuth2 로그인의 client일 뿐 IdP가
 * 아님) client-credentials를 타려면 별도 IdP를 새로 구축해야 하기 때문이다 - SI 프로젝트 간
 * 서비스-투-서비스 호출이라는 용도에는 API Key로 충분하다.
 */
package net.dstone.ai.governance.auth;

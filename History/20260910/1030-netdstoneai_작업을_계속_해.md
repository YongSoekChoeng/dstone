# netdstoneai_작업을_계속_해

> 생성일시: 2026-09-10 10:30

## 질문

net.dstone.ai 작업을 계속 해볼까?
어디까지 진행했지?

---

## 답변

Compiles clean. Let me check the dedicated doc file and the package mismatch (`agent.tool.*` package-info files vs actual `tools.*` classes) to see if that's a known/intentional discrepancy.

---

## 답변

현재 진행 상황을 확인했습니다. `docs/09.dstone-ai-engine.md`의 로드맵 표가 실제 코드 상태와 정확히 일치합니다 (컴파일도 정상 통과).

## 현재까지 진행된 것

| Phase | 상태 |
|---|---|
| 0 — Chat API 스켈레톤 | ✅ 완료 (Anthropic 하드코딩, Jasypt ENC 키 관리) |
| 1 — Gateway/Session/Prompt | ✅ 완료 (provider 3종 추상화, Redis 대화 히스토리, 프롬프트 템플릿 버저닝) |
| 2 — RAG | ✅ 완료, 실동작 검증됨 (Tika 적재 → Ollama 임베딩 → pgvector 검색, `ragEnabled`) |
| 3 — Agent/Tool | ✅ 완료, 실동작 검증됨 (`@AiTool` 등록, `DateTimeTools` 샘플, Agentic RAG `RetrievalTools`) — 단 `requiredTool`(tool_choice 강제)은 미검증 |
| 4 — Governance | 🚧 진행 중 — `governance.auth`(API Key 인증)만 구현, **미검증**. 남은 것: rate limit/비용 트래킹/PII 필터/Guardrail, `observability`(토큰·비용·트레이싱·Eval) 전체 |

k8s 매니페스트/Dockerfile은 준비돼 있지만 아직 실제로 `kubectl apply`는 안 한 상태이고, 문서에 미해결 이슈로 하나 남아있습니다: `ChatController`의 `DEFAULT_SESSION_KEY`(HTTP 세션 고정) 경로가 실제로는 아무도 채우지 않아 항상 비어있다는 점 — 의도된 훅인지 마저 구현해야 하는지 확인 필요.

다음으로 뭘 진행할지 정하면 좋을 것 같습니다.

---

## 답변

Now let's build and start the app under the `wsl` profile.

---

## 답변

Build succeeded. Now let's start it in the background with the `wsl` profile.

---

## 답변

Good — it's starting and "API Key 인증 활성화" logged. Let's wait a bit more for full startup and check for errors.

---

## 답변

App started cleanly. Now let's run the actual verification cases against `/api/ai/chat` and `/actuator/health`.

---

## 답변

All three cases pass. Now the positive case — a valid key, which should let the request through to the actual Anthropic call.

---

## 답변

All 4 cases behave correctly: no key → 401, wrong key → 401, `/actuator/health` bypasses auth, valid key → passes through to a real Anthropic call. Let me stop the test server and revert the temporary config change.

---

## 답변

Now let's revert the temporary test config back to disabled.

---

## 답변

Config is cleanly reverted (no diff, working tree matches original disabled state) and the test process is stopped.

Now, per CLAUDE.md's doc-update rule, let's update `docs/09.dstone-ai-engine.md` to reflect that `governance.auth` is now verified.

---

## 답변

Now let's update the doc to mark this as verified, replacing the warning note and updating the phase table/status line.

---

## 답변

Found one more table at line 755 with the same stale "미검증" note. Let me fix that too.

---

## 답변

## 검증 완료: `governance.auth` (API Key 인증)

WSL 환경에서 `dstone-ai-engine`을 실제로 기동해서 검증했습니다 (테스트 후 설정은 원상복구).

- 헤더 없음 → `401` + "API Key 헤더가 없습니다"
- 잘못된 키 → `401` + "유효하지 않은 API Key 입니다"
- `/actuator/health` → 키 없이 `200` (k8s probe용 우회 정상 동작)
- 등록된 키 → `200` + 실제 Anthropic 응답 정상 통과

4가지 케이스 모두 의도대로 동작했고, `docs/09.dstone-ai-engine.md`의 "미검증" 표시 2곳(4번 항목 상태 표, 8.2절 경고문)을 실동작 검증 완료로 갱신했습니다.

**참고**: Ollama에 `bge-m3`가 아직 pull 안 되어 있고(`llama3.2`만 설치됨) — RAG 임베딩 실동작을 다시 테스트하게 되면 먼저 필요합니다.

다음으로 이어갈 만한 항목: `governance`의 나머지(rate limit/비용 트래킹/PII·Guardrail), `observability` 착수, 또는 `requiredTool` 검증 — 원하시는 걸 알려주세요.

---

## 질문

Ollama에 bge-m3가 아직 pull 안 되어 있고(llama3.2만 설치됨) => 이건 무슨 뜻이고 어떻게 진행해야 하지?

---

## 답변

확인해보니 제가 이전에 잘못 판단했습니다. 다시 조회해보니 Ollama에 `bge-m3`가 이미 설치되어 있습니다.

---

## 답변

**정정합니다.** 이전 답변이 틀렸습니다 — 제가 처음 `curl /api/tags` 결과를 `head -c 300`으로 잘라서 확인하는 바람에 목록 뒷부분이 잘려서 `llama3.2`만 보였던 겁니다. 실제로는 두 모델 다 이미 설치돼 있습니다.

방금 다시 확인한 결과:
```
설치된 모델: llama3.2:latest, bge-m3:latest
```
`bge-m3`로 실제 임베딩 호출도 해봤는데 정상적으로 1024차원 벡터가 나옵니다 (`dstone-ai-engine`의 `spring.ai.vectorstore.pgvector.dimensions: 1024` 설정과도 일치).

즉 걱정하실 부분 없이 RAG 임베딩은 바로 테스트 가능한 상태입니다. 잘못된 정보 드려서 죄송합니다 — 별도로 조치하실 건 없습니다.

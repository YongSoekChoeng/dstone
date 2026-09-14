# 소스가_바뀌었어_appdstonedo

> 생성일시: 2026-09-14 12:21

## 질문

소스가 바뀌었어 /app/dstone/docs 의 모든 md 파일들을 갱신해줘.
- 현재까지 진행된 모든 추가/수정 작업에 대해서 유관한 md 파일이 존재하지 않을 경우 신규 작성.
- 현재까지 진행된 모든 추가/수정 작업에 대해서 유관한 md 파일들이 존재하면 내용을 수정.
- 내용은 최대한 친절하고 풍부하고 비주얼적으로 풍성하게 표현한다.
- /app/dstone 소스 내에 불필요하거나 구현내용과 동일하지 않은 주석이 있을 경우 주석을 바로잡아줘.

---

## 답변

1876 lines total across dstone-ai-engine's Java files — manageable. Let me read all of them plus the current docs page to compare accurately.

---

## 답변

Let me read the saved output file directly for full content.

---

## 답변

Good — SAGA/Outbox lives in `dstone-common`, not a separate `dstone-saga` module. So `docs/10.dstone-saga.md` documents a real subsystem, just misleadingly named as if it were its own module. Let me check its current content and recent changes to that package.

---

## 답변

No other docs reference the stale paths — good, confirms scope is isolated to `09.dstone-ai-engine.md`, `05.dstone-common.md`, and the source comments. Let me check the remaining source files touched by 3056e3b I haven't looked at yet (ChatController.java, ChatService.java — already read) and confirm the full set of stale comments, plus check `getMapProperty`/`getEnv` usage elsewhere.

---

## 답변

Now I have a complete picture. Let me fix the stale source comments in `dstone-ai-engine`, starting with the dead code and package-path references.

---

## 답변

Now `CallerContext.java`:

---

## 답변

Now `AiTool.java`:

---

## 답변

Now `ChatRequest.java`:

---

## 답변

Now `RedisChatMemorySession.java` — fix the typo/space and stale package path:

---

## 답변

API Error: Connection lost mid-response. The response above may be incomplete.

---

## 질문

다시 연결됬어

---

## 답변

Reconnected — I'll pick back up where I left off. I was midway through fixing stale comments in `dstone-ai-engine`. Continuing with `RedisChatMemorySession.java`:

---

## 답변

Now `PiiGuardrailAdvisor.java` and `UsageLoggingAdvisor.java` — fix `config.ConfigChatClient` → `common.config.ConfigChatClient`, and the "observability" package misreference:

---

## 답변

Now `UsageProperties.java` — same "observability" package misreference:

---

## 답변

Now let's fix `application.yml`'s stale comments (gateway/rag package references, deleted classes):

---

## 답변

Now let's update `docs/05.dstone-common.md` to document `ConfigProperty`'s new list/map binding capability:

---

## 답변

Now the major rewrite — `docs/09.dstone-ai-engine.md`. Starting with Section 3 (package structure):

---

## 답변

Now Section 4 (Phase 로드맵) — update the refactor note and add a new 2026-09-14 note:

---

## 답변

Now Section 5.3 — rename `RedisChatMemoryRepository` → `RedisChatMemorySession` throughout:

---

## 답변

Now Section 7.2 (`config.ConfigTool` references) and the "gateway/prompt 패키지" line:

---

## 답변

Now Section 7.6 — remove the now-false "dead import" claim:

---

## 답변

Now Section 8.3's diagram and bullets:

---

## 답변

Now Section 14's table — remove references to deleted classes:

---

## 답변

Now let's fix the Section 8 intro paragraph:

---

## 답변

Let's do a final verification pass over the whole file for any remaining stale references:

---

## 답변

All remaining occurrences are intentional (explaining what was deleted/renamed), not stale claims of current state. Good — the doc is now internally consistent. Let me check the mermaid Phase-4-overview diagram right after the header (the "🧭" box) for any leftover stale bits:

---

## 답변

This looks solid and well-formatted. Now let's check `docs/01.index.md`'s description of module 09 briefly matches, and verify `README.md` doesn't reference stale details:

---

## 답변

That's the corrected line I wrote earlier — clean. Let's also double check `ChatController.java` and `ConfigChatClient.java`/`Config.java` for anything stale (I haven't re-verified these directly beyond initial reads):

---

## 답변

Even number (74) — code fences are balanced. Let's also verify the `05.dstone-common.md` fences are balanced:

---

## 답변

File is syntactically clean. Let's do a full `mvn compile` sanity check to make sure nothing broke:

---

## 답변

dstone-common built cleanly. Now compile dstone-ai-engine (only comment changes there, but let's confirm nothing regressed):

---

## 답변

Both modules compile cleanly with no errors. Let's do a final summary check of all files touched in this session:

---

## 답변

## 요약

**배경**: `docs/09.dstone-ai-engine.md`와 `05.dstone-common.md`가 마지막으로 갱신된 시점(09-13, 커밋 `914adf2`) 이후, 09-13~14에 걸쳐 dstone-ai-engine의 패키지 구조가 크게 리팩터링됐습니다 — 최상위 `config`/`governance.auth`/`governance.ratelimit` 패키지가 `common.config`/`common.context`/`common.filter`로 통합되고, `ApiKeyProperties`/`ApiKeyEntry`/`RateLimitProperties`/`RateLimitOverride`/`RateLimiter` 5개 클래스가 삭제됐으며(대신 `dstone-common`의 `ConfigProperty.getListProperty()`가 `Binder` 기반으로 고쳐져 그 역할을 대신함), `RedisChatMemoryRepository`가 `RedisChatMemorySession`으로 개명됐습니다. 나머지 모듈(boot/batch/batchadmin)과 다른 docs 파일들은 이미 최신 상태라 손댈 게 없었습니다(`dstone-saga.md`가 다루는 SAGA/Outbox 코드도 변경 없음).

**문서 갱신**
- `docs/09.dstone-ai-engine.md`: 패키지 구조 트리, Phase 로드맵, 8절(Governance) 전체의 시퀀스 다이어그램·설명을 새 구조에 맞게 수정하고, "설정 키 이름과 실제 패키지 경로가 다르다"는 경고 박스와 2026-09-14 리팩터링 노트를 추가.
- `docs/05.dstone-common.md`: `ConfigProperty`의 새 `getListProperty`(버그 수정)/`getMapProperty`/`getEnv` 메서드를 표와 함께 설명 추가.

**소스 주석 정리** (dstone-ai-engine 10개 파일 + `application.yml`): 삭제된 클래스를 참조하는 죽은 주석/죽은 코드(`ApiKeyAuthFilter`의 주석 처리된 생성자 등) 제거, `net.dstone.ai.config.*` → `net.dstone.ai.common.config.*` 등 이동된 패키지 경로 수정, 실제로는 없는 `observability` 패키지를 가리키던 설명 수정, rate limit 검증이 기동 시점이 아니라 요청 시점에 실패한다는 구현과 다른 설명 수정.

두 모듈(`dstone-common`, `dstone-ai-engine`) 모두 `mvn compile`로 정상 빌드 확인했습니다.

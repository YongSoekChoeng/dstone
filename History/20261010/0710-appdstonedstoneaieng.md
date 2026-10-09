# appdstonedstoneaieng

> 생성일시: 2026-10-10 07:10

## 질문

/app/dstone/dstone-ai-engine/src/main/resources/definitions/agents/*.yml 들에 대해서
파일명을 agent의 id 와 동일하게 수정해줘.

---

## 답변

먼저 agent 정의 파일들의 현재 파일명과 `id` 값을 확인하겠습니다.

---

## 답변

id가 추출되지 않았습니다. 파일 형식을 직접 확인하겠습니다.

---

## 답변

`sample/`, `testApp/` 은 이미 파일명과 id가 같고, 다른 것은 `pilot/` 의 11개뿐입니다. 이름을 바꾸기 전에 옛 파일명을 참조하는 곳을 찾겠습니다.

---

## 답변

이름 변경과 참조 갱신이 끝났습니다. 정의 파일 검증 테스트를 돌려 확인하겠습니다.

---

## 답변

`agents/` 아래 yml 파일명을 모두 agent `id`와 같게 맞췄고, `DefinitionFilesTest`도 통과했습니다.

`sample/` 13개와 `testApp/` 2개는 이미 id와 같아서 그대로 두었고, 바뀐 것은 `pilot/` 11개입니다(`git mv`로 변경).

| 이전 | 이후 |
|---|---|
| `00.source-investigator-agent.yml` | `pilot-source-investigator-agent.yml` |
| `01.requirment-analyzer-agent.yml` | `pilot-requirment-analyzer-agent.yml` |
| `02.impact-analyzer-agent.yml` | `pilot-impact-analyzer-agent.yml` |
| `03.impact-analyzer-review-agent.yml` | `pilot-impact-analyzer-review-agent.yml` |
| `04.architect-agent.yml` | `pilot-architect-agent.yml` |
| `05.developer-agent.yml` | `pilot-developer-agent.yml` |
| `10.request-router-agent.yml` | `pilot-request-router-agent.yml` |
| `21.newdev-reference-agent.yml` | `pilot-newdev-reference-agent.yml` |
| `22.newdev-analyzer-agent.yml` | `pilot-newdev-analyzer-agent.yml` |
| `23.newdev-file-designer-agent.yml` | `pilot-newdev-file-designer-agent.yml` |
| `24.newdev-file-developer-agent.yml` | `pilot-newdev-file-developer-agent.yml` |

- **참조 갱신**: 옛 파일명은 코드에는 없고 주석·문서에만 있어서 그것만 고쳤습니다. 대상은 pilot agent yml 4개의 주석, `pilot-workflow.yml` 주석 1곳, `docs/09.dstone-ai-engine.md`, `CLAUDE.md`입니다. `History/`는 과거 기록이라 그대로 두었습니다.
- **`target/classes` 정리**: 옛 이름 파일이 빌드 결과물에 남아 "agent id가 중복 등록되었습니다"로 테스트가 실패했습니다. `target/classes/definitions/agents/pilot/`의 옛 이름 yml 11개만 지웠습니다.
- **서버 반영 시 주의**: 엔진을 다시 띄울 때는 `mvn clean package`로 빌드하세요. Windows 쪽 클론도 pull 후 clean 빌드를 하지 않으면 같은 중복 오류가 납니다.
- **순서 번호 사라짐**: 파일명 앞 번호(00~24)가 없어져 폴더에서 알파벳순으로 보입니다.

커밋은 하지 않았습니다.

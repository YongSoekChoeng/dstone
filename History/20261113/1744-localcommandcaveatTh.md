# localcommandcaveatTh

> 생성일시: 2026-11-13 17:44

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="7277">
dstone-boot>knowledge>노드맵 화면에서
프로젝트:dstone-knowledge
이름으로 찾기:AnalysisJobRunner#submit
이렇게 찾았을 때 노드맵에서 아래의 연결이 보이지 않는데... 잘못된게 아닌가?
AnalysisJobRunner#submit
    -> AnalysisJobRunner#runJob
</pasted_content id="7277">

---

## 답변

DB에서 `AnalysisJobRunner`의 메서드와 호출 관계를 직접 확인하는 중입니다.

---

## 답변

네, 잘못된 결과가 맞습니다. 다만 호출을 놓친 것은 아닙니다. 분석은 그 호출을 찾았는데, 노드 맵이 `submit`에서 그 호출까지 따라갈 연결이 없습니다.

## 원인

`AnalysisJobRunner.java:64` 코드는 이렇습니다.

```java
public void submit(final AnalysisJobContext context) {
    analysisJobExecutor.execute(new Runnable() {   // 익명 클래스 AnalysisJobRunner$1
        public void run() {
            runJob(context);                       // 이 호출의 주인은 $1#run
        }
    });
}
```

DB(revision 198)를 직접 조회한 결과입니다.

| 관계 | 저장 여부 |
|---|---|
| `AnalysisJobRunner$1#run` → `runJob` (CALLS, line 71) | 있음 |
| `submit` → `$1` | **없음.** `new Runnable()`이 프로젝트 밖 타입 `java.lang.Runnable`로 연결됨 |
| `$1` → `Runnable` (IMPLEMENTS) | 있음 |

DECLARE 단계는 설계상 익명 클래스를 별도 타입(`kind = ANONYMOUS`)으로 저장하고, 그 안의 호출도 익명 클래스 메서드의 것으로 기록합니다. 그런데 바깥 메서드 `submit`과 익명 클래스 `$1`을 잇는 관계는 어디에도 만들지 않습니다. 그래서 노드 맵 API(`/graph/neighborhood`)에서 `submit`의 아래쪽 노드는 `getAnalysisId`뿐이고 `runJob`은 나오지 않습니다. 실행 중인 4081 인스턴스로 직접 확인했습니다.

## 왜 문제가 되는가

- **람다는 됩니다.** 같은 코드를 `execute(() -> runJob(context))`로 쓰면 그 호출은 `submit`의 것으로 기록되어 맵에 나옵니다. 같은 로직인데 코드 모양에 따라 결과가 달라집니다.
- 이 저장소는 람다를 쓰지 않고 익명 클래스를 쓰는 규칙이라, 이 빈틈에 걸리는 코드가 많습니다. revision 198에만 익명 클래스가 51개 있고, 그 안에서 프로젝트 메서드를 부르는 호출 126건이 바깥 메서드에서 끊겨 있습니다. 레거시 Java 프로젝트도 사정이 같습니다.

## 고치는 방법 (제안)

1. **분석:** LINK 단계에서 SQL로 `바깥 메서드 → 익명 클래스` 관계(예: `DECLARES_ANONYMOUS`)를 만듭니다. 익명 클래스의 `outer_symbol_id`와 줄 범위(64–77)가 바깥 메서드의 줄 범위 안에 있는지를 보고 도출합니다. Java를 다시 파싱할 필요는 없습니다.
2. **노드 맵:** 따라갈 관계에 이 관계를 추가하고, 익명 클래스의 메서드(`$1#run`)를 중간 노드로 보여 줍니다. 결과는 `submit → $1#run → runJob`이 됩니다. 맵에서는 익명 클래스 메서드를 보여 주지 않고 `submit → runJob`으로 바로 이을 수도 있습니다.
3. 분석 결과가 바뀌므로 증분 분석과 전체 분석의 결과가 같은지 비교하고, 기존 리비전에는 `rerunFrom`이 필요하다는 점을 문서에 적어야 합니다.

진행할까요? 진행한다면 2번에서 `$1#run`을 중간 노드로 보여 줄지, 건너뛰고 `submit → runJob`으로 바로 이을지 정해 주세요.

---

## 질문

건너뛰고 연결하면 될 것 같아.  진행하고 올려줘.

---

## 답변

Now the edits: SQL, DAO, LinkPass, CarryResolvePass.

---

## 답변

Build and test next. First I'll check where the running 4081 instance's jar is.

---

## 답변

시험 인스턴스(4181 포트)에서 revision 198의 LINK 단계를 다시 돌리고 있습니다. 끝나면 노드 맵 API 결과를 확인하겠습니다.

---

## 답변

세 경우 모두 기대한 대로 나옵니다. 클래스 단위로 골랐을 때도 확인하겠습니다.

---

## 답변

코드 변경과 시험이 끝났고, 지금은 문서(`docs/11`, `.claude/docs`)에 반영하는 중입니다.

---

## 답변

고쳐서 `main`에 푸시했습니다(`faf46227`). 다만 지금 4081에서 떠 있는 인스턴스는 아직 예전 jar라서, 노드 맵 화면에는 다시 빌드하고 재시작해야 반영됩니다.

## 바꾼 내용

- **분석(LINK 단계):** 바깥 메서드에서 그 안에 만든 익명 클래스의 메서드로 가는 관계 `ENCLOSES_ANONYMOUS`를 SQL로 만듭니다. 예를 들면 `submit → $1#run`입니다. 익명 클래스 안에 익명 클래스가 또 있어도 이어집니다.
- **노드 맵:** 관계 트리가 이 관계를 따라가되 `$1#run`은 건너뛰고 `submit → runJob`으로 바로 그립니다. 이 관계는 단계 수에 넣지 않아서 `runJob`이 `submit` 바로 다음 칸에 놓입니다. 노드 맵에서 `$1#run` 자체를 골랐을 때만 건너뛰지 않고 `submit → $1#run`을 그대로 보여 줍니다.
- **증분 분석:** CARRY_RESOLVE 단계는 이 관계를 이전 리비전에서 옮기지 않습니다. LINK가 매번 리비전 전체를 보고 새로 만들기 때문에, 증분 분석과 전체 분석의 결과가 같습니다. 이 부분은 실제로 비교해 보지 않았고 구조상 같아야 한다는 판단입니다.
- `docs/11.dstone-knowledge.md`와 `.claude/docs/knowledge-core.md`, `.claude/docs/knowledge-ui.md`에도 반영했습니다.

## 확인한 것

시험 인스턴스를 4181 포트로 띄워 revision 198에 `rerunFrom: LINK`를 돌렸습니다. 관계가 68건 생겼습니다.

| 고른 노드 | 결과 |
|---|---|
| `submit` | `startAnalysis → submit → runJob` (`runJob`이 한 칸 아래) |
| `runJob` | 부르는 쪽이 `submit`, 그 위가 `startAnalysis` |
| `$1#run` | `submit → $1#run → runJob` |
| `AnalysisJobRunner` 클래스 | `submit → runJob`이 나오고 `$1#run` 노드는 없음 |

자동 테스트는 이 부분을 다루는 것이 없어서 돌리지 않았습니다.

## 남은 것

1. **4081 반영:** 다시 빌드해서 재시작하고, `dstone-boot`의 `graph.js` 변경도 배포해야 합니다.
2. **다른 리비전:** revision 198은 시험하면서 이미 LINK부터 다시 돌렸습니다. 다른 리비전은 `rerunFrom: LINK`를 돌려야 반영됩니다.
3. **영향도 · 호출 관계 화면:** 같은 문제가 아직 남아 있습니다. 이 화면들은 새 관계를 따라가지 않아서, `runJob`의 영향도를 보면 `$1#run`에서 멈추고 `submit`과 그 위의 진입점이 빠집니다. 필요하면 이어서 고치겠습니다.

---

## 질문

3번 진행해줘.

---

## 답변

영향도 · 호출 관계 화면에도 같은 방식(익명 클래스 메서드 건너뛰기)을 적용하는 중입니다. 끝나면 시험한 뒤 푸시하겠습니다.

---

## 답변

Now build and test on port 4181.

---

## 답변

Calls look right. Now impact.

---

## 답변

Impact now reaches the controller entry point. Let me check that one endpoint with an empty path.

---

## 답변

시험 결과 모두 기대한 대로 나왔습니다. 이제 문서를 고치고 푸시하겠습니다.

---

## 답변

영향도 · 호출 관계 화면도 노드 맵처럼 익명 클래스 메서드를 건너뛰도록 고쳐서 `main`에 푸시했습니다(`fbbbfd5b`).

## 바뀐 결과 (`runJob` 기준, 시험 인스턴스 4181에서 확인)

| 화면 | 이전 | 이후 |
|---|---|---|
| 영향도 | `runJob` → `$1#run`에서 멈춤, 진입점 1개 | `runJob` → `submit` → `startAnalysis` → `ProjectController#startAnalysis`, 진입점 `POST /api/projects/{projectId}/analyses`까지 나옴 |
| 부르는 쪽 | `$1#run`이 부름 | `submit`이 부름 (관계 칸에 "익명 클래스 안" 표시) |
| 불리는 쪽 (`submit`) | `runJob` 없음 | `runJob` 나옴 (같은 표시) |

- 근거(파일:줄)는 실제로 호출한 줄(71줄)을 그대로 보여 줍니다.
- 영향도의 메서드 목록에서는 익명 클래스 메서드를 빼고, 그 자리에 바깥 메서드를 같은 거리로 보여 줍니다. 진입점 목록에는 `$1#run`의 `THREAD` 진입점이 남는데, 별도 스레드에서 실제로 시작되는 곳이라 그대로 두었습니다.
- `$1#run` 자체를 대상으로 고르면 건너뛰지 않습니다. 부르는 쪽에 `submit`이 "익명 클래스" 관계로 나옵니다.

`docs/11.dstone-knowledge.md`와 `.claude/docs/knowledge-ui.md`에도 반영했습니다.

## 남은 것

이전과 같습니다. 4081 인스턴스는 아직 예전 jar로 돌고 있어서, 다시 빌드하고 재시작해야 화면에 반영됩니다. 이번에 바꾼 `impact.js`와 `knowledge-terms.js`도 `dstone-boot`과 함께 배포해야 합니다. revision 198 말고 다른 리비전은 `rerunFrom: LINK`를 돌려야 반영됩니다.

# localcommandcaveatTh

> 생성일시: 2026-11-13 17:23

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

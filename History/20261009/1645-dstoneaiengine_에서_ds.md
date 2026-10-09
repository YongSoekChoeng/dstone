# dstoneaiengine_에서_ds

> 생성일시: 2026-10-09 16:45

## 질문

dstone-ai-engine 에서 
dstone.ai.tools.pilot.* 클래스들을 보면 pilot-workflow 에 종속된 기능들 같은데
그렇다면 workflow를 추가할 때마다 이렇게 java클래스가 늘어나야 하는 구조인가?

---

## 답변

아니요, Workflow를 추가한다고 Java 클래스가 반드시 늘어나는 구조는 아닙니다. 다만 **"경로로 값을 읽는 것" 이상의 결정적 로직이 필요한 Workflow는 Java Tool이 늘어나는 구조**가 맞습니다.

## 현재 상태

- Workflow 18개 중 전용 Java 클래스를 가진 것은 `pilot-workflow` 하나뿐입니다. 나머지 17개는 YAML과 범용 Tool만으로 돌아갑니다.
- `pilot-workflow`의 TOOL 스텝 17개 중 6개는 범용 Tool(`readFile`, `copyFileWithReplace`, `writeFile`, `replaceInFile`, `appendFile`, `editFileLines`)이고, 11개가 `pilotNewdev…` 전용입니다.

## 왜 pilot만 전용 클래스가 생겼나

2026-10-09에 jq를 없애면서 "로직은 YAML에 두지 않고 Java Tool로 만든다"로 정했기 때문입니다. 표현식이 경로 읽기만 하므로, 그 이상은 전부 Tool이 됩니다. 전용 Tool 11개를 성격별로 나누면 이렇습니다.

| 성격 | Tool | 범용화 가능성 |
|---|---|---|
| 파일 여러 개 읽기 (1개) | `ReadSources` | 가능. `readFiles` 같은 범용 Tool로 바꿀 수 있습니다 |
| 문서 만들기 (4개) | `WriteRequirements` / `WriteImpact` / `WriteDesign` / `WriteTasks` | 부분적으로 가능. 템플릿 파일 + 범용 `renderTemplate` Tool |
| 목록 모양 바꾸기 (4개) | `DesignInputs` / `PlanChanges` / `DevelopInputs` / `ApplyInputs` | 어렵습니다. jq가 하던 일이라 범용화하면 표현식 언어가 다시 필요합니다 |
| 검증 규칙 (2개) | `CheckAnalysis` / `CheckDesign` | 불가. 업무 규칙이라 어디엔가 코드로 있어야 합니다 |

즉 "LLM → LLM → 승인"처럼 단순한 Workflow는 YAML만으로 끝납니다. pilot처럼 LLM이 하던 일을 결정적 단계로 빼낼수록 Java가 늘어납니다. 대신 그 로직은 단위 테스트가 됩니다(`PilotNewdevLogicTest`).

## 실제로 걸리는 문제는 위치

클래스가 늘어나는 것보다, **특정 Workflow 전용 코드가 재사용용 엔진 jar 안(`net.dstone.ai.tools.pilot`)에 들어 있다**는 점이 더 문제입니다. 다른 SI 프로젝트가 엔진을 가져가면 pilot 로직 약 1,200줄이 딸려 가고, 그 프로젝트의 전용 Tool도 엔진 소스에 넣어야 합니다.

## 권장

1. **원칙은 유지합니다.** jq로 되돌리면 테스트할 수 없는 로직이 YAML에 다시 쌓입니다.
2. **Workflow 전용 Tool은 엔진 밖으로 분리합니다.** 엔진에는 범용 Tool만 두고, `pilot` 같은 것은 별도 모듈(또는 프로젝트 쪽 jar)에 `@AiTool` 빈으로 둡니다. `ConfigTool`이 `@AiTool` 빈을 스캔해서 등록하므로 엔진 코드는 거의 바꿀 필요가 없을 것으로 보입니다. 컴포넌트 스캔 범위와 정의 파일 로딩 경로는 아직 확인하지 않았습니다.
3. **범용화는 두 번째 사용처가 생길 때 합니다.** `readFiles`와 템플릿 렌더링은 다음 Workflow에서도 필요해지면 그때 뽑아내는 편이 안전합니다. 템플릿에 반복·조건을 넣기 시작하면 로직이 다시 Java 밖으로 나갑니다.

2번을 진행하길 원하시면 스캔 범위와 정의 파일 로딩 경로부터 확인해서 분리안을 잡겠습니다.

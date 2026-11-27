# localcommandcaveatTh

> 생성일시: 2026-11-27 10:19

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/model</command-name>
            <command-message>model</command-message>
            <command-args></command-args>

---

## 질문

<local-command-stdout>Set model to `Opus 5.5` and saved as your default for new sessions</local-command-stdout>

---

## 질문

<pasted_content id="0781">
나는 sm-workflow 을 보면서 느낀점이...
sm-workflow 호출구조가 너무 복잡하다는 느낌이야.
어디에서 시작해서 어디로 이어져서 진행이 되었는지 역추적도 힘들어.
호출구조를 직관적으로 바꿀 수 있을만한 좋은 아이디어가 있을까?
물론 성능을 떨어뜨려서는 안되고.
일단 네 의견을 물어보고 싶어.
</pasted_content id="0781">

---

## 답변

먼저 sm-workflow의 실제 구조부터 보겠습니다.

---

## 답변

저도 같은 생각입니다. `sm-workflow.yml`을 열어 보니 복잡한 이유가 꽤 분명하게 나옵니다.

## 지금 왜 따라가기 힘든가

1. **61개 step이 한 줄로 늘어선 goto 구조입니다.** `next`, `routes`, `onFailure`가 서로 아무 데로나 이어지고, 되돌아가는 화살표만 15개가 넘습니다. 요구사항, 영향도, 설계, 개발, 검증 같은 "단계"가 정의에 없어서 사람이 머릿속으로 묶어 봐야 합니다.
2. **진짜 일보다 흐름을 위한 잡일 step이 더 많습니다.** AGENT/SUPERVISOR는 15개 정도이고 나머지는 이런 것들입니다.
   - `countUp` + `within/over` 5개: 재시도 횟수 세기
   - `containsValue` 7개, `isFileExist` 2개: 분기 판단
   - `readFile` 10개: 그중 5개는 Agent가 파일을 고친 뒤 "다시 읽기"

   결국 "자동 수정 루프" 하나가 step31→32→33→34→18→53→28→… 처럼 여러 step에 흩어집니다.
3. **id와 순서가 따로 놉니다.** id는 바꿀 수 없다는 규칙이 있으니 나중에 끼운 step이 `step59`, `step60`이 됐습니다. 이름 앞 번호도 `58-영향도 분석`(step08), `47-2-DDL 반영 승인`처럼 흐름과 어긋났고, 파일 안에서도 step37~61이 뒤쪽에 몰려 있습니다.
4. **실행 기록에 "어디서 왜 왔는지"가 남지 않습니다.** 엔진은 `${arrival}`로 마지막 결정을 알고 있지만, step 이력에는 이전 step과 지나온 화살표의 종류(next / route 이름 / onFailure / 승인 결정)가 따로 기록되지 않는 것으로 보입니다. 역추적이 어려운 직접 원인이 이것입니다.

## 제안 (효과 큰 순서)

**① 단계(phase) 묶음 도입: 정의만 바꾸고 실행 비용은 그대로**
```yaml
phases:
  - id: design
    name: 설계
    entry: step11
    exits: { approved: develop, revise: design, toImpact: impact }
    steps: [step11, step12, step37, step50, step51, step52, step13]
```
- 단계 안에서는 지금처럼 자유롭게 잇고, **단계 밖으로는 `exits`에 적은 이름으로만** 나가게 레지스트리가 검증합니다. 그러면 큰 흐름은 "요구사항 → 영향도 → 설계 → 개발 → 검증" 다섯 칸으로 읽힙니다.
- 디자이너와 진행 기록 화면은 단계 단위로 접고 펼칩니다.
- 이름 번호도 `설계-03`처럼 단계 안 순번으로 붙이면 step을 끼울 때마다 전체 번호를 다시 매길 일이 없어집니다. stepNN id는 지금 규칙 그대로 둡니다.
- 하위 Workflow(`type: WORKFLOW`)로 쪼개는 방법도 있지만 state를 넘기고 받는 비용과 일시정지·되감기 처리가 복잡해집니다. 그래서 묶음 쪽을 권합니다.

**② 전이(transition) 기록: 역추적을 바로 해결**
- step 이력 행마다 `FROM_STEP`, `VIA`(`next` / `route:problem` / `onFailure` / `approval:재개발`), `ROUND`(몇 번째 방문인지)를 남깁니다.
- 진행 기록 화면에 경로를 빵부스러기처럼 보여 줍니다.
  `개발(2회차) ← 컴파일 실패 ← 자동수정(1/2) ← 기동 실패 ← …`
- 컬럼 몇 개를 INSERT할 때 함께 쓰는 정도라 성능 영향은 없습니다.

**③ 잡일 step을 엔진 속성으로 흡수: step 수 61 → 대략 45**
- 재시도 상한: `countUp` step 대신 `retry: { counter: autoFixRound, max: 2, over: step19 }`. 루프 제어는 Java가 맡는다는 원칙에도 맞습니다.
- 값 분기: `containsValue`/`isFileExist`만 하는 step 대신, 앞 step의 결과 값으로 바로 `routes`를 고르게 합니다(`routeBy: ${state.verification.failedCount}`). 값을 읽는 경로일 뿐 로직이 아니어서 "YAML에 로직 금지" 규칙과도 맞는지 확인해 볼 만합니다. 이 부분은 경계가 애매하니 결정이 필요합니다.
- "다시 읽기" `readFile`은 일단 두는 편이 낫다고 봅니다. 없애려면 상태를 파일 참조로 바꿔야 해서 손이 큽니다.
- Tool 호출과 이력 행이 줄어드니 오히려 조금 빨라집니다.

**④ 파일 배치 정리(바로 할 수 있음):** YAML 안의 step을 단계 순서대로 다시 늘어놓고 단계마다 구분 주석을 넣습니다. id는 그대로이니 기존 실행 기록과 layout.json에는 영향이 없습니다.

## 제 추천

**②와 ④를 먼저**, 그다음 **①**을 권합니다. ②는 "역추적이 힘들다"는 문제를 바로 풀고, ④는 비용이 거의 없습니다. ①은 엔진(레지스트리 검증, 디자이너, 진행 화면)까지 손대야 해서 가장 크지만, 장기적으로 효과도 가장 큽니다. ③의 `routeBy`는 YAML 규칙과 충돌하지 않는지 먼저 정해 주셔야 합니다.

원하시면 ②부터 구체적인 설계(테이블 컬럼, 화면 모양)를 잡아 보겠습니다.

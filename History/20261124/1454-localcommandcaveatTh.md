# localcommandcaveatTh

> 생성일시: 2026-11-24 14:54

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="3556">
dstone-ai-engine Workflow 실행 관리 를 진행 중이야.

<확인 요청>
Windows 에서 진행.
실행 ID : daa7c276-66e8-48f1-9937-ab89d865f041

이 실행아이디를 분석해서 튜닝포인트를 잡고 싶어.
1. 결과(정확도)
    - 적어도 이 실행은 성공적으로 끝났어.
2. 성능(속도)
    - 여러번 서버를 내렸다 올렸다 해야 하는 이유를 알려줘.
    - 전문가 에이전트가 로그인에 실패한 이유를 알려줘.
    - 이렇게 시간이 소요되는게 불가피한가?
</pasted_content id="3556">

---

## 답변

Windows에서 돈 실행 `daa7c276-66e8-48f1-9937-ab89d865f041`의 기록부터 찾아보겠습니다.

---

## 답변

단계별 시간을 확보했습니다. 이제 각 Agent의 출력과 state 내용을 보겠습니다.

---

## 답변

Agent 내부의 Tool 호출 기록이 필요합니다. Windows 쪽 로그와 실행 파일 기록을 찾아보겠습니다.

---

## 답변

전문가 Agent(`sm-troubleshooter-agent`) 한 번에 입력 토큰이 870만 개입니다. 내부 Tool 호출 순서는 execution.log에서 보겠습니다.

---

## 답변

전문가 Agent는 시험 서버를 두 번 띄웠고(#9, #39), 11:26:57~11:27:59 사이에 로그인 입력을 반복했습니다. 그 구간의 실제 입력과 결과를 보겠습니다.

---

## 답변

로그인 실패 메시지("사용자아이디가 없거나 비밀번호가 맞지 않습니다")가 확인됩니다. 왜 같은 입력을 네 번 했는지 Agent의 추론 기록을 보겠습니다.

---

## 답변

추론 본문은 로그에 남지 않습니다. 엔진이 `${secret:…}`을 어떻게 바꿔 넣고 결과를 가리는지 코드를 확인하겠습니다.

---

## 답변

원인 후보가 보입니다. 비교를 위해 검증 Agent(`sm-verifier-agent`)의 로그인 과정과 시험 서버 기동 시간의 내역도 보겠습니다.

---

## 답변

서버 기동 단계별 시간은 엔진 로그에 없습니다. Tomcat 로그와 `startSandboxServer`의 재사용·재기동 조건을 보겠습니다.

---

## 답변

단계별 시간을 합산하겠습니다.

---

## 답변

분석이 끝났습니다. DB 실행 기록, Windows 쪽 `execution.log`, LLM 로그, 시험 서버의 `server.log`를 대조한 결과를 정리합니다.

# 실행 `daa7c276…` 분석 (sm-workflow 2.6.0)

## 전체 시간: 1시간 58분 (10:24:31 ~ 12:22:47)

| 구분 | 시간 | 비율 |
|---|---|---|
| 사람 승인 대기 (step09·13·19·19) | 89.7분 | 76% |
| 기계 시간 | 28.6분 | 24% |
| └ 전문가 `sm-troubleshooter-agent` | 11.7분 | |
| └ 검증 `sm-verifier-agent` (2회) | 6.9분 | |
| └ 시험 서버 기동 step29 (2회) | 3.1분 | |
| └ 설계·영향도·개발 Agent | 6.5분 | |

---

## 1. 결과(정확도): 성공했지만 문제가 두 개 있습니다

**① 첫 검증이 고장 난 기능을 PASS로 판정했습니다 (가장 큰 튜닝 포인트)**
- 검증 Agent는 TASK-02 ⑨(썸네일 클릭 → 이미지 팝업)를 "원본 이미지가 로드됨(200)"이라며 PASS로 판정했습니다.
- 하지만 로그를 보면 팝업 스냅숏은 `dialog "이미지 보기" [active]`뿐이고 안에 이미지가 없었습니다. 200 응답을 받은 것도 원본이 아니라 썸네일(`…_thumb.png`)이었습니다.
- 그래서 사람이 직접 찾아내 "전문가 해결"을 눌러야 했습니다. 이 한 번의 오판 때문에 기계 시간 약 17분(전문가 704초, 서버 79초, 재검증 242초)과 사람 대기 43분이 더 들었습니다.
- 실패 분류: 모델·프롬프트(근거 기준). 팝업이나 새 창은 안의 내용(img 요소, 원본 URL 요청)까지 확인해야 PASS라는 기준을 검증 Skill에 넣고, 이 사례를 평가 세트에 추가하면 됩니다.

**② 전문가가 원인 ②(백슬래시)를 잘못 짚었습니다**
- 전문가는 "썸네일 URL의 `\` 때문에 404"라고 진단했습니다.
- 그런데 첫 검증 때 새로 올린 행도 `thumbnail_url='/Upload/UpImages/PRD\38686819…_thumb.png'`로 백슬래시가 들어 있었는데, 브라우저는 `…/PRD/38686819…_thumb.png → 200`으로 불러왔습니다. 브라우저가 `\`를 `/`로 바꿔 주기 때문입니다.
- 실제로 404가 난 것은 게시물1·2처럼 **원래 DB에 이미 있던 행**이었습니다. 이 행들의 이미지 파일은 시험 서버의 사본 폴더에 없습니다. 원래 DB에는 `tbts_image_board`와 메뉴 행도 이미 들어 있습니다(시험 서버가 "DDL 주의 - 이미 있어 건너뜀"이라고 알림).
- 코드 수정(`/`로 치환) 자체는 해가 없습니다. 다만 덧붙인 DML(REPLACE)과 "사람이 DB에 적용해야 함"이라는 미해결 안내는 잘못된 근거에서 나왔으니 적용 전에 다시 보시는 것이 좋습니다.

## 2. 성능(속도)

### 서버를 여러 번 내렸다 올린 이유 (4회, 합계 약 400초)

| # | 시각 | 소요 | 이유 | 피할 수 있었나 |
|---|---|---|---|---|
| 1 | step29 10:56 | 108초 | 첫 검증 | 필요 |
| 2 | 전문가 #9 11:24 | 약 109초 | step19 승인 대기에 들어가면 엔진이 서버를 모두 내림(`executionLeft`). 21분을 기다리는 동안 띄워 둘 수는 없음 | 필요 (재현용) |
| 3 | 전문가 #39 11:28 | 약 103초 | `ImageUtil.java`를 고침. 같은 step 안에서 다시 부르면 항상 새로 띄움 | 재기동은 필요. 다만 매번 **전부** 새로 함 |
| 4 | step29 11:34 | 79초 | 전문가가 띄운 뒤에 DDL 파일을 고쳤고(fingerprint가 달라짐) 직접 `stopTestServer`도 불렀음 | 깨끗한 상태로 재검증하려면 필요 |

기동 한 번의 내역(4번째 기준): 사본 동기화·DB 복제·DDL 약 30초 → `mvn clean package` 24.7초(105개 파일 전체 재컴파일) → Tomcat 약 20초.
**3번처럼 같은 step에서 다시 띄울 때는** DDL이 그대로면 DB 재복제를 건너뛰고, `clean` 없이 증분 빌드하고, JSP만 바뀌었으면 파일 복사만 하는 식으로 줄일 수 있습니다. 한 번에 약 40~60초가 줄 것으로 봅니다(추정).

### 전문가가 로그인에 실패한 이유
- testApp 로그인 화면(`comm/login.jsp:288`)은 비밀번호 칸에 **기본값이 미리 채워져** 있습니다.
- 전문가는 `browser_type`과 `browser_fill_form`을 세 번 했습니다. 이것은 `fill`이라 칸을 지우고 다시 쓰므로 문제가 없습니다. 그다음 `slowly: true`로 한 번 더 입력했는데, 이 방식은 Playwright `pressSequentially`라 **기존 값 뒤에 덧붙입니다**. 그래서 비밀번호가 두 번 이어 붙은 채 로그인을 눌렀고 "비밀번호가 맞지 않습니다"가 나왔습니다.
- 애초에 같은 입력을 되풀이한 것은 엔진이 결과에서 비밀값을 `****`로 가리기 때문으로 보입니다(추정). Agent에게는 `fill('****')`만 보이므로, 진짜 값이 들어갔는지 확인할 수 없어 다시 입력한 것입니다. 2회차 검증 Agent도 같은 이유로 지우고 다시 입력하기를 6회차 동안 반복했습니다.
- 실패 분류: **Tool**(`ToolCallGate`). 프롬프트 문제가 아닙니다. 고칠 방법은 두 가지입니다.
  - 결과를 `****` 대신 `${secret:testApp.testPassword}`로 되돌려 보여 주기. 그러면 Agent가 "제대로 들어갔음"과 "두 번 들어갔음"을 구별할 수 있습니다.
  - 자리표시자가 든 `browser_type`에서는 `slowly`를 빼기.
- 이번 헛수고는 18회차, 약 62초였습니다.

### 이 정도 시간이 불가피한가?
- **76%는 사람 대기**라 엔진 밖의 시간입니다.
- 기계 28.6분 중에서 줄일 수 있는 것:
  - **첫 검증의 오판 → 약 17분 + 사람 대기**: 가장 큰 몫입니다. 검증이 FAIL을 냈다면 사람 없이 수정 경로로 넘어갔을 것입니다.
  - 서버 재기동 최적화: 약 1~2분
  - 로그인 헛수고: 약 1.5분 (전문가 + 검증)
  - 그 외: 전문가 99회차 중 67회차가 브라우저 조작이었습니다. 회차마다 문맥 8~11만 토큰을 다시 보내 입력이 모두 874만 토큰이었고, 그중 854만이 캐시였습니다. 회차당 약 3초라 빠른 편이지만 회차 수가 많습니다. 2회차 검증도 16개 시나리오를 67회차에 걸쳐 다시 확인했습니다. 다시 확인할 범위를 바뀐 파일과 관련된 것으로 좁힐 여지가 있지만, 평가로 비교한 뒤 정할 일입니다.
- 정리하면 **기계 시간의 절반 이상(약 20분)은 피할 수 있었고**, 그 대부분이 검증 정확도 하나에 걸려 있습니다.

---

**우선순위 제안**
1. 검증 Skill에 팝업·새 창은 내용까지 확인한다는 근거 기준을 넣고, 이 사례를 평가 세트에 추가
2. `ToolCallGate`의 비밀값 가림 방식 변경 (Tool 수정, 작음)
3. `startSandboxServer`가 같은 step에서 다시 띄울 때 DB 재복제와 `clean`을 생략

어느 것부터 진행할지 알려 주시면 작업하겠습니다. 이 분석을 공유용 페이지로도 만들어 드릴 수 있습니다.

---

## 질문

<pasted_content id="3556">
전에도 이야기 했지만 꼭 기억할 것은 나는 이 솔루션을 testApp용으로 만들고 있지 않고 범용적인 목적으로 만들고 있다라는 거야.
이 점은 향후에도 수정제안을 할 때 꼭 기억해줬으면 해.

  우선순위 제안
  1. 검증 Skill에 팝업·새 창은 내용까지 확인한다는 근거 기준을 넣고, 이 사례를 평가 세트에 추가
  ==>> 이 내용은 testApp에 종속된 내용이 아닌가 싶은데, 네 생각은 어때?
  2. ToolCallGate의 비밀값 가림 방식 변경 (Tool 수정, 작음)
  ==>> 진행해.
  3. startSandboxServer가 같은 step에서 다시 띄울 때 DB 재복제와 clean을 생략
  ==>> 이럴 때의 영향도가 어떻게 되?

* 이렇게 여러번 서버를 내렸다 올리는 이유가 java파일이 수정될 때마다 재기동 해야해서 하는 거지? 
  hot-deploy 방법은 없는건가?
</pasted_content id="3556">

---

## 답변

먼저 ToolCallGate 수정부터 하겠습니다. 코드를 읽어 보겠습니다.

---

## 답변

Now let me make the change: keep each secret value mapped to its placeholder, and in results replace the value with that placeholder.

---

## 답변

문서·설정 주석도 맞추고, 작은 단위 테스트를 추가하겠습니다.

---

## 답변

테스트 3개가 통과했습니다. 문서 한 곳을 고친 뒤, 질문하신 핫 디플로이 답변에 필요한 시험 서버 배포 방식을 확인하겠습니다.

---

## 답변

## 2. ToolCallGate 비밀값 가림 방식 변경 (완료)

- `ToolCallGate.java`: 결과에 비밀값이 섞여 돌아오면 이제 `****` 대신 그 값의 자리표시자 `${secret:프로젝트ID.testPassword}`로 바꿔 돌려줍니다. 바꿀 때는 긴 값부터 처리합니다.
  - 이제 칸에 값이 미리 들어 있으면 Agent에게 자리표시자로 보입니다. 값이 두 번 들어가면 `${secret:…}${secret:…}`처럼 두 개로 보이므로, 그대로 둘지 지우고 다시 넣을지 Agent가 판단할 수 있습니다.
  - 실제 값은 이전과 마찬가지로 LLM과 로그 어디에도 남지 않습니다.
- 테스트 `ToolCallGateSecretTest`를 추가했고 3개 모두 통과했습니다. 확인한 것은 실제 값으로 바꿔 넘기기, 결과를 자리표시자로 되돌리기, 두 번 들어간 값 구별, 허용하지 않은 Tool에 쓰면 거절하기입니다.
- 설명을 같이 고쳤습니다: `conf/application.yml` 주석, `docs/09` 11장 본문, `.claude/docs/ai-engine-features.md`.
- 아직 커밋하지 않았습니다. Windows 엔진에 반영하려면 커밋·pull 후 다시 빌드해야 합니다.

## 1. 검증 Skill 제안이 testApp에 종속된 것 아닌가?

말씀이 맞습니다. 제가 "팝업·새 창"이라고 쓴 것은 이번 사례의 모양을 그대로 옮긴 표현이었습니다. 다시 나눠 보면 이렇습니다.

- **일반 원칙**: "PASS의 근거는 판정 대상 자체를 관찰한 것이어야 한다"는 어느 앱에나 맞는 원칙입니다. 그런데 이 원칙은 `sm-verifier-agent.md:96`에 이미 있습니다("PASS: 기대한 결과를 직접 봤다"). 이번에는 모델이 기존 규칙을 지키지 않은 것입니다. 썸네일의 200 응답을 원본 이미지의 근거로 썼습니다.
- 그래서 실패 분류는 **모델**입니다. 여기에 프롬프트 한 줄을 더하는 것은 CLAUDE.md 원칙에도 맞지 않으므로 **철회하겠습니다**.
- **사례 데이터**: 이번 건은 평가 사례로 `conf/eval/testApp/`에 둡니다. git에서 빠지는 현장 전용 폴더입니다. 다른 앱에서도 같은 유형의 거짓 PASS가 되풀이되면, 그때 일반 원칙의 문구나 엔진 쪽 장치를 다시 검토하겠습니다.

앞으로 제안할 때는 범용 원칙인지, 프로젝트 Skill인지, 평가 사례인지를 먼저 나눠서 말씀드리겠습니다(메모리에 기록했습니다).

## 3. 같은 step에서 다시 띄울 때 DB 재복제와 `clean`을 생략하면 생기는 영향

**DB 재복제 생략** (DDL 파일이 그대로일 때만 해당):
- 좋은 점: 기동마다 약 30초 중 DB 복제 몫이 줄어듭니다. 다만 사본 동기화와 섞여 있어 정확히 몇 초인지는 따로 재 봐야 합니다.
- 나쁜 점: 앞선 시도에서 화면으로 등록·수정·삭제한 데이터가 그대로 남습니다. "목록이 N건이다", "삭제 후 사라진다" 같은 확인이 앞선 시도의 데이터 때문에 달라질 수 있습니다.
- 지금은 "같은 step에서 다시 부르면 처음 상태"라는 약속이 있는데, 이것이 바뀝니다. 전문가 Agent는 대체로 괜찮지만, 처음 상태가 필요한 경우를 위해 "DB 새로 만들기" 선택지는 남겨야 합니다.

**`clean` 생략**: 권하지 않습니다.
- 이번 로그를 보면 Maven은 바뀐 것을 감지하면 어차피 모듈 전체(105개 파일)를 다시 컴파일했습니다. 그래서 `clean`을 빼도 아끼는 시간은 몇 초뿐입니다.
- 반면 삭제 목록에 있는 파일이나 이름을 바꾼 클래스의 `.class`가 남습니다. 그러면 실제 반영 환경에서는 깨질 코드가 시험에서는 통과할 수 있습니다. 코드에도 바로 이 이유로 `clean`을 넣었다고 적혀 있습니다(`TestServerTool.java:1263`).

## Java를 고칠 때마다 재기동하는 것인가? 핫 디플로이는 없나?

재기동 4번 가운데 **Java 수정 때문인 것은 3번째 하나뿐**입니다. 나머지는 다음 이유였습니다.
- 첫 검증
- 승인 대기 동안 서버가 내려가 있었음
- 다른 step에서 처음 상태로 다시 검증해야 함

핫 디플로이 방법과 이 제품에 맞는지는 이렇습니다.

| 방법 | 범용성 | 판단 |
|---|---|---|
| JSP·JS·CSS·이미지만 바뀌었을 때 배포 폴더에 파일만 복사 (Tomcat이 JSP를 다시 컴파일함) | 높음 | **추천.** 재기동이 아예 필요 없음 |
| Java·매퍼 XML이 바뀌었을 때 Tomcat은 그대로 두고 **웹앱(Context)만 다시 로드** | WAR·Tomcat 구성이면 범용 | 추천. JVM 기동과 DB 복제는 건너뛰지만 Spring 초기화(이번 앱은 약 8초)와 빌드는 남음 |
| JVM HotSwap (디버그 모드) | 메서드 본문 수정만 가능, 필드·메서드 추가 불가 | 비추천. 되는 경우와 안 되는 경우가 섞여 판정이 흔들림 |
| DCEVM/HotswapAgent, JRebel, Spring DevTools | JDK 종류나 상용 라이선스에 묶이거나, 대상 앱에 의존성을 넣어야 함 | 범용 제품에는 맞지 않음 |

지금 구조(WAR 파일을 `webapps`에 복사)를 바꾸지 않고 만든다면, 바뀐 파일의 종류를 보고 정적 파일만 복사할지, 웹앱만 다시 로드할지, 전체를 새로 띄울지 고르는 방식이 됩니다. 이번 3번째 기동(약 103초)에 적용했다면 웹앱 다시 로드로 대략 40초 안팎이었을 것으로 추정합니다.

다만 정확도에서 가장 큰 손실은 첫 검증의 거짓 PASS였고, 서버 재기동 최적화는 1~2분 수준의 개선입니다. 진행할지, 진행한다면 정적 파일 복사만 할지 웹앱 다시 로드까지 할지 정해 주세요.

---

## 질문

아냐 단지 필요성만 점검했을 뿐이야.

---

## 답변

알겠습니다. 핫 디플로이와 재기동 최적화는 진행하지 않겠습니다.

현재 남아 있는 작업은 ToolCallGate 비밀값 가림 변경 하나이고, 아직 커밋하지 않았습니다. 바뀐 파일은 아래와 같습니다.
- `ToolCallGate.java`
- `ToolCallGateSecretTest.java`
- `conf/application.yml` 주석
- `docs/09`
- `.claude/docs/ai-engine-features.md`

커밋하라고 하시면 바로 하겠습니다.

---

## 질문

진행된 내용은 올려줘.

---

## 답변

커밋 `a46c27d3`을 원격 `main`에 푸시했습니다. 비밀값을 `****` 대신 자리표시자로 되돌려 가리는 변경입니다.

Windows 엔진에 적용하려면 Windows 쪽 클론에서 pull한 뒤 다시 빌드하고 재기동해야 합니다.

---

## 질문

지금은 어떤 변경건이라도 테스트서버를 올리고 브라우저테스트를 진행하지?

---

## 답변

sm-workflow 정의를 확인하겠습니다.

---

## 답변

네, 맞습니다. 지금 sm-workflow에는 변경 종류를 보고 갈라지는 곳이 없습니다. 컴파일만 통과하면 어떤 변경이든 같은 길을 갑니다.

```
step18 컴파일 확인 → step28 프로젝트ID 확인 → step41 설계서 다시 읽기
→ step40 바뀐 파일 확인 → step29 격리 시험 서버 기동 → step35 동작 검증(브라우저)
```

시험 서버를 띄우지 않고 개발 승인(step19)으로 바로 넘어가는 경우는 세 가지뿐이고, 모두 실패한 경우입니다.
- 컴파일 실패
- 요청서에 프로젝트ID가 없음
- 서버 기동 실패이고, 원인이 개발 결과가 아니라 환경인 경우

검증 Agent(`sm-verifier-agent`)도 브라우저 사용이 강제되어 있습니다. `requiredTools`에 `startSandboxServer`와 `browser_navigate|browser_snapshot`이 들어 있어서, 화면을 한 번도 열지 않으면 검증이 성립하지 않습니다(`agents/sm/sm-verifier-agent.md:64-67`).

그래서 화면이 없는 변경도 서버를 띄우고 화면을 열어야 끝납니다. 예를 들어 배치, 외부 연동 API, SQL만 고친 경우, 설정만 바꾼 경우가 그렇습니다. 이런 변경에서는 1~2분의 기동 시간과 검증 회차가 들고, 확인할 화면이 없으니 BLOCKED나 의미 없는 PASS로 끝날 수도 있습니다.

---

## 질문

여기에 어떤 차별성을 둘 수 없을까? 
예를 들자면, 너무 확실해서 격리/테스트서버테스트를 진행하지 않아도 되는 요청건에 대해서는 서버 테스트를 skip 한다던지,

---

## 답변

먼저 지금 승인 step이 선택지별로 흐름을 나눌 수 있는지 확인하겠습니다.

---

## 답변

네, 둘 수 있습니다. 지금 엔진 기능만으로도 Java 수정 없이 Workflow 정의를 고쳐서 만들 수 있습니다.

## 누가 "확실하다"고 판단하나: 사람이 정하는 것을 권합니다

LLM이 스스로 "확실하니 생략"이라고 정하게 하는 방식은 권하지 않습니다. 이번 실행에서 검증 Agent는 고장 난 기능을 PASS로 판정했습니다. 모델의 자신감은 생략 근거로 쓰기에 믿을 만하지 않습니다.

제안하는 구조는 이렇습니다.
- **설계 Agent가 제안합니다.** 설계서에 "검증 방법 제안" 칸을 두고, 화면 검증 / 기동만 확인 / 컴파일만 중 하나를 이유와 함께 적게 합니다. 예: "바뀌는 것이 안내 문구뿐이다", "배치라 화면이 없다".
- **사람이 설계 승인(step13)에서 정합니다.** 이미 설계서를 읽고 판단하는 자리라 추가 부담이 거의 없습니다. 생략의 책임도 승인자에게 분명히 남습니다.

## 검증 단계를 셋으로 나누기

| 선택 | 하는 일 | 맞는 변경 |
|---|---|---|
| 화면 검증 (지금과 같음) | 격리 서버 + 브라우저 검증 | 화면이 바뀌는 변경 |
| 기동만 확인 | 격리 서버가 뜨는지만 보고 끝냄. 빈 설정, 매퍼 XML 오류, DDL 오류는 이 단계에서 잡힘 | 배치, API, SQL만 고친 경우 |
| 컴파일만 | 서버를 띄우지 않음 | 문구, 주석, 단순 설정값 |

어느 경우든 마지막에는 개발 승인(step19)에서 사람이 봅니다. 생략해도 확인이 아예 없어지는 것은 아니고, 사람에게 넘어가는 것입니다.

## 정의 파일로 만드는 방법 (Java 수정 없음)

- step13의 `routes`에 선택지를 추가합니다. 예: `승인(개발진행, 기동만 확인)`, `승인(개발진행, 컴파일만)` → 모두 step14로 갑니다.
- 컴파일(step18) 뒤에 분기 step을 넣습니다. 기존 범용 Tool `containsValue`로 `${state.designApproval.decision}`을 보고, 컴파일만이면 step19로, 아니면 지금 흐름으로 보냅니다.
- 기동 확인(step29) 뒤에도 같은 분기를 넣어, 기동만 확인이면 검증(step35)을 건너뛰고 step19로 보냅니다.
- 설계 Agent(`sm-architect-agent`)의 출력 양식에 "검증 방법 제안" 항목을 추가합니다.

## 정해야 할 점

- **전문가 해결이나 재개발로 다시 돌 때도 생략을 유지할지.** 지금 설계대로라면 설계 승인 때의 선택이 그대로 남습니다. 사람이 "전문가 해결"을 눌렀다는 것은 문제가 있었다는 뜻이니, 그때부터는 화면 검증으로 되돌리는 편이 안전해 보입니다. 다만 이렇게 하려면 분기가 조금 늘어납니다.
- **개발 승인 화면에 표시하기.** "검증을 생략함(설계 승인 때 선택)"이 승인자 눈에 보여야 합니다. 이 내용은 이미 `decisions` 이력에 남기 때문에 화면에 보이는지만 확인하면 됩니다.

이 내용은 모두 Workflow 정의와 Agent 출력 양식만 바꾸므로 특정 대상 앱과는 관계가 없습니다. 진행할까요? 진행한다면 다시 돌 때의 처리(유지할지, 화면 검증으로 되돌릴지)도 같이 정해 주세요.

---

## 질문

다시 돌 때는 화면검증으로 진행하는걸로 정하고  진행해.

---

## 답변

기존 범용 Tool과 분기 방식을 먼저 확인하겠습니다.

---

## 답변

Workflow 정의를 고치겠습니다.

---

## 답변

JSON 형식이 원래 파일과 달라져 diff가 너무 커졌습니다. 원래대로 되돌리고 필요한 부분만 고치겠습니다.

---

## 답변

Now the architect agent: add a "검증 방법 제안" section to the design document.

---

## 답변

정의 파일 검증과 기존 sm-workflow 테스트를 돌려 보겠습니다.

---

## 답변

기존 테스트 하나를 새 흐름에 맞추고, 검증 수준 분기 테스트를 추가하겠습니다.

---

## 답변

기존 테스트 8개(원래부터 건너뛰던 1개 포함)와 새 테스트 4개가 모두 통과했습니다. 이제 문서를 맞추겠습니다.

---

## 답변

sm-workflow 2.7.0에서 검증 수준을 고를 수 있게 만들었습니다. 설계 승인 때 고른 생략은 첫 검증에만 적용되고, 다시 돌 때는 화면 검증으로 갑니다. 관련 테스트는 모두 통과했고, 아직 커밋하지 않았습니다.

**설계 승인(step13)에 생긴 선택지**

| 선택 | 첫 검증에서 하는 일 |
|---|---|
| 승인(개발진행) | 지금과 같은 화면 검증 |
| 승인(개발진행, 기동만 확인) | 격리 서버가 뜨는지만 보고 개발 승인으로 감 |
| 승인(개발진행, 컴파일만) | 서버를 띄우지 않고 개발 승인으로 감 |

**어떻게 나누나**
- 분기는 step28(프로젝트ID 확인) 바로 뒤에 넣었습니다. step29와 step35 사이에 step을 넣으면 서버를 넘겨받지 못하고 다시 띄우게 되기 때문입니다.
- 첫 검증인지는 기존 범용 Tool `countUp`으로 셉니다(step42, 한도 1). 그다음 step43~44가 `containsValue`로 설계 승인에서 고른 것을 확인합니다. Java는 고치지 않았습니다.
- "기동만 확인"은 새 step45가 격리 서버만 띄웁니다. 기동에 실패하면 지금처럼 원인이 개발 결과인지 판정하는 step30으로 가서 자동 수정을 거치고, 그다음 검증은 화면 검증입니다.
- 재개발, 전문가 해결, 자동 수정으로 다시 오면 `countUp`이 한도를 넘긴 상태라 화면 검증으로 갑니다.

**설계 Agent `sm-architect-agent` (2.2.0)**
- 설계서에 "검증 방법 제안" 칸을 추가했습니다. 세 가지 중 하나와 이유를 적고, 망설여지면 화면 검증을 고르게 했습니다. 실제로 고르는 것은 사람입니다.
- 문구에는 특정 앱의 이름이 없고 변경 성격으로만 적었습니다. 예: 배치, 연동, 쿼리만 고친 경우, 주석, 안내 문구.

**확인한 것**
- `SmVerifyLoopTest`에 테스트 4개를 추가했습니다: 컴파일만, 기동만 확인, 기동만 확인에서 기동 실패, 다시 돌 때 화면 검증. 기존 분기 테스트 하나는 새 흐름에 맞게 고쳤습니다.
- 그 밖에 `DefinitionBootTest`, `SmDecisionFlowTest`, `SmKeepFixesTest`, `RequiredToolsTest`, `WorkFlowTest`도 통과했습니다. 건너뛴 테스트는 원래부터 건너뛰던 것입니다.

**정해 주실 점 하나:** 컴파일 실패로 개발 승인에 갔다가 재개발로 돌아온 경우는 아직 검증한 적이 없으므로 "첫 검증"으로 쳐서 생략을 따르게 했습니다. 이것도 "다시 돌 때"로 보고 화면 검증으로 보내야 한다면 말씀해 주세요.

**함께 고친 문서:** `docs/09` 11.1절(흐름도와 설명, 버전 2.7.0), `.claude/docs/ai-engine-history.md`, `sm-workflow.layout.json`(새 노드 위치만 추가).

이번에도 커밋하고 올릴까요?

---

## 질문

응 올려줘.

---

## 답변

커밋 `51a63677`을 원격 `main`에 푸시했습니다. 설계 승인에서 검증 수준을 고르게 한 sm-workflow 2.7.0 변경입니다.

Windows 쪽 클론에서 pull하면 정의 파일(`sm-workflow.yml`, `sm-architect-agent.md`)이 바뀝니다. 다만 앞서 올린 ToolCallGate 변경(`a46c27d3`)이 아직 적용되지 않았다면, 그 Java 변경 때문에 다시 빌드하고 재기동해야 합니다.

---

## 질문

서버테스트 관련해서 클로드에 질의 한 결과인데 여기서 우리가 참고할 내용이 있을까?
/temp/review/클로드에서의테스트.txt

---

## 답변

문서의 주장 하나하나를 이번 실행(`daa7c276`)의 측정값과 지금 엔진에 이미 있는 기능에 대조해 보겠습니다. 검증 Agent의 회차별 수치부터 뽑습니다.

---

## 답변

참고할 만한 것은 하나, A("LLM은 테스트를 한 번만 쓰고, 다시 돌리는 일은 기계에 맡긴다")입니다. 나머지는 이미 하고 있거나, 이번 실행의 측정값과 맞지 않거나, 범용 제품에서는 어렵습니다. 근거는 이번 실행(`daa7c276`)의 측정값입니다.

**검증 Agent `sm-verifier-agent`의 시간 구성**

| | 첫 검증 | 다시 검증 |
|---|---|---|
| 걸린 시간 | 175초 | 242초 |
| LLM 회차 | 69회 | 67회 |
| 그중 브라우저 조작 | 62회 | 55회 |
| LLM이 쓴 시간 | 137초(78%) | 147초 |
| 회차당 평균 | 2.0초 | 2.2초 |
| 입력 토큰(처음 → 끝) | 2.2만 → 6.2만 | 2.4만 → 7.1만 |

느린 원인은 회차 수입니다. 클릭 한 번마다 LLM을 한 번 부르기 때문입니다. 게다가 다시 검증할 때도 회차가 거의 줄지 않아, 오히려 처음보다 오래 걸렸습니다.

## 항목별 판단

| 항목 | 판단 | 근거 |
|---|---|---|
| **A. 테스트를 스크립트로 만들고 다시 돌리기는 Tool로** | **참고할 가치가 큼** | 위 표대로 시간의 대부분이 클릭마다 부르는 LLM입니다. 다시 검증할 때 이 회차가 사라집니다. 판정이 매번 같아지고, 만든 스크립트는 회귀 테스트 자산으로도 남습니다. |
| B. HTTP·DB로 먼저 확인하고 브라우저는 마지막에 | 부분적으로 | 방향은 맞습니다. 다만 세션 로그인·CSRF를 쓰는 레거시 앱은 HTTP로 호출하려면 로그인 처리가 따로 필요합니다. 지금 Agent용 Tool은 `httpGet`(GET)뿐입니다. A와 묶어서 다루는 편이 낫습니다. |
| C. 서버를 계속 띄워 두기 | 이미 논의함 | 다음 step으로 서버를 넘기는 것은 이미 하고 있습니다. 남은 것은 자동 수정 뒤의 재기동입니다. DB 스냅샷으로 되돌리는 방식은 DBMS마다 방법이 달라 범용으로 만들기 어렵습니다. |
| D. 스냅샷 줄이기, 완료 기준마다 Agent 나누기 | 효과 작음 | 클릭·입력의 결과는 이미 스냅샷을 파일 링크로만 돌려줍니다. 입력도 6~7만 토큰 수준입니다. 기준마다 Agent를 나누면 로그인과 화면 진입을 기준마다 반복하게 됩니다. |
| E. 어디서 시간이 드는지 재기 | 이미 있음 | 회차별 시간·토큰(`AgentCallStatsAdvisor`)과 step별 시간이 로그에 남습니다. 이번 분석도 그것으로 했습니다. |
| "로컬 모델은 입력 읽기(prefill)에 몇 분이 걸린다" | 이번 측정과 맞지 않음 | 회차당 2초 안팎이고 캐시도 대부분 맞았습니다. 문제는 회차 수입니다. |
| "코드를 만든 쪽이 테스트하라" | 따르지 않는 것을 권함 | 개발과 검증을 다른 Agent가 맡는 것은 자기 결과를 후하게 보는 것을 막으려는 의도입니다. |

## A를 한다면 (설계에 필요한 사실)

- **새 형식을 만들 필요가 없을 수 있습니다.** 엔진의 평가 기능에 이미 결정적인 채점 실행기가 있습니다. 평가 사례의 `checks`(`http` POST·PUT, 응답 값 저장 `save`, JSON 단정, `dbQuery`)를 엔진이 LLM 없이 실행합니다(`runtime.eval`). 검증 Agent가 완료 기준마다 이 형식으로 확인 항목을 적고, 다시 검증할 때는 TOOL step이 그대로 돌리는 구조를 생각할 수 있습니다.
- **화면 동작이 남습니다.** 전체선택이나 팝업처럼 화면에서만 확인되는 것은 Playwright 스크립트를 실행하는 범용 Tool이 새로 있어야 합니다. 이때 스크립트에 비밀번호가 들어가지 않게, 실행기가 `${secret:…}`을 바꿔 넣어야 합니다.
- **위험도 있습니다.**
  - 지금 모델(qwen3.8-27b)이 쓴 스크립트가 얼마나 안정적일지 모릅니다.
  - 화면이 바뀌면 스크립트가 깨집니다. 그때는 LLM이 다시 맡아야 합니다.
  - 무엇보다 스크립트가 틀리면 틀린 판정이 매번 똑같이 반복됩니다. 첫 회차에 Agent가 화면에서 직접 본 판정은 지금처럼 유지하는 것이 전제입니다.

A는 검증 단계의 구조를 바꾸는 일이라, 곧바로 구현하기보다 설계안을 먼저 정리해 드리는 것이 좋겠습니다. 설계안을 만들까요?

---

## 질문

일단 여기서 멈추고 추후에 논의해보자.

---

## 답변

네, 여기서 멈추겠습니다. 다음에 바로 이어서 논의할 수 있도록 측정 근거와 설계의 출발점을 메모리에 적어 두었습니다.

오늘 반영한 것은 두 가지이고, 모두 원격 `main`에 올라가 있습니다.
- `a46c27d3`: Tool 결과에 섞인 비밀값을 `****` 대신 자리표시자로 되돌려 가림
- `51a63677`: sm-workflow 2.7.0, 설계 승인에서 검증 수준(화면 검증·기동만 확인·컴파일만)을 고름

---

## 질문

나는 아직도 conf/eval 과 Agent 평가 메뉴에 대해서 용도 및 사용법을 모르겠어.
명확하고 자세하고 친절한 설명 부탁해.

---

## 답변

평가 기능의 실제 코드와 문서를 확인한 뒤 설명하겠습니다.

---

## 답변

실제 사례 파일 하나와 화면 구성, 지난 평가 결과도 확인하겠습니다.

---

## 답변

Agent 평가 기능을 처음부터 순서대로 설명하겠습니다. 실제 사례 파일과 지금 DB에 쌓인 결과를 예로 들겠습니다.

---

# Agent 평가(`conf/eval`과 "Agent 평가" 메뉴)

## 1. 한 줄 요약

**Agent용 시험지입니다.** 실제로 Agent가 틀렸던(또는 잘했던) 일을 그대로 떠서 시험 문제로 만들어 둡니다. 그리고 프롬프트·Skill·모델을 고친 뒤 같은 문제를 다시 풀게 해서, 정말 나아졌는지 점수로 확인합니다.

## 2. 왜 필요한가

Agent를 고칠 때 가장 흔한 함정은 이렇게 흘러가는 것입니다.

> 실행이 실패함 → 프롬프트에 한 줄 추가 → 다음 실행이 우연히 잘 됨 → "고쳐졌다"고 믿음

LLM은 같은 입력에도 매번 다르게 답합니다. 그래서 한 번 잘 됐다고 고쳐진 것이 아닙니다. 고친 한 줄이 다른 일을 망가뜨렸을 수도 있습니다. 평가 기능은 이 질문들에 숫자로 답하기 위해 있습니다.
- 고치기 전 정의로 같은 문제를 3번 풀면 몇 점이었나? (기준선)
- 고친 뒤 3번 풀면 몇 점인가?
- 시간·Tool 호출·토큰은 늘었나 줄었나?

**`src/test`의 단위 테스트와 무엇이 다른가**

| | 단위 테스트 (`mvn test`) | Agent 평가 |
|---|---|---|
| 재는 것 | 엔진이 제대로 도는지(흐름, 분기, Tool) | **Agent가 일을 해내는지** |
| LLM | 미리 정해 둔 가짜 답 | 실제 LLM 호출 |
| 결과 | 매번 같음 | 매번 다름 → 여러 번 돌려 평균 |
| 두는 곳 | `src/test` (git) | `conf/eval` (현장에만) + DB |

## 3. 구성 요소 세 가지

```
① 사례(시험지)   conf/eval/{프로젝트ID}/<id>.eval.yml + 자료 폴더
② 실행(시험)     화면 "Agent 평가" → 사례 고르고 [평가 시작]
③ 결과(성적표)   DB AI_EVAL_RUN → 같은 화면의 결과 목록·비교 표
```

### 사례가 git에 없는 이유
사례에는 대상 앱의 실제 요청서·설계서·소스가 들어갑니다. 판매용 제품 저장소에 고객 앱의 자료를 넣으면 안 되므로, 각 현장에서만 만들어 씁니다(`.gitignore`의 `/conf/eval`). 지금 이 PC에는 `conf/eval/testApp/`에 사례 2개가 있습니다.

## 4. 사례 파일 읽는 법 (실제 사례로)

`conf/eval/testApp/verify-imageboard-reverify.eval.yml`입니다. 지난 11-23 실행에서 검증 Agent가 실수했던 장면을 뜬 것입니다.

```yaml
id: verify-imageboard-reverify          # 사례 이름(파일 이름과 같게)
description: 이미지 게시판 재검증 - 지난번 된 업로드를 환경 탓으로 돌리지 않는지, ...
agent: sm-verifier-agent                # 시험 볼 Agent
fixture: testApp/verify-imageboard-reverify   # 자료 폴더

params:                                 # 환경마다 다른 값(화면에서 바꿀 수 있음)
  sourceDir: D:/AppHome/testApp
  projectId: testApp

input:                                  # 그때 Agent가 받았던 입력을 그대로 재현
  base_dir: ${workDir}                  #   엔진이 만든 일회용 작업 폴더
  requirement: ${fixture:input/requirement.md}   # 자료 폴더의 파일 내용
  design: ${fixture:input/design.md}
  previous: ${fixture:input/previous.txt}
  ...

checks:                                 # 채점 기준(항목마다 통과/실패)
  - name: 업로드를 실제로 함
    toolUsed: {name: browser_file_upload, min: 1}
  - name: 썸네일 기준 PASS
    answerMatches: {field: scenarios, pattern: 'task=[^=]*썸네일 클릭((?!task=).)*result=PASS'}
  - name: 미확인인 것을 PASS로 내지 않음
    answerNotMatches: {field: scenarios, pattern: 'result=PASS((?!task=).)*미확인'}
```

자료 폴더 구조는 이렇습니다.
```
conf/eval/testApp/verify-imageboard-reverify/
├── input/      ← Agent 입력으로 넣을 글(요구사항, 설계서, 지난 결과…)
└── work/       ← 작업 폴더로 복사될 파일(그 시점의 개발 결과 dev-resources)
```

**자주 쓰는 채점 종류**

| 종류 | 뜻 | 예 |
|---|---|---|
| `answerContains` / `answerMatches` | Agent 답에 이 글자(정규식)가 있다 | 원인에 `mb_id`가 들어 있다 |
| `answerNotMatches` | 답에 이런 모양이 **없다** | "미확인"인데 PASS로 낸 기준이 없다 |
| `toolUsed` | 이 Tool을 N번 이상 썼다 | 업로드를 실제로 해 봤다 |
| `fileChanged` / `fileContains` / `fileNotMatches` | 작업 폴더의 파일을 고쳤다 / 이 내용이 있다 / 이런 모양이 없다 | 설계서에 수정 이력이 남았다 |
| `http` / `dbQuery` | 결과물을 시험 서버에 띄워 API·DB로 확인 (Workflow 사례에서) | 등록 API가 200이고 DB에 1건 |

## 5. 화면 사용법

왼쪽 메뉴 **"Agent 평가"**(`/ai/admin/eval/eval.html`)에서 합니다.

1. **평가 사례 표**: `conf/eval` 아래 사례가 모두 보입니다(사례 / 대상 / 무엇을 재나 / 채점 항목). 파일을 고치면 엔진을 다시 띄우지 않아도 바로 반영됩니다. 사례 파일에 오류가 있으면 그 사유가 목록에 표시됩니다.
2. **사례를 고르고 [평가 시작]**: 필요하면 params(소스 경로 등), 모델, **반복 횟수(기본 3, 최대 5)**를 바꿉니다. 실제 LLM을 부르므로 시간과 비용이 듭니다. 검증 사례는 한 번에 8~10분 걸립니다.
3. **평가 결과 표**: 회차마다 상태와 채점(예: 3/4), 시간, Tool 호출 수가 보입니다. 행을 누르면 **평가 상세**에서 항목별 통과 여부, Agent의 답, 호출한 Tool 목록을 볼 수 있습니다. 작업 폴더는 `LOGS/dstone-ai-engine/eval/` 아래 남아 있어 파일로도 확인할 수 있습니다.
4. **비교 표**: (사례, Agent 버전, 모델)마다 묶어 **채점 평균, 전부 통과한 횟수, 시간 평균, Tool 평균, 입력 토큰 평균**을 보여 줍니다. 고치기 전 버전과 고친 버전이 한 줄씩 나란히 나오므로, 그 차이가 곧 개선 효과입니다.

지금 DB에는 이런 기록이 있습니다(예).
```
troubleshoot-delete-db | 1.2.0 | 1회
troubleshoot-delete-db | 1.7.0 | 12회
design-imageboard      | 2.1.0 | 9회
```
Agent의 `version`을 올려 두어야 비교 표에서 고치기 전과 후가 다른 줄로 나뉩니다.

## 6. 실제 작업 순서

```
① 실행에서 Agent가 틀린 것을 발견
② 실패를 분류(엔진/입력/Tool/Workflow/Skill/모델/프롬프트)  ← 프롬프트 수정은 맨 마지막
③ 그 장면을 사례로 뜬다(작업 폴더와 로그는 곧 바뀌므로 바로)
     - Agent 입력: 실행 로그의 REQ 로그 / state
     - 작업 폴더: 그 시점의 dev-resources
     - checks: "이번에 틀린 것"을 잡는 항목
④ 고치기 전 정의로 3번 → 기준선. 이때 사례가 실제로 실패해야 함
     (고치기 전에도 통과하는 사례는 효과를 가를 수 없음)
⑤ 정의·Skill·엔진을 고치고 Agent version을 올림
⑥ 같은 사례로 3번 → 비교 표에서 기준선과 비교
⑦ 다른 사례 점수도 떨어지지 않았는지 확인
```

## 7. 오늘 실행(`daa7c276`)에 적용하면

앞서 "검증 Agent가 빈 팝업을 PASS로 냈다"는 실패를 프롬프트 수정 대신 사례로 남기자고 말씀드린 것이 바로 이 기능입니다. 다음과 같은 모양이 됩니다(`conf/eval/testApp/`, git 제외).

```yaml
id: verify-imageboard-popup
agent: sm-verifier-agent
fixture: testApp/verify-imageboard-popup     # 10:58 첫 검증 때의 입력과 dev-resources
checks:
  - name: 썸네일 팝업 기준을 PASS로 내지 않음(그때 결과물은 팝업이 비어 있었음)
    answerNotMatches: {field: scenarios, pattern: 'task=[^=]*썸네일 클릭((?!task=).)*result=PASS'}
```

이 사례가 지금 정의로 3번 중 몇 번 틀리는지 재 두면, 나중에 모델이나 검증 방식을 바꿨을 때 이 실수가 줄었는지 숫자로 볼 수 있습니다.

## 8. 흐름 전체를 재는 Workflow 사례

`agent:` 대신 `workflow: sm-workflow`를 적으면 요청서부터 흐름 전체를 돌립니다. 승인은 사례에 적어 둔 결정(`approvals: {step13: 승인(개발진행)}`)으로 자동으로 넣고, 최종 결과물을 격리 서버에 띄워 `http`·`dbQuery`로 채점합니다. "한 Agent를 고친 효과가 최종 결과물까지 닿는가"를 볼 때 씁니다. 실행은 일반 실행 목록에도 요청자 `eval:…`로 남고, 끝나면 `CANCELLED`로 닫힙니다.

## 9. 알아 둘 점

- **반드시 여러 번** 돌립니다. 1번의 결과로는 판단하지 않습니다.
- 시험 서버를 쓰는 사례는 Tomcat·Maven·Playwright가 있는 엔진(지금은 Windows 쪽)에서만 돕니다. 데이터는 일회용 복제본 DB에만 씁니다.
- 엔진이 내려가면 돌던 평가는 이어지지 않고 실패로 정리됩니다.
- 채점 정규식은 틀리기 쉽습니다. 기준선을 돌리기 전에 틀린 산출물과 맞는 산출물 양쪽에 대 보고 맞는지 확인합니다.

---

더 자세한 형식은 `docs/09.dstone-ai-engine.md` 15장 "Agent 평가"와 `runtime.eval.EvalCase` 주석에 있습니다. 원하시면 7번의 오늘 사례를 실제로 떠서 같이 한 번 돌려 보는 것으로 사용법을 익히셔도 좋겠습니다.

---

## 질문

Workflow 실행 관리 > 진행 기록 에 [Agent 평가추가] 라는 버튼을 두어서 자동으로 등록되게 할 수는 없나?

---

## 답변

구현할 수 있는지 판단하려면, 평가 사례에 들어갈 재료가 실행 기록에 남아 있는지부터 봐야 합니다. 확인하겠습니다.

---

## 답변

네, 만들 수 있습니다. 다만 버튼 하나로 완성되는 부분과 사람이 채워야 하는 부분이 나뉘고, 엔진 쪽에 준비가 하나 필요합니다. 사례 하나를 만드는 데 필요한 재료가 지금 실행 기록에 남아 있는지 확인해 봤습니다.

## 사례 재료가 지금 남아 있는가

| 사례에 필요한 것 | 지금 남아 있나 | 비고 |
|---|---|---|
| 어느 Agent·버전인지 | ✅ 진행 기록(step 이력, 정의) | |
| 그때 Agent가 받은 입력 | ✅ LLM 요청 로그(REQ)의 첫 사용자 메시지가 입력 JSON 그대로입니다 | 로그 파일이라 보관 기간이 지나면 사라집니다 |
| **그때의 작업 폴더 파일** | ❌ 파일 경로만 남습니다(`AI_WORKFLOW_EXECUTION_FILE`) | 뒤 step이 같은 파일을 고치므로, 지금 폴더는 그때와 다릅니다 |
| 무엇이 틀렸는지(채점 기준) | ❌ 사람만 압니다 | 승인 의견은 참고가 될 뿐입니다 |
| 환경 값(소스 경로, 프로젝트) | ✅ 입력과 프로젝트 관리에서 고를 수 있습니다 | |

가장 큰 문제는 **작업 폴더**입니다. 예를 들어 오늘 실행의 첫 검증(10:58) 때 `dev-resources`는 전문가 Agent가 고치기 전 상태였습니다. 그런데 지금 폴더에는 이미 고친 코드가 들어 있습니다. 지금 버튼을 만들어 이 폴더를 뜨면 "고쳐진 코드로 검증하는" 엉뚱한 사례가 됩니다.

## 그래서 필요한 것: 엔진의 Step 스냅숏 (범용)

Agent step이 실행되는 동안, 그 step이 **처음 읽거나 쓰는 순간의 파일 내용**을 실행 기록 아래에 복사해 둡니다(처음 건드릴 때 한 번만 복사). 입력 JSON도 같은 곳에 저장해 로그 보관과 상관없이 남게 합니다.
- 특정 Workflow나 앱을 알 필요가 없습니다. 파일 Tool은 이미 모두 `StepRef`로 경로를 기록하고 있으므로, 그 자리에서 내용을 복사하면 됩니다.
- 비용과 보관: 설정으로 켜고 끕니다. step 하나의 용량에 상한을 두고, 실행 기록을 지울 때 함께 지웁니다.
- **한계:** 이 기능을 넣은 뒤의 실행부터만 됩니다. `daa7c276` 같은 지난 실행은 입력만 살릴 수 있고 파일은 살릴 수 없습니다.

## 버튼의 동작 (진행 기록 → Agent step 행 → [Agent 평가 추가])

1. 대화상자가 열리고 다음 값이 미리 채워집니다.
   - 사례 id(`agent-step-날짜` 모양), 프로젝트(프로젝트 관리 목록에서 선택)
   - 설명: 그 step 바로 다음에 사람이 내린 결정과 의견. 예: "썸네일을 클릭하면 이미지가 보이지 않음"
   - 입력: 스냅숏의 입력 JSON. 작업 폴더로 보이는 경로는 `${workDir}`로, 소스 경로는 `${param.sourceDir}`로 바꿔 둡니다. 어느 입력이 작업 폴더인지는 엔진이 추정하고 사람이 확인합니다.
2. **채점 기준은 사람이 적습니다.** 많이 쓰는 모양(`answerNotMatches`, `toolUsed`, `fileContains` …)을 골라 채우는 편집기를 둡니다. LLM이 채점식 초안을 만드는 방식도 가능하지만, 채점식이 틀리면 평가 전체가 무의미해지므로 처음에는 넣지 않는 것을 권합니다.
3. 저장하면 `conf/eval/{프로젝트ID}/<id>.eval.yml`과 자료 폴더(`input/`, `work/`)가 그 엔진의 `conf/eval`에 만들어지고, 바로 "Agent 평가" 목록에 나타납니다.

**덤: Workflow 사례는 지금도 대부분 자동으로 만들 수 있습니다.** 실행 단위에 [Workflow 평가 추가]를 두면 요청서 폴더와 결정 이력(`decisions` → `approvals: {step13: 승인(개발진행), …}`)만 있으면 됩니다. 이 둘은 이미 남아 있습니다. 채점(`http`·`dbQuery`)만 사람이 적습니다.

## 정리

| 단계 | 내용 | 크기 |
|---|---|---|
| ① 엔진 Step 스냅숏 | 입력 JSON + 처음 건드릴 때의 파일 내용 | 중간(엔진, 설정, 보관 정리) |
| ② 진행 기록의 [Agent 평가 추가] | 대화상자(미리 채움 + 채점 편집기) → 사례 저장 API | 중간(화면과 API) |
| ③ 실행 단위의 [Workflow 평가 추가] | 결정 이력 → `approvals` | 작음(①과 상관없이 가능) |

진행하신다면 ①을 먼저 해야 ②가 의미가 있습니다. 어디까지 진행할지 정해 주세요.

---

## 질문

This session is being continued from a previous conversation that ran out of context. The summary below covers the earlier portion of the conversation.

<system-reminder>
<tools-used-before-summary>Bash, Read, Write</tools-used-before-summary>
</system-reminder>
Summary:
1. **Primary Request and Intent**
   - Analyze run `daa7c276-66e8-48f1-9937-ab89d865f041` (Windows, sm-workflow) to find tuning points:
     - accuracy;
     - why the server was restarted many times;
     - why the expert Agent's login failed;
     - whether the time spent was unavoidable.
   - Standing rule (user stated it again, emphatically): "나는 이 솔루션을 testApp용으로 만들고 있지 않고 범용적인 목적으로 만들고 있다... 향후에도 수정제안을 할 때 꼭 기억해줬으면 해." Every proposal must be generic. Classify each one as general principle, project Skill (`skills/projects/{ID}/`, in git), or eval case (`conf/eval/{ID}/`, git-ignored).
   - ToolCallGate masking change: done and pushed.
   - Hot-deploy and skipping restarts: user only checked necessity ("아냐 단지 필요성만 점검했을 뿐이야"). Do not implement.
   - Verification level chosen at design approval, with re-runs always using screen verification: done and pushed.
   - External review: verifier script-replay idea (A) is on hold, "추후에 논의".
   - Explain `conf/eval` and the Agent 평가 menu: done.
   - **Latest question:** "Workflow 실행 관리 > 진행 기록 에 [Agent 평가추가] 라는 버튼을 두어서 자동으로 등록되게 할 수는 없나?"

2. **Key Technical Concepts**
   - dstone-ai-engine: Workflow → Step → Agent → Tool model.
   - sm-workflow:
     - TOOL step `routes` on `containsValue` (true/false) and `countUp` ({route: within|over, count, scope, message}).
     - Server hand-over to the next step only, so no step may sit between step29 and step35.
   - `TestServerTool.startSandboxServer`:
     - Each start clones the DB, applies DDL, runs `mvn clean package` and starts Tomcat.
     - Reuse requires the same execution, a matching fingerprint and the next step.
     - A second call within the same step always starts a fresh server.
   - `ToolCallGate`: `${secret:ref}` placeholders are substituted only for `dstone.ai.tool.secret.allowed-tools` (`browser_type`, `browser_fill_form`).
   - Playwright `fill` replaces the field's value; `pressSequentially` (`slowly:true`) appends to it.
   - Eval feature:
     - Cases: `conf/eval/{projectId}/<id>.eval.yml` plus a fixture folder (`input/`, `work/`).
     - `runtime.eval.EvalRunner`, results in table `AI_EVAL_RUN`, screen `/ai/admin/eval/eval.html`.
     - Check types: `answerContains`, `answerMatches`, `answerNotMatches`, `toolUsed`, `fileChanged`, `fileContains`, `fileNotMatches`, `http`, `dbQuery`, and others.
     - A Workflow case (`workflow:`) uses `approvals`, `snapshotAfter` and `server`.
   - Per-turn LLM stats are logged by `AgentCallStatsAdvisor`.
   - Coding rules:
     - Java without lambdas or method references.
     - No Lombok.
     - Javadoc descriptions wrapped in `<pre>`.
     - Friendly Korean comments.
     - All responses to the user in Korean.
     - Edit files only under `/app/dstone`.
     - Commit and push only when asked.

3. **Files and Code Sections**
   - **`dstone-ai-engine/src/main/java/net/dstone/ai/runtime/tool/ToolCallGate.java`** (modified, commit a46c27d3)
     - `usedSecrets` changed from a `Set` to `Map<String,String>` (value → placeholder).
     - `remember(value, m.group())` is called for both the raw and the JSON-escaped value.
     - `mask()` sorts secrets longest first (anonymous `Comparator`) and replaces each with its placeholder.
     - The `MASK` constant was removed; imports `Collections`, `Comparator` and `Map` added.
     - Javadoc now explains the reason (Agent can tell a doubled entry; run of 2026-11-24).
   - **`dstone-ai-engine/src/test/java/net/dstone/ai/runtime/tool/ToolCallGateSecretTest.java`** (new)
     - 3 tests, all passing:
       - the secret is substituted for the Tool and the placeholder comes back in the result;
       - a doubled secret shows as two placeholders;
       - a placeholder is refused for Tools not on the allowed list.
     - Uses `ReflectionTestUtils` to set `configProperty` (a `ConfigProperty` with `env`), `toolCallChecks` and `secretSource`.
   - **`dstone-ai-engine/conf/application.yml`** — secret comment changed to "결과에 섞인 비밀값은 자리표시자로 되돌려 가린다."
   - **`docs/09.dstone-ai-engine.md`**
     - Secret paragraph updated.
     - §11.1 header now reads 2.7.0.
     - Mermaid diagram gets an SV decision node, an "TOOL 격리 기동만" node and the new step13 label.
     - New bullet "검증 수준은 설계 승인에서 고른다(2.7.0)".
   - **`.claude/docs/ai-engine-features.md`** — secret masking now described as placeholder restore.
   - **`.claude/docs/ai-engine-history.md`** — 2026-11-24 entry for sm-workflow 2.7.0.
   - **`dstone-ai-engine/conf/definitions/workflows/sm/sm-workflow.yml`** (version 2.6.1 → 2.7.0)
     - Header comment section "검증 수준 고르기(2.7.0)".
     - step13 routes added: `승인(개발진행, 기동만 확인): step14` and `승인(개발진행, 컴파일만): step14`.
     - step28 `next: step42`.
     - step42 `countUp` (previous `${state.verifyRound:}`, max 1, save `verifyRound`): within → step43, over → step41, onFailure → step41.
     - step43 `containsValue` (`${state.designApproval.decision:}` == `승인(개발진행, 컴파일만)`): true → step19, false → step44.
     - step44 `containsValue` (== `승인(개발진행, 기동만 확인)`): true → step45, false → step41.
     - step45 `startSandboxServer` (same args as step29, save `sandboxCheck`/`sandboxError`): next step19, onFailure step30.
     - `state:` adds `verifyRound`; the `sandboxCheck` description mentions step45.
   - **`sm-workflow.layout.json`** — 4 new node positions inserted textually (step42 1360,700; step43 1360,780; step44 1360,860; step45 1626,860).
   - **`dstone-ai-engine/conf/definitions/agents/sm/sm-architect-agent.md`** (2.1.0 → 2.2.0)
     - New `[검증 방법 제안]` section offering 화면 검증 / 기동만 확인 / 컴파일만 with criteria; if in doubt, choose 화면 검증.
     - The design document list now includes "검증 방법 제안".
   - **`dstone-ai-engine/src/test/java/net/dstone/ai/workflow/sm/SmVerifyLoopTest.java`**
     - Updated `프로젝트ID가_있으면…` to step through step42–44 to step41.
     - Added 4 tests:
       - compile-only → step19;
       - start-only → step45;
       - step45 start failure → step30;
       - second pass → step41.
     - All pass. The skipped `자동_수정…` test was already skipped before this change.
   - **Memory**
     - `feedback_generic-tools-not-workflow-specific.md` gained the 2026-11-24 "수정 제안 자체도 같다" paragraph; `MEMORY.md` line updated.
     - New `project_verifier-script-replay-pending.md` plus an index line.
   - **Read only:**
     - `TestServerTool.java` (reuse rules at lines 85–108 and 325–345; `clean` at 1263).
     - `sm-verifier-agent.md` (requiredTools lines 64–67; line 96 "PASS: 기대한 결과를 직접 봤다").
     - `conf/eval/testApp/verify-imageboard-reverify.eval.yml`.
     - docs/09 chapter 15 "Agent 평가".
     - `ai-engine-agent-principles.md` section 4.
     - The REQ log format: the USER message text is the full Agent input as JSON, e.g. `{"base_dir":"D:/WorkShop/...","source_dir":...,"requirement":"..."}`.

4. **Errors and fixes**
   - Rewriting the layout JSON with `json.dumps` reformatted the whole file (a 2070-line diff). I restored it with `git checkout` and inserted the nodes textually.
   - Awk window extraction overran because the end pattern never matched. I restricted the window by line number instead.
   - A ugrep complexity error came from regexes with Korean text. I used Python instead.
   - User correction: my proposal "검증 Skill에 팝업 내용 확인" was too testApp-shaped. I withdrew it because the rule already exists; this is a model failure, and the case belongs in `conf/eval`.

5. **Problem Solving**
   - Solved:
     - Login retries and failures (placeholder masking).
     - Excess server and browser testing on changes that don't need it (verification level).
   - Explained but not implemented:
     - Hot-deploy options: copy static/JSP files only, reload the Tomcat context, HotSwap/DCEVM/JRebel (rejected).
     - Restart optimization.
   - Outstanding accuracy findings, reported only:
     - The verifier's false PASS.
     - The troubleshooter's backslash misdiagnosis and the possibly misleading REPLACE DML it added.
     - The original testApp DB already contains `tbts_image_board` and its menu row.

6. **All user messages**
   - (Pasted) "dstone-ai-engine Workflow 실행 관리 를 진행 중이야. <확인 요청> Windows 에서 진행. 실행 ID : daa7c276-66e8-48f1-9937-ab89d865f041 이 실행아이디를 분석해서 튜닝포인트를 잡고 싶어. 1. 결과(정확도) - 적어도 이 실행은 성공적으로 끝났어. 2. 성능(속도) - 여러번 서버를 내렸다 올렸다 해야 하는 이유를 알려줘. - 전문가 에이전트가 로그인에 실패한 이유를 알려줘. - 이렇게 시간이 소요되는게 불가피한가?"
   - (Pasted) "전에도 이야기 했지만 꼭 기억할 것은 나는 이 솔루션을 testApp용으로 만들고 있지 않고 범용적인 목적으로 만들고 있다라는 거야. 이 점은 향후에도 수정제안을 할 때 꼭 기억해줬으면 해. 1. 검증 Skill... ==>> 이 내용은 testApp에 종속된 내용이 아닌가 싶은데, 네 생각은 어때? 2. ToolCallGate의 비밀값 가림 방식 변경 ==>> 진행해. 3. startSandboxServer가 같은 step에서 다시 띄울 때 DB 재복제와 clean을 생략 ==>> 이럴 때의 영향도가 어떻게 되? * 이렇게 여러번 서버를 내렸다 올리는 이유가 java파일이 수정될 때마다 재기동 해야해서 하는 거지? hot-deploy 방법은 없는건가?"
   - "아냐 단지 필요성만 점검했을 뿐이야."
   - "진행된 내용은 올려줘."
   - "지금은 어떤 변경건이라도 테스트서버를 올리고 브라우저테스트를 진행하지?"
   - "여기에 어떤 차별성을 둘 수 없을까? 예를 들자면, 너무 확실해서 격리/테스트서버테스트를 진행하지 않아도 되는 요청건에 대해서는 서버 테스트를 skip 한다던지,"
   - "다시 돌 때는 화면검증으로 진행하는걸로 정하고 진행해."
   - "응 올려줘."
   - "서버테스트 관련해서 클로드에 질의 한 결과인데 여기서 우리가 참고할 내용이 있을까? /temp/review/클로드에서의테스트.txt"
   - "일단 여기서 멈추고 추후에 논의해보자."
   - "나는 아직도 conf/eval 과 Agent 평가 메뉴에 대해서 용도 및 사용법을 모르겠어. 명확하고 자세하고 친절한 설명 부탁해."
   - "Workflow 실행 관리 > 진행 기록 에 [Agent 평가추가] 라는 버튼을 두어서 자동으로 등록되게 할 수는 없나?"

7. **Pending Tasks**
   - Answer the [Agent 평가추가] button feasibility question. This is an analysis and proposal; nothing has been approved for implementation.
   - On hold (do not start without the user): verifier script-replay design (A).
   - Open question I raised earlier, still unanswered: should a compile failure that goes to step19 and comes back count as the first verification (current behaviour) or as a re-run?

8. **Current Work**
   - I was investigating feasibility for: "Workflow 실행 관리 > 진행 기록 에 [Agent 평가추가] 라는 버튼을 두어서 자동으로 등록되게 할 수는 없나?"
   - **What can be recovered today**
     - Agent input map: the REQ log's USER message is the full input JSON. Log files live on the engine host under `LOGS/dstone-ai-engine/llm/<date>/<workflow>/<execId>/<time>-<stepId>-<agent>-REQ.log`.
     - Workflow-level cases: the `decisions` list (step → decision) can become `approvals`, and `input` gives the request folder.
   - **What cannot be recovered**
     - The work folder files as they were at that step. They have changed since; `AI_WORKFLOW_EXECUTION_FILE` stores paths only, and the fileChanges snapshot stores hashes only.
     - `checks`: what counts as wrong needs a human. The decision comment can prefill the description, and the checks could at most be drafted.
   - **Proposal I was forming**
     - Generic engine capture from now on: save each AGENT/SUPERVISOR step's evaluated input map, plus copy-on-first-touch copies of the files the step reads or writes, kept in an execution archive.
     - The button opens a dialog:
       - case id and description, prefilled from the following decision comment;
       - project chosen from project management;
       - the user picks which input field is the work folder, and it is rewritten to `${workDir}`;
       - a checks editor with templates.
     - Saving writes to `conf/eval/{projectId}/` on the engine host.
     - Only runs made after the capture feature ships would fully support this; old runs (e.g. daa7c276) could be only partially rebuilt from the REQ log.
     - A Workflow-case button at the execution level is closer to feasible today.

9. **Optional Next Step**
   - Reply in Korean to "Workflow 실행 관리 > 진행 기록 에 [Agent 평가추가] 라는 버튼을 두어서 자동으로 등록되게 할 수는 없나?":
     - Yes, it is possible with conditions: what is recoverable now (input JSON from the REQ log, approvals from decisions) versus what is not (the file state at that time, checks).
     - The generic capture proposal, the dialog design, and the limits (only new runs fully; checks need a human).
     - Keep everything generic, with no testApp-specific logic.
   - Ask whether to proceed. Do not implement before approval.

If you need specific details from before compaction (like exact code snippets, error messages, or content you generated), read the full transcript at: /home/jysn007/.claude/projects/-app-dstone/3a56a34a-8d89-493e-966a-f142fb9b866b.jsonl
Continue the conversation from where it left off without asking the user any further questions. Resume directly — do not acknowledge the summary, do not recap what was happening, do not preface with "I'll continue" or similar. Pick up the last task as if the break never happened.

The messages after this summary are the most recent messages from before compaction, kept verbatim. The summary was written without seeing them, so something it says has not happened yet may already have happened in them.

---

## 질문

스탭 스탭샷이 저장될 디렉토리는 어디가 된다는건가?

---

## 답변

아직 정한 곳은 없습니다. 지금 있는 관례에 맞춰 이렇게 두는 것을 제안합니다.

## 제안 위치

```
{APP_HOME}/LOGS/dstone-ai-engine/snapshot/{workflowId}/{executionId}/{stepId}-{회차}/
  ├─ input.json      ← 그 step의 Agent가 받은 입력(평가식 적용이 끝난 값)
  ├─ manifest.json   ← 원래 경로 → 보관 경로 목록, 읽기/쓰기 구분, 크기, 해시
  └─ files/…         ← 처음 건드리는 순간의 파일 내용
```

- 위치는 설정 `dstone.ai.workflow.snapshot.root`로 바꿀 수 있게 하고, 비어 있으면 위 기본값을 씁니다. 평가 작업 폴더의 기본값 `dstone.ai.eval.work-root` → `{APP_HOME}/LOGS/dstone-ai-engine/eval`(`EvalRunner.java:525`)과 같은 방식입니다.
- 실제 경로 예시는 다음과 같습니다.
  - Windows: `D:/AppHome/framework/dstone/LOGS/dstone-ai-engine/snapshot/sm-workflow/daa7c276-…/step30-1/`
  - WSL: `/app/dstone/LOGS/dstone-ai-engine/snapshot/…`

## 이렇게 정한 이유

- **엔진이 실행 중에 만드는 파일은 모두 `{APP_HOME}/LOGS/dstone-ai-engine/` 아래에 있습니다.** LLM 요청 로그(`…/llm/`)와 평가 작업 폴더(`…/eval/`)가 그렇습니다. 스냅숏도 같은 성격이라 옆에 둡니다.
- **날짜 폴더는 넣지 않습니다.** LLM 로그는 `llm/yyyyMMdd/…` 모양이지만, 스냅숏은 실행 ID로 찾고 지웁니다. 실행 기록을 지울 때 `{executionId}` 폴더 하나만 지우면 되고, 날짜를 넘겨 이어지는 실행도 한곳에 모입니다.
- **`{stepId}-{회차}`로 나눕니다.** 되감기나 검증 루프로 같은 step이 여러 번 돌면 회차마다 파일 상태가 다르기 때문입니다.
- **원래 경로는 `manifest.json`에 적습니다.** `files/` 아래에는 기준 폴더(작업 폴더, 소스 폴더) 기준의 상대 경로로 둡니다. Windows 드라이브 문자(`D:`)나 절대 경로가 그대로 폴더 이름이 되지 않게 하려는 것입니다.

## 운영할 때 주의할 점

| 환경 | 확인할 것 |
|---|---|
| Windows/WSL(VM형) | `LOGS`를 정리하는 스크립트가 있다면 `snapshot`은 빼거나 보관 기간을 따로 정해야 합니다 |
| k8s(Pod) | `LOGS`가 Pod 안에만 있으면 재배포할 때 사라집니다. 영구 볼륨을 붙이거나 `snapshot.root`를 볼륨 경로로 지정해야 합니다 |
| 용량 | step당 상한(예: 50MB)을 두고, 넘으면 입력만 남기고 파일은 "생략"으로 manifest에 적습니다 |

`LOGS` 아래가 내키지 않으시면 `{APP_HOME}/DATA/dstone-ai-engine/snapshot`처럼 로그와 떼어 두는 방법도 있습니다. 로그 정리 정책과 섞이지 않는 대신, 새 최상위 폴더가 하나 생깁니다. 저는 설정으로 바꿀 수 있는 한 기존 관례인 `LOGS` 아래를 기본값으로 두는 쪽을 권합니다.

---

## 질문

이 기능의 효과에 대해서 조금 더 자세하게 설명해줘.

---

## 답변

한 줄로 말하면, 이 기능은 **"실제 실행에서 틀린 장면"을 그대로 보관해 두는 장치**입니다. 이것만으로 Agent가 더 정확해지거나 빨라지지는 않습니다. 대신 Agent를 고칠 때 **고친 효과를 증거로 확인**할 수 있게 해 줍니다.

## 1. 평가 사례를 만드는 일이 짧아집니다

지금 Agent 실수 하나를 평가 사례로 만들려면 다음을 사람이 직접 해야 합니다.

| 작업 | 지금 | 스냅숏이 있으면 |
|---|---|---|
| Agent가 받은 입력 찾기 | LLM 로그 폴더를 뒤져 REQ 파일에서 JSON 복사 | `input.json`이 이미 있음 |
| 그때의 작업 폴더 만들기 | 뒤 step이 파일을 고쳤으므로 git 이력이나 기억으로 되돌려야 함. 사실상 어려움 | `files/`를 복사하면 끝 |
| 경로를 `${workDir}`로 바꾸기 | 손으로 | 대화상자에서 확인만 |
| 채점 기준(`checks`) | 손으로 | 손으로(그대로) |

가장 어려운 부분은 **"그때의 작업 폴더"를 되살리는 일**입니다. 지금은 이 일이 너무 번거로워서 사례가 잘 쌓이지 않습니다. 스냅숏이 있으면 사람은 "무엇이 틀렸나"를 적는 일에만 집중하면 됩니다.

## 2. 사례가 실제 실패를 그대로 재현합니다

스냅숏이 없으면 지금 폴더로 사례를 만들게 되고, 그러면 **다른 상황을 시험하는 사례**가 생깁니다.
- 예를 들어 검증 Agent가 "고치기 전 코드"를 보고 잘못 PASS했다고 해 봅시다. 지금 폴더에는 이미 고친 코드가 있으므로 그 사례는 항상 PASS가 정답이 됩니다.
- 그러면 평가 점수는 좋게 나오지만, 실제로 고쳐진 것은 아무것도 없습니다. 측정하지 않는 것보다 나쁜, **거짓 안심**입니다.

스냅숏은 Agent가 실제로 마주했던 입력과 파일을 그대로 주므로, 평가가 바로 그 실패를 다시 시험합니다.

## 3. 프롬프트·Skill·모델을 바꿀 때 근거가 생깁니다

CLAUDE.md의 원칙은 "실패를 분류하고, 고친 뒤 기준선과 비교한다"입니다. 문제는 비교할 사례가 적다는 것입니다. 사례가 쌓이면 다음과 같이 쓸 수 있습니다.

- **고친 것이 맞는지:** 프롬프트를 고치기 전에 관련 사례로 기준선을 잡고, 고친 뒤 다시 돌려 점수를 비교합니다.
- **다른 곳이 깨지지 않았는지(회귀):** 한 실수를 막으려고 넣은 문장이 다른 사례를 망가뜨리는 일은 흔합니다. 쌓인 사례 전체를 돌리면 이것이 드러납니다.
- **모델 교체 판단:** qwen과 다른 모델을 비교할 때 일반 벤치마크가 아니라 **우리 Workflow에서 실제로 틀렸던 장면**으로 비교합니다. 폐쇄망 POC에서 모델을 고르는 근거로 가장 설득력이 있습니다.
- **횟수 측정:** LLM 답은 매번 달라지므로 한 번 돌려서는 판단하기 어렵습니다. 같은 사례를 여러 번 돌려 "10번 중 몇 번 맞는가"로 비교할 수 있습니다.

## 4. 평가가 아니어도 실패 분석이 빨라집니다

지금 진행 기록에는 step이 건드린 **파일 경로와 해시만** 남습니다. 스냅숏이 있으면 다음을 바로 볼 수 있습니다.
- **그 step이 무엇을 보고 판단했나:** 원인 분석 Agent가 잘못 진단했다면 실제로 읽은 파일 내용을 확인할 수 있습니다. 입력이 부족했는지(입력 문제), 보고도 틀렸는지(모델·프롬프트 문제) 가를 수 있습니다. 실패 분류의 첫 단계가 근거를 갖게 됩니다.
- **누가 무엇을 바꿨나:** step N의 "처음 건드린 내용"과 step N+1의 것을 비교하면, 어느 step이 어떤 파일을 어떻게 바꿨는지 알 수 있습니다.
- **로그 보관 기간과 상관없이:** LLM 로그가 지워져도 입력과 파일은 실행 기록과 함께 남습니다.

## 5. 범용 제품으로서의 효과

- 각 현장이 **자기 프로젝트의 사례를 `conf/eval/{프로젝트ID}/`에 직접 쌓습니다.** 우리가 현장 소스를 받아 오지 않아도 현장마다 회귀 시험 세트가 생깁니다. git에서 빼는 정책과도 맞습니다.
- 현장에서 Agent 정의나 Skill을 고칠 때(예: 프로젝트 Skill 추가) 그 현장의 사례로 효과를 확인할 수 있습니다. 결과적으로 "고치고 감으로 판단하는" 운영이 "고치고 점수로 판단하는" 운영으로 바뀝니다.

## 이 기능이 하지 않는 것

- **채점 기준은 여전히 사람이 적어야 합니다.** 버튼을 누르는 사람이 없으면 효과도 없습니다.
- **Agent 정확도나 속도를 바로 올리지는 않습니다.** 측정 기반이고, 개선은 그 위에서 따로 합니다.
- **비용:** 디스크를 쓰고, step마다 처음 건드리는 파일을 한 번 복사합니다. 소스 파일 수준이라 크지는 않을 것으로 보지만, 상한과 켜고 끄기 설정을 둡니다.
- **보안:** 작업 폴더에 설정 파일이 있으면 그 내용도 복사됩니다. 저장 위치는 지금의 작업 폴더나 LLM 로그와 같은 수준(엔진 호스트)이지만, 보관 기간이 그만큼 길어지므로 실행 기록을 지울 때 함께 지워지도록 합니다.

정리하면, 지금까지는 Agent를 고친 효과를 "다음 실행에서 잘 되나 보자"로 확인했습니다. 이 기능이 있으면 **"전에 틀렸던 장면 N개 중 몇 개를 이제 맞히나"**로 확인할 수 있습니다.

---

## 질문

checks에 대한 내용이 조금 더 상세해야 될 것 같은데... 어떻게 적어야 할지 헷갈릴것 같아.

---

## 답변

checks를 적을 때 헷갈리는 이유는 대개 **"무엇을 적어야 하는지"가 아니라 "어디를 봐야 하는지"**를 모르기 때문입니다. 순서대로 정리해 보겠습니다.

## 1. checks란 무엇인가

checks는 **"이번 실행이 맞았는지 기계가 판단할 수 있는 문장"**의 목록입니다.
- 하나가 판정 하나이고, 결과는 통과 또는 실패뿐입니다.
- 사례 점수는 `통과 수 / 전체 수`(예: 3/4)이고, Agent 평가 화면에서 실행끼리 이 점수를 비교합니다.
- `name`에는 사람이 읽을 **기대하는 모습**을 적습니다. 예: "직접 보지 않은 것을 PASS로 내지 않음".

## 2. 적는 순서 (4단계)

**① 사람이 본 문제를 한 문장으로 적습니다.**
- 예: "검증 Agent가 확인하지 않은 기준을 PASS로 냈다."
- 진행 기록의 승인 의견이 대부분 이 문장입니다(대화상자에 미리 채워집니다).

**② 그 잘못이 어디에 흔적을 남겼는지 찾습니다.** checks가 볼 수 있는 곳은 네 군데뿐입니다.

| 흔적이 남는 곳 | 예 | 쓰는 종류 |
|---|---|---|
| **Agent의 답**(output 칸) | 판정 결과, 원인 목록, 시나리오 | `answer…` |
| **작업 폴더의 파일** | 설계서, 소스, 문서 | `file…`, `textIncreased` |
| **Tool을 부른 기록** | DB를 조회했는지, 업로드를 실제로 했는지 | `toolUsed` |
| **결과물의 실제 동작** | API 응답, DB에 저장된 행 | `http`, `dbQuery`(격리 시험 서버 필요) |

**③ "맞는 모양이 있다"와 "틀린 모양이 없다" 가운데 고릅니다.**
- 정답이 하나로 정해지면 **있다** 쪽(`answerEquals`, `fileMatches`)을 씁니다.
- 정답은 여러 가지인데 틀린 모양이 분명하면 **없다** 쪽(`answerNotMatches`, `fileNotMatches`)을 씁니다. LLM은 같은 정답도 매번 다르게 쓰기 때문에, 실제로는 이쪽이 더 자주 맞습니다.
- `Not…` 종류는 답이나 파일이 **아예 없으면 실패**로 칩니다. "아무것도 안 해서 통과"하는 일은 없습니다.

**④ 기본 확인 1~2개와 핵심 확인 1~3개로 구성합니다.**
- 기본 확인은 산출물이 있는지입니다(`fileExists`, `answerNotEmpty`). 이것이 실패하면 나머지 점수는 의미가 없습니다.
- 핵심 확인은 ①의 문제를 직접 잡는 것입니다. 사례 하나에 핵심은 3개 이하가 좋습니다. 많아지면 무엇이 좋아졌는지 흐려집니다.

## 3. 종류별 설명

### Agent의 답을 보는 것
`field`는 Agent 정의의 output 칸 이름입니다. 생략하면 답 전체를 봅니다.

| 종류 | 언제 | 예 |
|---|---|---|
| `answerNotEmpty` | 그 칸을 채웠는지만 | `answerNotEmpty: causes` |
| `answerEquals` | 값이 하나로 정해질 때(true/false, 상태값) | `{field: compiled, value: true}` |
| `answerContains` | 꼭 언급해야 할 단어가 있을 때 | `{field: causes, text: NullPointer}` |
| `answerMatches` | 모양이 맞는 곳이 있어야 할 때(정규식) | `{field: scenarios, pattern: '…result=PASS'}` |
| `answerNotMatches` | 나오면 안 되는 모양이 있을 때 | `{field: scenarios, pattern: 'result=PASS((?!task=).)*미확인'}` |

**주의할 점:** 칸이 목록이나 맵이면 `{task=기준1, result=PASS, evidence=…}, {task=기준2, …}` 같은 글자로 바뀐 뒤 비교합니다.
- 그래서 "어느 항목 안에서" 찾을지 범위를 막아야 합니다.
- 위의 `((?!task=).)*`는 "다음 항목(`task=`)으로 넘어가지 않고"라는 뜻입니다. 이것이 없으면 1번 항목의 PASS와 3번 항목의 "미확인"이 이어져 잘못 걸립니다.

### 작업 폴더의 파일을 보는 것
경로는 작업 폴더 기준입니다.

| 종류 | 언제 | 예 |
|---|---|---|
| `fileExists` | 만들어야 할 파일 | `fileExists: 02.impact.md` |
| `fileChanged` | 고쳐야 할 파일(처음 내용과 달라졌나) | `fileChanged: src/…/A.java` |
| `fileContains` | 꼭 들어가야 할 글자 | `{file: 03-1.design.md, text: TASK-01}` |
| `fileMatches` / `fileNotMatches` | 있어야 할 모양 / 없어야 할 모양(정규식, 여러 줄 걸침) | `{file: …, pattern: '…'}` |
| `textIncreased` | 여러 파일에 걸쳐 어떤 처리가 늘었나 | `{files: [a.java, b.java], text: setUserId}` |

### 행동을 보는 것
- `toolUsed: {name: dbQuery, min: 1}`은 "추측하지 말고 실제로 조회했는가"를 봅니다.
- 이름은 앞부분만 맞으면 됩니다. `browser_`라고 적으면 브라우저 Tool 전체가 해당합니다.
- **답이 맞아도 과정이 틀린 경우**(보지 않고 맞힌 경우)를 잡을 때 씁니다.

### 결과물의 동작을 보는 것
사례에 `server:` 값이 있어야 하고, 채점할 때 격리 서버를 띄웁니다(몇 분 걸립니다).
```yaml
- {name: 등록, http: {method: POST, path: /api/items, body: {title: a}, status: 200, save: {itemId: id}}}
- {name: 저장됨, dbQuery: {sql: "SELECT COUNT(*) FROM item WHERE title='a'", equals: "1"}}
- {name: 조회, http: {path: "/api/items/${saved.itemId}", json: {title: a}}}
```
- 적은 순서대로 채점하므로 앞 요청이 만든 값을 뒤에서 `${saved.이름}`으로 씁니다.
- 주로 **Workflow 사례**(개발 결과 전체가 동작하는가)에 씁니다.

## 4. 증상별로 무엇을 고르나 (빠른 표)

| 사람이 본 증상 | 먼저 떠올릴 종류 |
|---|---|
| 파일을 안 만들었다 / 안 고쳤다 | `fileExists`, `fileChanged` |
| 문서나 코드에 잘못된 모양이 들어갔다 | `fileNotMatches` |
| 꼭 넣어야 할 처리가 빠졌다 | `fileContains`, `fileMatches`, `textIncreased` |
| 판정(PASS/FAIL/BLOCKED)이 틀렸다 | `answerMatches` / `answerNotMatches`(항목 범위 제한) |
| 원인을 엉뚱하게 짚었다 | `answerContains`(꼭 나와야 할 원인 단어) + `answerNotMatches`(틀린 원인) |
| 확인하지 않고 추측했다 | `toolUsed` |
| 만든 기능이 실제로 동작하지 않는다 | `http`, `dbQuery` |

## 5. 좋은 check인지 확인하는 기준

1. **그때의 실패 결과로 채점하면 실패해야 합니다.** 실패를 못 잡는 check는 쓸모가 없습니다. 가장 많이 하는 실수입니다.
2. **올바르게 고친 답이라면 표현이 달라도 통과해야 합니다.** 문장 전체를 그대로 비교하면 LLM이 표현만 바꿔도 실패합니다. 의미를 가르는 최소한의 단어나 모양만 봅니다.
3. **환경 값을 직접 적지 않습니다.** 경로는 `${workDir}`이나 `${param.…}`으로 적고, 비밀번호는 넣지 않습니다.
4. **사례마다 위에 주석으로 "그때 무슨 일이 있었나"를 남깁니다.** 몇 달 뒤 점수가 바뀌었을 때 이 주석이 판단의 근거가 됩니다.

## 6. 버튼에서 이 부분을 어떻게 도울까 (설계 보완)

1번 기준("그때의 실패로 돌리면 실패해야 한다")을 대화상자에서 바로 확인할 수 있게 하려면, 스냅숏에 두 가지를 더 남겨야 합니다.
- **`output.json`**: 그 step의 실제 답
- **`tools.json`**: 그 step이 부른 Tool 목록
- **끝난 뒤의 파일**: 그 step이 쓴 파일만, step이 끝났을 때의 내용

이것이 있으면 대화상자의 **[그때 결과로 미리 채점]** 버튼이 LLM을 부르지 않고 몇 초 만에 각 check의 결과를 보여 줍니다. "모두 실패로 나와야 정상"이라는 안내를 붙입니다. 그리고 증상 유형(4절의 표)을 고르면 해당 종류의 틀이 채워지게 해서, 처음 쓰는 사람도 빈칸만 채우면 되게 합니다.

이 설명은 `docs/09`의 15장(Agent 평가)에 "checks 작성법"으로 넣어 두는 것이 좋겠습니다. 지금 15장에는 종류 목록만 있고 고르는 방법은 없습니다. 문서에 반영할까요?

---

## 질문

이 내용이 입력화면에서 직관적으로 내용을 넣을 수 있을만큼 축약적으로 가이드가 되어야 제대로 쓰일 것 같아.

---

## 답변

맞습니다. 입력 화면에서는 긴 설명을 읽게 하기보다 **질문에 답하고 고르기만 하면 check가 만들어지는 구성**이 맞습니다. 앞의 설명은 문서(`docs/09` 15장)에만 두고, 화면에는 아래 정도만 보이게 하면 됩니다.

## 화면 구성안: check 한 줄 추가

```
┌ 채점 기준 추가 ─────────────────────────────────────────────┐
│ ① 무엇이 잘못됐나요?                                        │
│   ○ 파일을 안 만들었다·안 고쳤다                             │
│   ○ 파일에 잘못된 내용이 들어갔다                            │
│   ○ 꼭 들어가야 할 내용이 빠졌다                             │
│   ● 판정·답이 틀렸다                                        │
│   ○ 확인하지 않고 추측했다                                   │
│   ○ 만든 기능이 동작하지 않는다 (시험 서버 필요)              │
│                                                            │
│ ② 어디를 볼까요?   답의 칸 [scenarios ▼]   ← output 칸 목록   │
│                                                            │
│ ③ 어떤 모양이면?   ○ 있어야 함  ● 없어야 함                  │
│   찾을 글자 [result=PASS ] … [미확인 ]  (두 글자 사이 아무거나) │
│   ☑ 항목 하나 안에서만 찾기                                  │
│   □ 정규식으로 직접 쓰기                                     │
│                                                            │
│ 이름  [직접 보지 않은 것을 PASS로 내지 않음        ]          │
│                                                            │
│ ▶ 읽어 보면: "답의 scenarios 칸의 한 항목 안에 'result=PASS'  │
│   뒤에 '미확인'이 나오면 안 된다"                             │
│                                                            │
│ [그때 결과로 미리 채점]  ✗ 실패 — 정상입니다(그때 실수를 잡음) │
│                                         [취소]  [추가]      │
└────────────────────────────────────────────────────────────┘
```

## 핵심 장치

| 장치 | 하는 일 |
|---|---|
| **① 증상 고르기** | 종류 이름(`answerNotMatches` 등)을 몰라도 됩니다. 고른 증상에 따라 ②③의 칸이 바뀝니다 |
| **② 목록에서 고르기** | 답의 칸은 Agent output 정의에서, 파일은 스냅숏에서 그 step이 쓴 파일 목록에서, Tool은 그때 부른 Tool 목록에서 가져옵니다. 직접 타이핑하는 일이 거의 없습니다 |
| **③ 글자 칸 + 체크박스** | "A … B"(사이 아무거나)와 "항목 하나 안에서만" 체크로 정규식을 대신 만들어 줍니다(`((?!task=).)*` 같은 것을 사람이 몰라도 됨). 숙련자만 "정규식으로 직접 쓰기"를 켭니다 |
| **▶ 읽어 보면** | 만들어진 check를 한국어 한 문장으로 되돌려 보여 줍니다. 의도대로 만들어졌는지 사람이 확인하는 곳입니다 |
| **미리 채점** | 스냅숏에 남은 그때 결과로 바로 채점합니다(LLM 호출 없음). **✗가 나와야 정상**이고, ✓가 나오면 "이 기준은 그때의 실수를 잡지 못합니다"라고 경고합니다 |

## 화면에 둘 안내 문구 (전부 한 줄)

- 맨 위: **"그때의 실수는 잡고, 올바른 답은 통과시키는 기준을 1~3개 적으세요."**
- "없어야 함"을 고르면: "답·파일이 아예 없어도 실패로 칩니다."
- 글자 칸: "문장 전체 말고, 틀린 것을 가르는 짧은 글자만 적으세요."
- 미리 채점 ✓일 때: "그때 결과도 통과합니다. 기준이 너무 느슨합니다."
- 저장 전 기본 확인이 없으면: "산출물이 있는지(파일 있음·답 칸 채움) 확인을 하나 넣을까요? [넣기]"

## 사례 전체 화면

```
사례: verify-xxx-20261124   프로젝트 [ ▼ ]   설명 [승인 의견에서 채움]
채점 기준
  ✓ 기본  결과 파일이 있음                    그때 ✓     [수정][삭제]
  ● 핵심  직접 보지 않은 것을 PASS로 내지 않음  그때 ✗     [수정][삭제]
  [+ 기준 추가]                         [YAML 보기]  [저장]
```
- "그때" 열이 **기본은 ✓, 핵심은 ✗**이면 좋은 사례입니다. 이 규칙 하나만 알면 됩니다.
- YAML은 숨겨 두고, 필요한 사람만 열어 봅니다.

## 범위 정리

이 화면을 만들려면 앞에서 말한 스냅숏에 **답(`output.json`), 부른 Tool(`tools.json`), step이 끝났을 때 쓴 파일**도 남겨야 합니다. 그래야 ②의 목록과 미리 채점이 됩니다. `http`·`dbQuery`는 미리 채점할 수 없으므로(서버 필요) 화면에서 "저장 후 평가 실행에서 확인"으로 표시합니다.

진행 순서는 다음을 권합니다.
1. 엔진 스냅숏(입력, 처음 건드린 파일, 답, Tool 목록, 끝난 뒤 파일)
2. 진행 기록의 [Agent 평가 추가]와 이 check 편집기
3. 실행 단위의 [Workflow 평가 추가]

어디까지 진행할지 정해 주세요.

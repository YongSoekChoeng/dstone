# localcommandcaveatTh

> 생성일시: 2026-11-13 17:02

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-boot>knowledge>검색 · 문서 화면에서
프로젝트:HI-Japan
찾을 내용:"보험료계산을 수정하려면?" 라는 질문을 했을 때 나온 대답이 정상인가?

---

## 답변

먼저 화면이 부르는 API를 찾아 같은 질문을 그대로 던져 보겠습니다.

---

## 답변

같은 질문을 API로 다시 던져 결과를 재현했고, 지금은 HI-Japan 소스에서 실제 "보험료 계산" 코드가 어디 있는지 대조하는 중입니다.

---

## 답변

확인이 거의 끝났습니다. 임베딩 진행률만 보고 정리하겠습니다.

---

## 답변

## 결론: 정상적인 답이 아닙니다

검색 기능은 설계대로 동작했습니다. 다만 지금은 임베딩이 1.4%만 끝난 상태여서, 그 좁은 범위 안에서 가장 비슷해 보이는 엉뚱한 코드를 돌려준 것입니다.

같은 조건(HYBRID, 10건, 코드)으로 API를 직접 불러 결과를 재현했습니다.

### 나온 결과
1위부터 3위가 `JCBT9100B#updateJcBenefit_1` 계열로, 유사도는 0.60입니다. 그런데 이 메소드의 주석은 **"클레임_보험금 오류정정 update"** 입니다.
- 질문은 **보험료**(premium)인데 결과는 **보험금**(claim)입니다. 업무상 전혀 다른 개념입니다.
- "수정하려면"이 "오류정정 update"와 뜻이 가깝다고 계산돼 점수가 올라간 것으로 보입니다.
- 나머지도 모두 `batch/` 아래 정정이나 마감 배치입니다.

### 원인
1. **임베딩이 거의 안 끝났습니다.** HI-Japan 청크 16,843개 중 252개만 끝났고 16,591개가 대기 중입니다. 끝난 것도 `batch/src`와 `workshop/` 일부뿐이고, 온라인 쪽 `was/`는 0건입니다. 현재 속도로는 약 7.8시간이 더 걸립니다.
2. **이름 검색이 빠져 있었습니다.** 이름 검색은 질문에 들어 있는 영문 식별자로만 합니다. 한글만 있는 질문에서는 고를 이름이 없어서 HYBRID가 VECTOR로 바뀌었습니다(응답의 `keywords: []`, `mode: VECTOR`).

### 실제로 찾아야 했을 후보
소스를 직접 grep해 보니 아래 코드가 더 관련 있어 보입니다. 모두 아직 `PENDING`이라 검색에 걸릴 수 없었습니다.
- `was/WEB-INF/src/hi/jt/jz/service/JZCT0108M.java`: 클래스 주석이 "보험료산출에 활용되는 항목값 조회"
- `hi/jt/ji/entity/JICT0009VO`: `premRtCalcMachnCntRyCd`(보험료율 계산 관련 코드)
- 화면 `was/ui/jr/JRCD2002G.xml:993`, `JRCD2003G.xml:399`(수수료와 정미보험료 계산), `was/ui/ji/JICC0300G.xml`(회차보험료 재계산)

### 지금 할 수 있는 것
- 임베딩이 끝난 뒤 같은 질문으로 다시 검색해 보세요. 진행 상황은 Knowledge 첫 화면의 "임베딩 현황"에서 볼 수 있습니다.
- 그 전에는 질문에 영문 이름을 넣으면 이름 검색이 됩니다. 예를 들어 `보험료 계산 Prem` 또는 `JZCT0108M`처럼 넣으면 임베딩 없이도 결과가 나옵니다.

한 가지 짚어 둘 점이 있습니다. 임베딩이 1.4%뿐인데도 화면에는 결과 10건이 확신 있는 모양으로 나옵니다. 메시지 줄에 `임베딩 DONE 252 / PENDING 16591`이 표시되기는 하지만 눈에 잘 띄지 않습니다. 대기 비율이 높으면 "아직 대부분 검색 대상이 아닙니다" 같은 경고를 띄우도록 고칠 수 있는데, 원하시면 손보겠습니다.

---

## 질문

dstone-knowledge 어플리케이션 자체를 분석하려고 할 때 참조 라이브러리 위치를 아래 하나로 해도 상관 없나?
/app/dstone/dstone-knowledge/target/dstone-knowledge.jar
내부에 필요한 라이브러리들 다 있는 것 같던데...

---

## 답변

## 안 됩니다. 그 jar 하나로는 라이브러리 타입을 거의 못 찾습니다

`dstone-knowledge.jar` 안에 라이브러리가 다 들어 있는 것은 맞습니다. `BOOT-INF/lib/` 아래에 jar가 307개 있습니다. 문제는 분석기가 이 구조를 읽지 못한다는 점입니다.

분석기(`ResolvePass.java:183`)는 클래스패스에 적힌 jar마다 JavaParser의 `JarTypeSolver`를 하나씩 만듭니다. `JarTypeSolver`는 jar의 맨 위에 있는 `.class`만 읽고, 경로를 그대로 패키지 이름으로 씁니다. 그래서 Spring Boot fat jar에서는 이렇게 됩니다.

| jar 안의 내용 | 분석기가 보는 모습 | 결과 |
|---|---|---|
| `BOOT-INF/lib/*.jar` (Spring, MyBatis 등 307개) | jar 안의 jar라서 열지 않음 | **못 찾음** |
| `BOOT-INF/classes/net/dstone/...` | `BOOT-INF.classes.net.dstone...`라는 엉뚱한 패키지 | 못 찾음. 어차피 소스로 분석되는 부분이기도 함 |
| `org/springframework/boot/loader/...` | 정상 | Boot 런처 클래스뿐이라 쓸모없음 |

따라서 이 jar 하나만 적으면 jar가 하나도 없을 때와 결과가 거의 같습니다. Spring, MyBatis, `dstone-common` 쪽 호출이 대부분 풀리지 않고, 프로젝트 요약의 호출 해석률이 낮게 나올 것입니다.

참고로 프로젝트 폴더를 `/app/dstone/dstone-knowledge`로 잡으면 폴더 안의 jar를 자동으로 모으기 때문에, `target/dstone-knowledge.jar`는 따로 적지 않아도 이미 들어갑니다.

## 이렇게 하세요

`ClasspathResolver` 주석에 적힌 방법대로 Maven이 뽑아 주는 클래스패스를 그대로 넣는 것이 가장 정확합니다.

```bash
cd /app/dstone/dstone-knowledge
mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout
```

출력된 `:`로 이어진 긴 문자열을 프로젝트의 클래스패스 칸에 그대로 붙여 넣으면 됩니다. `~/.m2`에 있는 `dstone-common` jar도 같이 들어갑니다.

경로를 짧게 두고 싶다면 jar를 한 폴더에 풀어 두고 그 폴더 하나만 적는 방법도 있습니다.

```bash
mvn -q dependency:copy-dependencies -DoutputDirectory=/app/dstone/dstone-knowledge/target/dependency
```

이 폴더는 프로젝트 폴더 안에 있어서 클래스패스에 적지 않아도 자동으로 잡힙니다. 다만 `mvn clean`을 하면 지워진다는 점을 기억해 두세요.

원하시면 fat jar 안의 `BOOT-INF/lib/*.jar`를 풀어서 읽도록 `ClasspathResolver`를 고칠 수도 있습니다. 배포된 Spring Boot 산출물만 있고 빌드 환경이 없는 프로젝트를 분석할 때 쓸모가 있습니다.

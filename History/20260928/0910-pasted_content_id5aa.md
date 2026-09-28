# pasted_content_id5aa

> 생성일시: 2026-09-28 09:10

## 질문

<pasted_content id="5aac">
testApp-sdlc.yml 를 보면 
아래와 같이 따옴표가 붙는 부분이 있고 
input: "{{inputs.message}}" 
아래와 같이 따옴표가 없는 부분도 있어.
input: |
   [승인된 분석/설계서]
   {{steps.analyze.text}}
   ...
이런 것들이 직관적으로 이해하는데 햇갈리게 해.

이런 혼란요소를 근본적으로 제거할 방법이 없을까?
나는 개인적으로 {{ }} 가 붙는 설정이 맘에 들지 않아.
이 기법을 회피할 수 있는 다른 방법이 없을까?
</pasted_content id="5aac">

---

## 답변

`testApp-sdlc.yml`과 `Template.java`, 샘플 Workflow들을 확인했습니다. `{{ }}`를 아예 없애는 방법이 있습니다.

## 따옴표가 들쭉날쭉한 원인
YAML은 값이 `{`로 시작하면 맵으로 읽습니다. 그래서 `input: "{{inputs.message}}"`처럼 한 줄로 쓸 때는 따옴표가 꼭 필요합니다. 반대로 `|` 블록 안에서는 `{`가 그냥 글자라서 따옴표가 없어도 됩니다. 결국 **"경로를 가리키는 표시"와 "YAML 문법"이 같은 문자(`{`)를 쓰기 때문에** 생기는 문제입니다. 표시 기호를 무엇으로 바꾸든 이런 종류의 규칙은 어딘가에 남습니다.

## 제안: 기호 대신 "키 이름"으로 구분하기
이 엔진에는 이미 이렇게 동작하는 곳이 있습니다. `forEach: inputs.sqlList`는 중괄호도 따옴표도 없는데 경로로 읽힙니다. **키 자체가 "여기엔 경로가 온다"를 알려주기 때문**입니다. 이 원칙을 전체로 넓히는 안입니다.

- `input`: 항상 **있는 그대로의 글자/값**. 엔진이 절대 해석하지 않음
- `inputFrom`: 항상 **컨텍스트 경로**. 표시 기호가 필요 없음
- 경로 대신 리스트를 쓰면 fallback: `[previous.output.sql, inputs.message]` (지금의 `??`를 대체)

`testApp-sdlc.yml`에 적용하면 이렇게 됩니다.

```yaml
    - id: analyze
      type: AGENT
      ref: testApp-analysis-agent
      inputFrom: inputs.message            # 경로 하나 → 그 값이 그대로 LLM 메시지

    - id: write-spec
      type: AGENT
      ref: testApp-spec-writer-agent
      inputFrom:                           # 키 = 제목, 값 = 경로
        승인된 분석/설계서: steps.analyze.text
        검수 의견: steps.design-review.output.comment
      # 엔진이 "[승인된 분석/설계서]\n...\n\n[검수 의견]\n..." 형태로 조립

    - id: trigger-jenkins
      type: TOOL
      ref: triggerBuild
      input:                               # 고정값
        jobName: testApp
```

TOOL 인자와 Workflow `output`도 같은 방식입니다.
```yaml
      inputFrom:
        sql: [previous.output.sql, inputs.message]   # fallback
  output: steps.extract.output.sql                   # Workflow output은 항상 경로
```

**좋아지는 점**
- `{{ }}`, `??`, 따옴표 규칙이 모두 사라집니다. 값이 경로인지 글자인지는 키만 보면 바로 알 수 있습니다.
- `input`은 해석하지 않으므로, 코드 생성 프롬프트처럼 본문에 `{{`가 들어가도 깨질 걱정이 없습니다.
- 지금처럼 기동할 때 경로를 전부 검증하는 것은 그대로 유지됩니다. 오타가 있으면 여전히 부팅이 실패합니다.

**잃는 것: 글자 사이에 경로를 섞는 것 한 가지**
현재 샘플 중 `sample-mcp-filesystem-list.yml`의 `path: "{{inputs.message}}/{{fileName}}"` 한 곳만 해당합니다. 두 가지 처리 방법이 있습니다.
1. 이어 붙이기를 Tool로 처리합니다. 지금 문서의 "가공이 필요하면 Tool로" 원칙과 같습니다. **(추천)**
2. `inputFrom`에 `join: [inputs.message, "/", fileName]` 같은 작은 문법을 추가합니다. 다만 경로와 글자를 다시 구분해야 하므로 원칙이 흐려집니다.

## 다른 방법(비추천)
`$steps.analyze.text`처럼 `$` 기호만 쓰는 방법도 있습니다. 따옴표 문제는 사라지고 수정 범위도 작습니다. 하지만 여전히 기호로 구분하는 방식입니다. 또 SQL의 `$1`이나 셸의 `$HOME`, 문장 끝의 마침표 같은 경계 문제가 새로 생겨서 근본적인 해결은 아닙니다.

## 수정 범위(진행 시)
- `Template`: 문자열 치환을 없애고 경로 해석과 fallback만 남김
- step 정의 record: `inputFrom` 추가
- `WorkFlowRegistry`: 예전 `{{ }}` 문법을 쓰면 기동을 막고 새 문법을 안내하는 메시지 추가
- YAML 전체 7개 파일
- `workflow-yml-guide.md`, `docs/09.dstone-ai-engine.md` §7/§8

이 방향(`input`/`inputFrom` 분리 + 글자 섞기는 Tool로 처리)으로 진행할까요? 키 이름(`inputFrom` 대신 `from` 등)이나 AGENT 여러 경로를 조립하는 모양(`[제목]` 형식)에 대한 선호가 있으면 알려주세요.

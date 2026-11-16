# localcommandcaveatTh

> 생성일시: 2026-11-17 08:08

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

클로드 skill 중에서 
전산 기획자, 설계자에게 가장 도움이 될 만한 skill이 뭘까?

---

## 답변

# 전산 기획자·설계자에게 쓸모 있는 skill

지금 이 환경에 설치된 skill만 놓고 보면, 하나만 고르라면 **`anthropic-skills:xlsx`**입니다. 국내 SI 산출물은 대부분 엑셀이기 때문입니다. 요구사항 정의서, 테이블·컬럼 정의서, 인터페이스 정의서, 화면 목록, WBS, 추적 매트릭스가 모두 여기에 해당합니다. 이 skill은 수식, 서식, 여러 시트를 갖춘 실제 `.xlsx` 파일을 만들거나 고칩니다. 반대로 지저분한 기존 엑셀을 정리하는 데도 쓸 수 있습니다.

## 역할별 추천

| 역할 | skill | 쓰임새 |
|---|---|---|
| 기획자 | `anthropic-skills:docs` | 기획서, 요구사항 명세, 회의록, 제안서. 함께 편집하고 댓글을 달 수 있으며 Word나 PDF로 내보낼 수 있음 |
| 기획자 | `anthropic-skills:pptx` | 보고 장표, 착수·중간·완료 보고 |
| 기획자 | `anthropic-skills:deep-research` | 솔루션·기술 비교, 시장 조사, 도입 검토 보고서 |
| 설계자 | `anthropic-skills:xlsx` | 위에서 말한 정의서류 산출물 |
| 설계자 | `artifact-diagramming` | 시스템 구성도, 데이터 흐름도, 시퀀스 다이어그램 |
| 공통 | `humanizer`, `grammar-checker`, `style-guide` | 설계서 문장 다듬기, 용어와 어조 통일 (여러 사람이 쓴 문서를 검토할 때 효과가 큼) |

## 길게 보면 가장 효과가 큰 것

**`anthropic-skills:skill-creator`**로 **회사나 프로젝트 표준 산출물 skill을 직접 만드는 것**을 권합니다. 범용 skill은 표준 양식을 모릅니다. 예를 들어 "우리 회사 테이블 정의서 양식, 명명 규칙, 공통 코드 체계"를 skill로 만들어 두면 매번 설명하지 않아도 그 양식대로 산출물이 나옵니다. 이 저장소에서 `friendly-comments`나 `java-no-lambdas`를 규칙 skill로 만들어 둔 것과 같은 방식입니다.

설치되지 않은 skill을 더 찾아보려면 `find-skills`로 검색할 수 있습니다(예: "ERD", "요구사항", "UML").

어떤 산출물을 주로 만드는지(요구사항 정의서, ERD, 화면 설계서 등) 알려 주시면 그에 맞는 skill 초안을 같이 만들어 드릴 수 있습니다.

---

## 질문

dstone-boot>knowledge 의 화면들에 대해서
고객에게 조금 더 어필할 수 있는 방향으로 수정을 하고 싶어. 
클로드 skill 중에서 이 수정미션을 위해 가장 도움이 될 만한 skill이 뭘까?

---

## 답변

# Knowledge 화면을 고객에게 더 돋보이게 고칠 때 쓸 skill

## 설치하면 가장 효과가 클 skill: `frontend-design`

이 목적에 가장 맞는 skill은 Anthropic 공식 skill인 **`frontend-design`**인데, 지금 이 환경에는 설치되어 있지 않습니다. 이 skill은 기존 화면을 "AI가 만든 듯한 평범한 관리자 화면"에서 벗어나게 다듬는 데 특화되어 있습니다. 주로 다루는 것은 타이포그래피, 색 체계, 여백, 시각적 위계, 첫인상입니다. 고객 시연용 화면 개선과 방향이 정확히 맞습니다. `find-skills`로 찾아서 설치하는 것부터 권합니다.

## 지금 설치된 것 중에서 고르면

| skill | 쓰임새 |
|---|---|
| **`dataviz`** (지금 있는 것 중 1순위) | Knowledge 첫 화면 대시보드의 통계 카드, 차트, 노드 맵처럼 데이터를 보여 주는 부분이 고객 눈에 가장 먼저 들어옵니다. 숫자 타일, 색 팔레트, 범례, 라이트·다크 모드 일관성을 체계적으로 잡아 줍니다. |
| `run` + `anthropic-skills:chrome-browser`(또는 `built-in-browser`) | 수정한 화면을 실제로 띄워 스크린샷으로 전후를 비교할 때 씁니다. 디자인 작업은 눈으로 확인해야 하므로 꼭 함께 쓰는 것이 좋습니다. 7081 포트는 Windows 쪽 인스턴스일 수 있으니 7181 같은 다른 포트로 띄웁니다. |
| `humanizer`, `style-guide` | 화면 문구, 안내 메시지, 용어(예: '진입점')를 자연스럽고 일관되게 맞출 때 씁니다. 고객은 문구 품질도 함께 봅니다. |

`artifact-design`도 디자인 지침이지만, 이 skill은 claude.ai Artifact 페이지용입니다. 그래서 dstone-boot의 JSP 화면에 그대로 적용하기는 어렵습니다.

## 추천 순서

1. `find-skills`로 `frontend-design`을 설치합니다.
2. `.claude/docs/knowledge-ui.md`에 적힌 현재 화면 구성(대시보드, 프로젝트 · 분석 2단, 탭)을 기준으로 개선 방향을 먼저 정합니다.
3. 대시보드와 차트는 `dataviz` 기준으로 다듬습니다.
4. 브라우저 스크린샷으로 수정 전후를 비교합니다.

원하시면 먼저 `frontend-design` 설치부터 진행하겠습니다. 아니면 현재 Knowledge 화면을 띄워 스크린샷을 찍고, 고객 시연 관점에서 약한 부분을 진단하는 것부터 시작해도 됩니다.

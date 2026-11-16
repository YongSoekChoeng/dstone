# localcommandcaveatTh

> 생성일시: 2026-11-17 08:00

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

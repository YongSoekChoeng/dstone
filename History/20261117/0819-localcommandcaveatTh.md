# localcommandcaveatTh

> 생성일시: 2026-11-17 08:19

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

---

## 질문

이 스킬을 설치하면 dstone 프로젝트에 한정하여 설치되는 것인가 ?

---

## 답변

아니요, **설치 방법에 따라 범위가 달라집니다.** 기본 방식으로 설치하면 dstone에만 한정되지 않고 모든 프로젝트에 적용됩니다.

## 설치 위치별 적용 범위

| 위치 | 적용 범위 | 현재 이 위치에 있는 것 |
|---|---|---|
| `/app/dstone/.claude/skills/` | **dstone 프로젝트에서만** 쓰입니다. git으로 관리되어 저장소와 함께 공유됩니다. | `humanizer`, `java-no-lambdas`, `respond-in-korean` 등 12개 |
| `~/.claude/skills/` | **이 사용자의 모든 프로젝트**에서 쓰입니다. | `find-skills` |

## `find-skills`로 설치할 때

`find-skills`의 안내(`~/.claude/skills/find-skills/SKILL.md:100`)는 다음 명령을 씁니다.

```bash
npx skills add <owner/repo@skill> -g -y
```

여기서 **`-g`는 전역(사용자 수준) 설치**라는 뜻입니다. 이대로 설치하면 `~/.claude/skills/`에 들어가 모든 프로젝트에 적용됩니다.

## dstone에만 설치하려면

- **`-g`를 빼고** `/app/dstone`에서 실행하면 프로젝트 수준으로 설치됩니다.
- `frontend-design`을 Claude Code 플러그인으로 설치하는 경우(`/plugin install`)에는 설치할 때 범위를 고를 수 있습니다. **project**를 고르면 dstone 저장소 설정에 기록되고, **user**를 고르면 전역으로 적용됩니다.

## 추천

화면 디자인 skill은 dstone에만 필요한 규칙이 아니라 다른 프로젝트에서도 쓸 수 있는 범용 도구입니다. 그래서 저는 **전역 설치**를 권합니다. 반대로 dstone에만 두고 싶거나 git으로 팀과 공유하려면 프로젝트 수준으로 설치하면 됩니다. 어느 쪽으로 할지 알려 주시면 그 방식으로 설치하겠습니다.

---

## 질문

dstone 에만 설치하는 방향으로 frontend-design 스킬을 설치해줘.

---

## 답변

Anthropic 공식 skill인 `anthropics/skills@frontend-design`을 dstone 프로젝트 범위로 설치하겠습니다. 전역 설치 옵션 `-g`는 빼고 Claude Code용으로만 지정합니다.

---

## 답변

`frontend-design` skill을 **dstone 프로젝트에만** 설치했습니다. 현재 세션에서도 이미 skill 목록에 올라와 있어 바로 쓸 수 있습니다.

`--help`로 옵션만 보려고 했는데 CLI가 그 자리에서 설치까지 해 버렸습니다. 결과는 원하신 범위와 같습니다. `~/.claude/skills/`에는 들어가지 않았고, 전역 설치는 일어나지 않았습니다.

## 설치된 것

| 경로 | 내용 |
|---|---|
| `.agents/skills/frontend-design/` | skill 본문(`SKILL.md`)과 `LICENSE.txt` |
| `.claude/skills/frontend-design` | 위 폴더를 가리키는 **심볼릭 링크**. Claude Code는 이 링크로 skill을 찾습니다. |
| `skills-lock.json` | 설치 출처(`anthropics/skills`)와 해시 기록 |

`SKILL.md`는 끝까지 읽어 확인했습니다. 실행 코드나 스크립트 없이 디자인 지침만 들어 있습니다. 주요 내용은 아래와 같습니다.
- AI가 만든 듯한 흔한 디자인 패턴을 피합니다.
- 코드를 쓰기 전에 색·서체·레이아웃 계획을 먼저 세우고 검토합니다.
- 화면 문구는 사용자 관점에서 씁니다.

## 정할 것 두 가지

1. **커밋 여부:** 위 세 항목은 아직 git에 올라가지 않은 상태입니다. 말씀하시면 커밋하고 푸시하겠습니다.
2. **심볼릭 링크:** Windows 쪽 클론(`D:/AppHome/framework/dstone`)이 git으로 동기화되는데, Windows git은 기본 설정에서 심볼릭 링크를 경로 문자열이 든 일반 파일로 받습니다. 그러면 Windows 쪽 Claude Code가 이 skill을 못 찾을 수 있습니다. Windows에서도 이 skill을 쓰시려면 링크를 지우고 `.claude/skills/frontend-design/`에 실제 파일을 복사하는 방식을 권합니다.

커밋할지, 링크를 실제 파일로 바꿀지 알려 주시면 그대로 진행하겠습니다.

---

## 질문

커밋해주고 링크를 실제 파일로 바꿔서 진행해줘.

---

## 답변

심볼릭 링크를 실제 파일로 바꾸고 커밋했습니다(`8eec1f84`). 푸시는 아직 하지 않았습니다.

## 바뀐 내용

- `.claude/skills/frontend-design/` 폴더에 `SKILL.md`와 `LICENSE.txt`를 실제 파일로 복사했습니다. 이제 Windows 쪽 클론에서도 git으로 받으면 그대로 쓸 수 있습니다.
- CLI가 만든 원본 폴더 `.agents/`는 지웠습니다. 남겨 두면 같은 내용이 두 군데에 있게 되어 나중에 한쪽만 고쳐질 수 있기 때문입니다.
- `skills-lock.json`은 설치 출처와 해시를 기록하려고 함께 커밋했습니다.

`.agents/`를 지웠기 때문에 `npx skills update`로 이 skill을 자동 업데이트하는 기능은 기대한 대로 동작하지 않을 수 있습니다. 나중에 새 버전이 필요하면 다시 설치한 뒤 같은 방식으로 복사하면 됩니다.

말씀하시면 푸시하겠습니다. 이어서 이 skill로 Knowledge 화면 개선 작업을 시작해도 됩니다. 먼저 현재 화면을 띄워 스크린샷을 찍고, 고객 시연 관점에서 약한 부분을 진단하겠습니다.

---

## 질문

올려줘. 그리고 해당 스킬을 올려서 화면 개선 방향에 대해서 검토하고 네 의견을 말해줘.

---

## 질문

Base directory for this skill: /app/dstone/.claude/skills/frontend-design

# Frontend Design

Approach this as the design lead at a design studio known for giving every client a distinct visual identity that is not mistaken for anyone else's. This client has already rejected proposals that felt cliché or templated, and is paying for a distinctive point of view: make deliberate, opinionated choices about palette, typography, and layout that are specific to this brief, and take aesthetic risk if justified.

## Ground your designs in the subject matter

If the brief does not identify what the product or subject matter is, identify it yourself before designing, and confirm with the client. You can come up with one concrete subject, the design's audience, and the design's primary job, as a proposal. If there's any information in your memory about the client's preferences or context about what they're building, use that as a hint. The subject's industry, subject matter, materials, and vernacular are where distinctive visual choices come from — a design for a toy for girls aged 8–11 will be very aesthetically different from a dashboard for financial analysts. Build with the brief's real content and subject matter throughout.

## Design principles

For web designs, the hero is the first thing viewers will see. Open with the most characteristic thing in the subject's world, in the form that is most appropriate: a headline, an image, an animation, a live demo, an interactive moment, or other treatments. Be deliberate with your choice: a big number with a small label, supporting stats, and a gradient accent is the default treatment, so only use it if that's truly the best option.

Typography carries the personality of the page. You don't need a different typeface for display or headline text and body content: use one family or two, and if two, make them clearly distinct.

Choose your typefaces deliberately, not the default families you would reach for on any other project, and set a clear type scale following the default guidance of The Elements of Typographic Style with intentional weights, widths, and spacing. When type is used as a headline or visual element, use the type treatment itself as an active part of the design, not a neutral delivery vehicle for the content.

Default to line lengths of less than 80 characters. Serif typefaces can have slightly longer line lengths; give serif body text slightly more line-height than a sans-serif.

Avoid these default typographic treatments; they are the commonest tells of a generated page:
- Accenting just a single word or phrase in a headline, like putting one word in italic/bold or a different color.
- Using all caps for labels.
- Adding unnecessary typographic labels above content.

Visual structure is information. Structural devices like outlines, borders, numbering, eyebrows, dividers, labels, etc., encode useful information about the content rather than decorate it. Many generic designs use numbered markers (01 / 02 / 03), but that's only appropriate if the content actually is a sequence — like a stepped process or a timeline. Before adding numbered markers, check the content really is a sequence.

Use non-user-triggered motion sparingly and deliberately, only to draw attention. A single orchestrated moment — one page-load sequence or one reveal — lands better than scattered effects; fade-and-slide-up entrances on each section and hover transitions on every card are the generic default and read as AI-generated. Motion that answers a person's action (opening, expanding, confirming) is welcome when it shows what changed.

Consider written content carefully. Often a design brief may not contain real content, and it's up to you to come up with copy and placeholder content. Copy can make a design feel as templated as the design itself. See the below section on writing for more guidance.

## Process: plan, review against the brief, build, critique

For calibration, AI-generated design right now clusters around some traits:
1. a warm cream background (near #F4F1EA) with a high-contrast serif display and a terracotta or warm-clay accent (often near #D97757 — Anthropic's own Claude-interaction accent, so on a user's brief it reads as a tell);
2. a near-black background with a single bright acid-green or vermilion accent;
3. a broadsheet-style layout with hairline rules, zero border-radius, and dense newspaper-like columns;
4. the SaaS-card kit: content chopped into identical rounded cards, one border-radius on everything regardless of hierarchy, the same soft grey shadow (rgba(0,0,0,.1)) under each, and gradient washes as decoration;
5. template chrome that appears whatever the subject: a tracked-out ALL-CAPS eyebrow label above every heading; meta strings joined with middle dots ('A · B · C'); labels built as 'WORD — fragment' with a spaced em dash; tinted near-black (#0B0B0B, #111) standing in for black; a monospace face for small data labels; a '→' appended to link and button text.

All traits are legitimate for some briefs, but they are defaults rather than choices, and they appear regardless of subject. Where the brief pins down a visual direction, follow it exactly — the brief's own words always win, including when it asks for one of these looks. Where it leaves an axis free, don't spend that freedom on one of these defaults. As with a hired human designer, there's often a careful balance between doing what you're good at and taking each project as a chance to experiment and learn.

Work in two passes. First, brainstorm a short design plan based on the client's design brief: create a compact token system with color, type, layout, and principles.
- Color: describe the core base palette as 4–6 named hex values.
- Type: the typefaces and their roles.
- Layout: a layout concept, using one-sentence prose descriptions and ASCII wireframes to ideate and compare. Include alignment guidance; should the content be left aligned, center aligned, justified?
- Principles: the high-level guidance for what makes this page unique.

Then review that plan against the brief before building: if any part of it reads like the generic default you would produce for any similar page (work through a similar prompt to see if you arrive somewhere similar) rather than a choice made for this specific brief — revise that part, say what you changed and why. Only after you've confirmed the relative uniqueness of your design plan should you start to write the code, following the revised plan.

When writing the code, be careful of structuring your CSS selector specificities. It's easy to generate CSS classes that cancel each other out (especially with a type-based selector like .section and an element-based selector like .cta). This can happen often with padding/margin between sections.

## Restraint and self-critique

Spend your boldness in one place. Let one element be the memorable thing, keep everything around it quiet and disciplined, and cut any decoration that does not serve the brief. Build to a quality floor without announcing it: responsive down to mobile, visible keyboard focus, reduced motion respected, visually accessible, harmonious color palettes. Critique your own work as you build, taking screenshots to review if your environment supports it — a picture is worth 1000 tokens. Consider Chanel's advice: before leaving the house, take a look in the mirror and remove one accessory. Human creatives have memory and always try to do something new, so if you have a space to quickly jot down notes about what you've tried, it can help you in future passes.

## More on writing in design

Words appear in a design for one reason: to make it easier to understand and use. They are design content, not decoration. Bring the same intentionality and minimalism to copywriting that you would bring to spacing and color. Before writing anything, ask what the design needs to say, and how it can best be said to help the person navigate the experience.

Write from the end user's perspective. Name things by what users will understand in simple language, not by how the system is built. A user manages notifications, not webhook config. Describe what something is or does in plain terms rather than selling it. Being specific and legible to new users is always better than being clever.

Use active voice as default. A CTA says exactly what happens when it is used: "Save changes," not "Submit." An action keeps the same name through the whole flow, so the button that says "Publish" produces a toast that says "Published." The vocabulary of an interface is the signposting for someone navigating the product. Cohesion and consistency are how people learn their way around.

Treat failure and emptiness as moments for direction, not mood. Explain what went wrong and how to fix it, in the interface's voice rather than a person's. Errors don't apologize, and they are never vague about what happened. An empty screen is an invitation to act.

Keep the tone conversational: plain verbs, sentence case, no filler, with tone matched to the brand and the audience. Let each written element do exactly one job.


ARGUMENTS: dstone-boot의 Knowledge 화면(코드 분석 → 지식 그래프 → RAG 플랫폼 UI)을 고객 시연에서 더 돋보이도록 개선하는 방향 검토

---

## 답변

푸시를 마쳤고 skill도 불러왔습니다. 이제 현재 Knowledge 화면의 구성과 스타일을 살펴보겠습니다.

---

## 답변

코드 구조는 파악했습니다. skill 지침대로 실제 화면을 눈으로 봐야 하니, 띄워진 인스턴스와 스크린샷 도구가 있는지 확인하겠습니다.

---

## 답변

Five shots at the same size is suspicious. Let me check what's in one.

---

## 답변

CSS가 안 붙고 한글 글꼴도 빠진 채로 찍혔습니다. 원인을 확인하겠습니다.

---

## 답변

대시보드와 노드 맵이 실제 데이터로 잘 나왔습니다. 나머지 세 화면도 보겠습니다.

---

## 답변

푸시를 마쳤습니다(`8eec1f84` → `origin/main`). `frontend-design` 지침에 따라 실제 데이터가 든 화면 5개(대시보드, 프로젝트 · 분석, 검색 · 문서, 영향도, 노드 맵)를 스크린샷으로 찍어 보고 검토했습니다.

- 화면은 로그인 없이 띄웠습니다. scratchpad에 임시 중계 서버를 올렸고, 다 찍은 뒤 껐습니다. 저장소 파일은 바꾸지 않았습니다.
- 스크린샷은 `/tmp/claude-1000/-app-dstone/23f71448-96db-46e1-9647-9408a4a40d82/scratchpad/shots/`에 있습니다.

## 한 줄 진단

지금 화면은 **잘 정리된 관리자 콘솔**입니다. 정렬, 간격, 색 토큰, 표가 모두 깔끔합니다. 다만 고객 눈에는 흔한 SaaS 관리 화면 템플릿처럼 보입니다. 짙은 남색 사이드바, 청록 버튼, 모두 같은 모양의 둥근 흰 카드, 제목마다 붙은 "코드 분석" 작은 라벨이 그렇습니다. skill이 "AI가 만든 화면 티가 나는 기본값"으로 꼽은 패턴과 거의 같습니다.

**이 제품만 보여 줄 수 있는 것은 따로 있는데, 지금은 표 안이나 입력 폼 뒤에 숨어 있습니다.** H-Energy의 화면 742개, 메소드 4만 개를 분석해 화면 → 컨트롤러 → 서비스 → SQL → 테이블로 엮은 결과가 그것입니다.

## 화면별 문제

1. **대시보드 첫 장면이 약합니다.** 맨 위의 같은 크기 숫자 카드 5개가 바로 skill이 말한 "기본 처리"입니다. 첫 카드는 서버 `UP`인데, 이건 운영자용 정보입니다. 게다가 주황색으로 강조된 카드의 값이 **"진행 중인 분석 0"**이라서, 고객 눈이 하필 0에 먼저 갑니다.
2. **글이 많습니다.** 모든 화면이 작은 라벨, 제목, 설명 한 줄, "처음이신가요?" 블록, 패널 안내문 순서로 시작합니다. 대시보드는 데이터가 나오기 전에 문단이 네 개입니다. 시연할 때는 글 벽처럼 보입니다.
3. **가장 강력한 기능이 빈 화면으로 시작합니다.** 영향도("이걸 고치면 무엇을 다시 테스트해야 하나")는 고객이 가장 반길 기능입니다. 그런데 첫 화면은 입력 칸 하나와 큰 빈 공간뿐입니다. 프로젝트 · 분석 화면도 오른쪽 넓은 영역이 비어 있습니다.
4. **노드 맵이 너무 늦게, 너무 복잡하게 나옵니다.** 시각적으로 가장 인상적인 화면인데, 그래프 위에 설정 줄이 6줄 있어서 그래프가 화면 중간부터 시작합니다. 처음 보이는 그림도 선 366개가 엉킨 실뭉치이고 글자는 작습니다.
5. **내부 코드값이 그대로 보입니다.** `READY`, `DONE`, `DONE_WITH_WARNING`처럼 영어 대문자 enum이 상태 배지로 나옵니다. 고객에게는 "완료", "경고 있음"이 맞습니다.

## 제안 방향: "레이어 색을 이 제품의 언어로"

skill은 과감한 시도를 **한 곳에만** 쓰라고 권합니다. 저는 그 한 곳으로 이미 노드 맵에 있는 **레이어 색**을 고르겠습니다(화면 파랑, 서비스 보라, SQL 분홍, 테이블 초록). 지금은 노드 맵에서만 쓰이는데, 이 색이 이 제품만의 정체성이 될 수 있습니다. 나머지(사이드바, 표, 폼)는 지금처럼 조용하게 둡니다.

| 순서 | 할 일 | 효과 |
|---|---|---|
| 1 | **대시보드 맨 위를 "흐름 띠"로 바꿉니다.** 프로젝트마다 `화면 742 → 컨트롤러 213 → 서비스 230 → SQL 217 → 테이블 305`를 레이어 색 띠로 보여 주고, 호출 해석률을 붙입니다. 서버 상태와 임베딩은 위쪽의 조용한 한 줄 상태 표시로 내립니다. | 시연 첫 10초에 "우리 시스템 전체 구조를 읽었구나"가 전달됩니다. |
| 2 | **노드 맵은 그래프를 먼저 보여 줍니다.** 설정은 접어 두고, 처음에는 진입점 하나를 중심으로 좁힌 그림을 보여 줍니다. | 실뭉치 대신 이야기가 있는 그림이 됩니다. |
| 3 | **영향도의 빈 화면을 예시로 채웁니다.** 최근 변경분의 영향이나 예시 대상을 바로 누를 수 있게 합니다. | 아무것도 입력하지 않아도 결과를 보여 줄 수 있습니다. |
| 4 | **글을 줄이고 상태 배지를 한글로 바꿉니다.** 화면 설명은 한 줄로 줄이고, "처음이신가요?"는 사용 안내 화면으로 모읍니다. | 화면이 정돈되어 보이고, 숫자와 그림이 앞에 섭니다. |
| 5 | **Pretendard 글꼴을 함께 배포합니다.** CSS에는 Pretendard가 첫 번째로 지정돼 있지만 파일을 같이 배포하지 않습니다. 그래서 고객 PC에 이 글꼴이 없으면 맑은 고딕으로 바뀝니다. Pretendard는 OFL 라이선스라 woff2 파일을 같이 넣어도 됩니다(폐쇄망도 고려). | 어느 PC에서 시연해도 같은 인상이 나옵니다. |

화면의 골격(사이드바, 2단 구성, 탭)과 JS가 쓰는 class 이름은 그대로 두고, 강조할 곳만 바꾸는 방향입니다. 1번에 필요한 레이어별 개수가 `GET /api/dashboard`에 없다면 dstone-knowledge 쪽에 필드를 추가해야 할 수 있습니다. 착수 전에 확인하겠습니다.

## 확인할 것

skill도 디자인 전에 대상을 확정하라고 권합니다. 두 가지를 알려 주세요.

1. **주된 시연 대상은 누구인가요?** 현업이나 임원(그림과 숫자 중심)인지, 고객사 개발 리더(정확성과 깊이 중심)인지에 따라 1번 흐름 띠를 어디까지 강조할지 달라집니다.
2. **어디서부터 할까요?** 저는 시연 첫인상을 정하는 **1번(대시보드 흐름 띠)**부터 시작하기를 권합니다. 그 전에 색, 글꼴, 레이아웃 계획을 와이어프레임으로 먼저 보여 드리겠습니다.

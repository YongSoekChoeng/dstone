# pasted_content_id276

> 생성일시: 2026-11-22 10:05

## 질문

<pasted_content id="2760">
dstone-ai-engine Workflow 실행 관리 에서

<수정 요청>
1. 승인 시에 입력한 텍스트가 step이 바뀐 뒤에도 그대로 남아있음. => 바뀐 step이 도출한 아웃풋으로 오해를 줄 수 있음. 요청시 서버를 호출하고나면 초기화 해야 하는게 아닐까 싶음. 
</pasted_content id="2760">

---

## 답변

승인 의견칸이 이제 서버에 결정을 보낸 뒤 비워집니다. `dstone-ai-engine/src/main/resources/static/ai/assets/js/workflow-admin.js` 한 파일만 고쳤습니다.

**동작**
- **결정을 보내 성공하면:** 의견칸을 바로 비웁니다.
- **다시 승인 대기가 되면:** 의견칸과 상태 문구("결정을 전달했습니다…")를 함께 비웁니다. 다음 APPROVAL step에 닿았을 때나, '다시'를 골라 같은 승인 step으로 돌아왔을 때가 여기에 해당합니다. 지금까지는 실행을 바꿀 때만 상태 문구를 지웠습니다.
- **그대로 두는 경우:**
  - 같은 승인 대기가 이어지는 동안 자동 갱신이 돌 때. 쓰고 있던 의견이 지워지지 않습니다.
  - 결정 전송이 실패했을 때(다른 사람이 먼저 결정해 409가 났을 때 등). 다시 보낼 수 있도록 남겨 둡니다.

`node --check`로 문법만 확인했고, 실제 화면에서는 아직 확인하지 않았습니다. 정적 파일이 jar 안에 들어가므로 다시 빌드하고 배포해야 반영됩니다. 화면 동작만 바뀌어서 문서는 고치지 않았습니다.

---

## 질문

커밋 푸시해줘

---

## 답변

`workflow-admin.js` 수정분을 `3854f322`로 커밋하고 원격 `main`에 푸시했습니다. 이 파일 말고 다른 변경은 없었습니다.

---

## 질문

<pasted_content id="2760">
dstone-ai-engine Workflow 실행 관리 에서

<수정 요청>
승인을 기다립니다 섹션의 UI가 너무 허접해. 밑에 버튼이 노출될 때 버튼의 갯수가 많어지니까 버튼 옆의 텍스트가 보기좋지 않게 아래라인으로 떨어지고 아무튼 성의가 없어보여.
이 어플리케이션은 제품판매를 위해 만들어지고 있어. UI에도 각별히 신경써야 하는 이유야.
</pasted_content id="2760">

---

## 답변

화면 구조(HTML)는 바꿨고, 지금 버튼 렌더링 JS와 CSS를 고치는 중입니다.

---

## 답변

CSS 수정을 마쳤습니다. knowledge 쪽 공용 CSS 사본과 일치 여부를 확인합니다.

---

## 답변

서버가 떠 있지 않아서, 같은 CSS로 승인 영역만 그린 시험 페이지를 만들어 스크린샷으로 확인합니다.

---

## 답변

'승인을 기다립니다' 영역을 새로 꾸몄습니다. 선택지가 많아져도 버튼 옆 글자가 아랫줄로 떨어지지 않습니다. 고정 문구로 시험 페이지를 만들어 넓은 화면, 폰 너비(420px), 승인/반려 방식을 캡처해 배치만 확인했습니다. 실제 서버에서는 아직 확인하지 않았습니다.

**바뀐 화면** (노란 띠 안에 흰 카드 하나로 묶음)
1. **맨 위 정보 줄:** 기다리는 step, 결정할 역할, 결정하는 사람을 이름표와 값으로 나란히 보여 줍니다. 전에는 이 정보가 긴 안내 문장 하나에 섞여 있었습니다. 역할이 없으면 그 항목은 숨깁니다.
2. **짧은 안내 한 줄.**
3. **의견칸:** 폭을 가득 쓰고, 고정폭 글꼴 대신 본문 글꼴을 씁니다. "선택 · 고른 결정과 함께 다음 step에 전달됩니다"라는 설명을 붙였습니다.
4. **결정 (선택지 방식):** 버튼과 "→ 받는 곳" 글자를 따로 늘어놓던 것을 카드로 바꿨습니다.
   - 카드 한 장에 선택지 이름(굵게)과 "의견 받는 곳 · stepNN(…)"이 함께 들어갑니다.
   - 같은 너비 격자로 놓여서 개수가 늘어도 줄이 맞게 다음 줄로 넘어갑니다.
   - 마우스를 올리면 강조됩니다.
5. **승인/반려 방식:** 오른쪽 아래에 반려(흰 버튼)와 승인(진한 버튼)을 둡니다.
6. **좁은 화면:** 정보 줄은 항목마다 한 줄씩, 카드는 한 줄을 다 쓰도록 쌓입니다.

결정을 보내는 동안에는 모든 결정 버튼을 잠가 두 번 눌리지 않게 했습니다.

**고친 파일**
- `dstone-ai-engine/.../static/ai/admin/workflow/workflow.html`: 승인 영역 구조
- `dstone-ai-engine/.../static/ai/assets/js/workflow-admin.js`: 카드 그리기와 보내는 동안 잠금
- `dstone-ai-engine/.../static/ai/assets/css/main.css`: 새 스타일. 더는 안 쓰는 `.wa-decision`, `.ai-route-receiver` 규칙은 지웠습니다.
- `dstone-knowledge/.../static/knowledge/assets/css/main.css`: 같은 내용으로 맞췄습니다. 두 앱이 쓰는 공용 사본으로, 수정 전에 두 파일이 똑같았습니다.
- `docs/09.dstone-ai-engine.md`: 화면 설명 한 줄을 고쳤습니다.

캡처 환경에 한글 글꼴이 없어 한글은 깨져 보였습니다. 실제 화면에서는 Pretendard 글꼴로 나옵니다. 정적 파일은 jar 안에 들어가므로 다시 빌드해야 보입니다.

이번에는 커밋하지 않았습니다. 화면을 확인하신 뒤 커밋·푸시하라고 하시면 하겠습니다.

---

## 질문

커밋 푸시해줘

---

## 답변

승인 대기 영역 개선을 커밋(`fa9b76a7`)하고 원격 `main`에 푸시했습니다.

커밋한 파일은 다섯 개입니다. 승인 영역 화면, 스크립트, 스타일 파일과 같은 내용으로 맞춘 dstone-knowledge 쪽 공용 CSS 사본, 그리고 `docs/09` 문서입니다. 남은 변경은 없습니다.

---

## 질문

<pasted_content id="2760">
dstone-ai-engine Workflow 실행 관리 에서

<수정 요청>
승인을 기다립니다 섹션의 UI가 더 허접해졌는데?
</pasted_content id="2760">

---

## 답변

먼저 실제 글꼴로 화면을 그려 무엇이 어색한지 확인하겠습니다.

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

---

## 답변

Now the JS: replace the button logic with a select-then-submit flow.

---

## 답변

이제 실제 글꼴로 다시 그려 확인합니다.

---

## 답변

지난번 카드형이 더 나빠 보였던 이유는 두 가지였습니다. 노란 띠 안에 흰 상자, 그 안에 또 카드가 들어가 상자가 세 겹이었습니다. 선택지 카드와 노란 띠 양쪽에 색 선이 붙어 화면이 어수선했습니다. 이번에는 구조를 바꿔 **결정을 목록에서 하나 고른 뒤 "결정 보내기"로 보내는** 방식으로 다시 만들었습니다.

**새 화면**
```
승인을 기다립니다
step06(요구사항 검토)에서 PM의 결정을 기다립니다. 진행 기록에서 … 확인한 뒤 고르세요.

결정                                          의견 (선택)
┌──────────────────────────────────────┐   ┌────────────────────────────┐
│ ○ 승인(설계진행)      step07 설계 · …   │   │                            │
│ ● 요구사항 수정       step03 요구사항 … │   │                            │
│ ○ 분석 다시          step02 현행 분석 … │   └────────────────────────────┘
│ ○ 중단                     받는 곳 없음 │   의견은 step03 …에 전달됩니다. admin 이름으로 남습니다.
└──────────────────────────────────────┘                        [ 결정 보내기 ]
```
- **상자는 한 겹만 둡니다.** 노란 띠 위에는 흰 결정 목록과 흰 의견칸만 놓았습니다.
- **선택지는 한 줄에 하나씩입니다.** 이름은 왼쪽, 의견 받는 곳은 오른쪽 끝에 붙입니다. 선택지가 늘어나도 줄만 늘고 글자가 아랫줄로 밀리지 않습니다. 받는 곳이 길면 말줄임 처리하고, 마우스를 올리면 전체가 보입니다.
- **잘못 눌러도 바로 넘어가지 않습니다.** 고르기만 해서는 아무것도 보내지 않고, "결정 보내기"를 눌러야 보냅니다. 고르기 전에는 이 단추가 꺼져 있습니다. 고르면 단추 위에 "의견은 ○○에 전달됩니다. admin 이름으로 남습니다."라는 안내가 나옵니다.
- **승인/반려 방식도 같은 모양입니다.** 목록에 승인·반려 두 줄이 나오고, 반려를 고르면 보내기 단추가 빨간색으로 바뀝니다.
- **좁은 화면:** 860px 이하에서는 목록 아래에 의견칸이 옵니다. 520px 이하에서는 받는 곳을 이름 아래 줄로 내려 자르지 않고 다 보여 줍니다.
- 자동 갱신이 돌아도 고른 결정과 쓰던 의견은 그대로 남습니다. 결정을 보내고 나면 둘 다 비웁니다.

**확인한 범위:** 실제 Pretendard 글꼴로 넓은 화면, 폰 너비(430px), 승인/반려 방식을 캡처해 보았습니다. 다만 고정 데이터로 만든 시험 페이지였고, 서버에 띄운 실제 화면에서는 아직 확인하지 않았습니다. 다시 빌드한 뒤 확인해 주세요.

고친 파일은 지난번과 같은 다섯 개입니다. 실행 관리 화면 HTML·JS·CSS, 같은 내용으로 맞춘 dstone-knowledge 쪽 공용 `main.css`, `docs/09` 설명 한 줄입니다. 아직 커밋하지 않았습니다.

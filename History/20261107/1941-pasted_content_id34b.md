# pasted_content_id34b

> 생성일시: 2026-11-07 19:41

## 질문

<pasted_content id="34bb">
dstone-boot> knowledge > 프로젝트·분석 메뉴에서 [분석 시작]을 클릭했을 때 흘러가는 로직을 아주 자세하게 추적해서 쉽게 설명해줘. 
프론트 영역에서 백엔드 영역까지 아주 자세했으면 좋겠어.
특히 엣지영역을 어떻게 처리하여 상호 관계를 정의하는지 궁금해.
<아웃풋>
/app/dstone/docs/temp/knowledge분석프로세스.md
</pasted_content id="34bb">

---

## 답변

먼저 화면부터 백엔드까지 코드를 따라가 보겠습니다.

---

## 답변

Now the common `K.call` and the boot proxy.

---

## 답변

화면 → 프록시 → 분석 시작 API → Job 실행기까지 확인했습니다. 지금은 각 분석 단계(Pass)와 엣지(관계) 생성 코드를 읽는 중입니다.

---

## 답변

Now the pass code — SCAN and the per-file runner.

---

## 답변

Now DECLARE — where references and structural edges are born.

---

## 답변

Let me read ResolvePass and the core of ReferenceResolver.

---

## 답변

Now the LINK pass and its SQL.

---

## 답변

Now the semantic plugins that create edges (Spring INJECTS, MyBatis, JSP, RichClient).

---

## 답변

Now MyBatisPlugin (EXECUTES_SQL / READS/WRITES_TABLE) and JspPlugin.

---

## 답변

엣지 생성 코드(RESOLVE·LINK·SEMANTIC 플러그인)는 거의 다 읽었습니다. 참조 수집 부분만 마저 확인하고 문서를 쓰겠습니다.

---

## 질문

Base directory for this skill: /app/dstone/.claude/skills/humanizer

# humanizer: 한국어 AI 작문 패턴 감지 및 교정기

## 소개

당신은 AI가 생성한 텍스트의 특징적인 패턴을 감지하고 교정하는 한국어 글쓰기 분석 전문가입니다. 당신의 전문성은 과학적 언어학 연구, 특히 KatFishNet 논문(ArXiv 2503.00032v4)에 기반하고 있으며, 이 논문은 인간이 작성한 텍스트와 LLM이 생성한 한국어 텍스트 사이의 측정 가능한 차이를 식별했습니다.

**중요한 컨텍스트**:
- 이 스킬은 **한국어 텍스트에만** 집중하며, 영어 번역이나 다른 언어는 다루지 않습니다
- 검출 패턴은 높은 정확도(쉼표 패턴: 94.88% AUC, 품사 다양성: 82.99% AUC, 띄어쓰기: 79.51% AUC)를 가진 **실증적 언어학 분석**에 기반합니다
- 당신의 목표는 원래의 의미와 의도를 보존하면서 텍스트를 **자연스럽게 인간이 작성한 것처럼** 만드는 것입니다
- 우선순위에 따라 구성된 6개 카테고리의 40가지 고유 패턴을 분석하며, 각 패턴은 S1/S2/S3 심각도로 분류됩니다

## 작업 설명

실행될 때 다음을 수행합니다:

1. **한국어 텍스트 읽기** - 사용자가 제공한 텍스트(인자, 대화 컨텍스트, 또는 파일)
2. **텍스트 분석** - 아래에 설명된 40가지 검출 패턴에 대해 분석
3. **AI 작문 마커 식별** - 각 발견 사항에 대한 과학적 근거와 함께
4. **텍스트 재작성** - 자연스럽고 인간이 작성한 것처럼 들리도록
5. **수정된 버전 제시** - 주요 변경 사항의 요약과 함께(선택적)

**프로세스 가이드라인**:
- 항상 원래의 의미와 사실적 내용을 보존하세요
- 적절한 격식 수준(격식체 vs 반말)을 유지하세요
- 완벽함보다 자연스러움에 집중하세요
- 중요한 변경 사항에 대한 근거를 설명하세요
- 텍스트가 AI 마커를 거의 보이지 않으면, 이미 자연스럽다고 인정하세요

## 한국어 AI 작문 특징

LLM이 생성한 한국어 텍스트는 인간의 글쓰기와 측정 가능하게 다른 고유한 언어적 패턴을 보입니다:

- **문장부호**: 과도한 쉼표 사용(61% vs 인간 26%), 영어식 쉼표 배치
- **띄어쓰기**: 의존명사 띄어쓰기의 경직된 일관성(SD=0.02 vs 인간의 변동성)
- **어휘 다양성**: 낮은 품사 다양성, 명사 과다 사용, 동사/형용사 빈곤
- **어휘**: 일반적 서술어 과다 사용(중요하다, 핵심적, 효과적, 지속가능한, 혁신적)
- **구조**: 단조로운 문장 리듬, 3박자 리스트, 과도한 접속사

이러한 패턴은 LLM 학습 데이터 편향과 확률적 텍스트 생성에서 나타나며, 한국어 원어민 독자에게 부자연스럽게 느껴질 수 있습니다.

---

## 검출 패턴 (총 40가지)

### 패턴 분류 체계

모든 패턴은 검증 수준과 심각도에 따라 메타데이터를 포함합니다.

**검증 수준**:
- **✅ 과학적**: KatFishNet 논문(ArXiv 2503.00032v4, 2025) 등 외부 문헌으로 검증된 패턴 — 검증 필드에 인라인 인용 (예: `검증: ✅ 과학적 (KatFishNet, 94.88% AUC)`)
- **📊 경험적**: 실무 관찰을 기반으로 한 커뮤니티 기여 패턴

**심각도(severity)**:
- **S1 (Critical)**: 한 번만 나와도 AI 신호로 결정적. 무조건 수정 권장.
- **S2 (Strong)**: 1~2회 허용, 3회+ 반복 시 AI 신호. 빈도 기반 판정.
- **S3 (Weak)**: 단독으로는 약한 신호이나 다른 패턴과 중첩될 때 결정적.

**자연도 등급(Naturalness Grade)** — v1.6.0 신설:

윤문 후 텍스트의 자연스러움을 한 글자로 요약. 출력 형식의 "자연도 등급" 라인에 표시.

- **A**: S1 0건 + S2 ≤ 2 — 자연스러움 거의 완벽 (인간이 작성한 것처럼 읽힘)
- **B**: S1 1~2건 또는 S2 3~5건 — 자연스럽지만 사소한 AI 흔적
- **C**: S1 3+건 또는 S2 6+건 — AI 흔적 명확, 추가 윤문 권장
- **D**: 다중 카테고리 위반 (S1 5+건 + S2 8+건) 또는 변경률 가드 트리거 발생

자연도 등급은 윤문 **후** 텍스트에 적용 — 윤문 결과의 품질을 한 줄로 평가. 사용자가 결과를 신뢰할 수 있는지 빠르게 판단하도록 돕습니다.

각 패턴에는 다음 3필드 메타데이터가 포함됩니다:
- **검증**: 검증 수준 (✅ 과학적 / 📊 경험적), 외부 문헌은 인라인 인용
- **심각도**: S1 / S2 / S3 + 정의
- **버전**: 패턴이 추가된 스킬 버전

### 빠른 참조

**카테고리별 우선순위와 대표 패턴**:

1. **문장부호** (7가지, 최고: 94.88% AUC) - 쉼표 과다, 영어식 배치, 연결어미 뒤 쉼표, 콜론 과다
2. **띄어쓰기** (3가지, 높음: 79.51% AUC) - 경직된 의존명사, 보조용언, 숫자 띄어쓰기
3. **품사 다양성** (3가지, 높음: 82.99% AUC) - 명사 과다, 동사/형용사 빈곤
4. **어휘** (10가지, 중간) - AI 유행어, 불필요한 한자어, 영어 직역, 복수형 과다, 대명사 과다, 지시관형사 과다, 주어 생략 미흡, AI 결말 표현, hype 어휘 군집, ~적 N 추상 체인
5. **구조** (4가지, 중간) - 문장 리듬, 3박자, 접속사, 경어체
6. **번역투** (13가지, 높음: 경험적) - 조사 번역투(에 대해/통해/있어서), 동사 잉여(가지고 있다), 피동 남용(되어진다/에 의해), 가능 표현 남발(할 수 있다), 미래 단정(~것이다 남발)

전체 40가지 패턴 목록과 상세 내용은 각 카테고리 참조 문서 참조.

### 상세 패턴 설명

각 패턴의 완전한 검출 기준, 과학적 근거, 예시, 교정 전략은 다음을 참조하세요:

- **문장부호 패턴 (1-7)**: [references/punctuation-patterns.md](references/punctuation-patterns.md) 참조
- **띄어쓰기 패턴 (8-10)**: [references/spacing-patterns.md](references/spacing-patterns.md) 참조
- **품사 다양성 패턴 (11-13)**: [references/pos-patterns.md](references/pos-patterns.md) 참조
- **어휘 패턴 (14-20, 37-39)**: [references/vocabulary-patterns.md](references/vocabulary-patterns.md) 참조
- **구조 패턴 (21-24)**: [references/structure-patterns.md](references/structure-patterns.md) 참조
- **번역투 패턴 (25-36, 40)**: [references/translation-ese-patterns.md](references/translation-ese-patterns.md) 참조

**상세 패턴을 로드해야 할 때**:
- 해당 카테고리의 패턴을 감지했을 때 특정 카테고리 파일을 로드하세요
- 포괄적인 분석이 필요한 경우 모든 파일을 로드하세요
- 텍스트가 최소한의 AI 마커만 보이면 로드를 건너뛰세요

---

## 작업 흐름

### 1단계: 텍스트 받기

- 사용자가 인자, 대화, 또는 파일 경로를 통해 한국어 텍스트를 제공합니다
- 파일 경로가 주어지면 Read 도구를 사용하여 내용을 로드합니다
- 텍스트가 대화 컨텍스트에 있으면 추출합니다

### 2단계: 모든 패턴에 대해 분석

다음을 체계적으로 확인합니다:
- **문장부호 패턴 (1-7)**: 쉼표 개수, 위치 확인, 불필요한 사용 식별, 영어식 콜론 사용 확인
- **띄어쓰기 패턴 (8-10)**: 의존명사 띄어쓰기 일관성, 보조용언 띄어쓰기, 숫자 띄어쓰기 확인
- **품사 패턴 (11-13)**: 명사/동사/형용사 비율 추정, 명사화 구문 식별
- **어휘 패턴 (14-20, 37-39)**: AI 유행어, 한자어 과다 사용, 영어 직역, 복수형 '-들' 과다 사용, 대명사 과다, 지시관형사 과다, 주어 생략 미흡, AI 결말 관용구·결말 공식·결말 단언, hype 어휘 군집, ~적 N 추상 체인 확인
- **구조 패턴 (21-24)**: 문장 길이 변동, 목록 패턴, 접속사, 경어체 균일성 확인
- **번역투 패턴 (25-36, 40)**: 조사 번역투(에 대해/통해/있어서), 동사 잉여(가지고 있다), 피동 남용(되어진다/에 의해), 가능 표현 남발(할 수 있다), 미래 단정(~것이다 남발) 확인

감지한 패턴에 필요한 **상세 참조 파일을 로드**하세요.

### 3단계: 발견 사항 식별

감지된 각 패턴에 대해:
- 텍스트의 구체적인 인스턴스를 기록하세요
- 과학적 근거를 설명하세요(해당되는 경우 KatFishNet 참조)
- 이 패턴이 왜 AI 작문을 나타내는지 설명하세요

### 4단계: 텍스트 재작성

- 원래의 의미와 사실적 내용을 보존하세요
- 적절한 격식 수준을 유지하세요
- 감지된 모든 패턴에 대한 교정을 적용하세요
- 자연스러움과 인간다운 흐름에 집중하세요
- 한국어 원어민이 작성한 것처럼 들리게 만드세요

### 4.5단계: 의미 보존 자체검증 (필수)

자연스러운 버전을 출력하기 전에 다음 6항을 자체 점검합니다. **위반 사항이 발견되면 해당 부분을 수정한 뒤 재점검합니다.**

- [ ] **수치·날짜·고유명사**: 원본의 숫자, 날짜, 인명, 기관명, 제품명이 모두 보존되었는가?
- [ ] **인과관계 방향**: "X 때문에 Y" → "Y 때문에 X"로 뒤바뀌지 않았는가?
- [ ] **부정 표현**: "있다/없다", "가능/불가능", "찬성/반대"가 반전되지 않았는가?
- [ ] **직접 인용**: 큰따옴표 안 직접 인용은 그대로인가?
- [ ] **핵심 주장·결론**: 원본의 메시지·결론이 변경되지 않았는가?
- [ ] **격식 수준**: 원본의 격식체/반말이 일관되게 유지되었는가?

위반 사항이 없으면 출력 형식의 "주요 변경 사항 요약" 끝에 "✓ 의미 보존 검증: 6항 모두 통과"를 명시합니다. 위반이 있었다면 수정 사항을 함께 기록합니다.

### 5단계: 결과 제시

**형식**:

```
## 분석 결과

**감지된 패턴 요약**: S1 N건, S2 M건, S3 K건
**자연도 등급**: A | B | C | D — [등급 부여 근거 한 줄]

다음 AI 작문 패턴을 감지했습니다(심각도 높은 순):

1. **패턴 X [S1]**: [발견 사항에 대한 간단한 설명]
   - [텍스트의 구체적인 예시]
   - [과학적/경험적 근거]
   - [S1/S2/S3 부여 이유]

2. **패턴 Y [S2]**: [...]

[감지된 모든 패턴에 대해 계속, S1 → S2 → S3 순으로 정렬]

---

## 자연스러운 버전

[자연스러운 한국어 글쓰기로 재작성된 텍스트]

---

## 주요 변경 사항 요약 (선택적)

- [근거와 함께 변경 사항 1]
- [근거와 함께 변경 사항 2]
- [기타]
```

**텍스트가 이미 자연스러운 경우**:

```
## 분석 결과

이 텍스트는 AI 작문 마커를 거의 보이지 않으며 상당히 자연스럽게 읽힙니다. 사소한 문제만 발견했습니다:

- [사소한 문제 1 (있는 경우)]
- [사소한 문제 2 (있는 경우)]

이 글은 이미 인간이 작성한 것처럼 들립니다. [원본 또는 약간 수정된 버전을 제공]
```

---

## 중요 사항

### 의미 보존
- 사실적 내용이나 핵심 메시지를 절대 변경하지 마세요
- 모든 주요 정보를 그대로 유지하세요
- 작성자의 의도된 톤과 목적을 유지하세요

### 변경률 가드 (Over-rewriting Guard)

자연스러운 버전을 작성한 후 **변경률**을 자체 추정해 과도한 교정을 방지하세요.

**변경률 계산**:
- 원본과 자연스러운 버전을 어절 단위로 비교
- 변경된 어절 / 전체 어절 비율 (대략적, 0~100%)
- 단순 띄어쓰기·문장부호 변경은 제외하고 의미 단위 변경만 카운트

**임계값**:
- **<30% (정상)**: 그대로 출력
- **30~50% (경고)**: 출력 끝에 "변경률 N%로 다소 큼. 의미 보존 재확인 권장" 한 줄 추가
- **>50% (중단)**: 출력하지 말고 사용자에게 확인 요청
  - 메시지: "변경률이 N%로 과도합니다. 원본 의미가 변경됐을 가능성이 있습니다. 더 보수적으로 재작성해드릴까요?"
  - 사용자 동의 시 보수적으로 재작성 후 30% 이내로 다시 시도

**근거**: 과도한 교정은 원본의 의미·뉘앙스·작성자 톤을 잃을 위험이 있음. 정량 임계로 자체 검증해 의미 보존 원칙을 강화.

### 격식 유지
- 원래의 격식 수준(격식체 vs 반말)을 존중하세요
- 텍스트가 격식체(비즈니스, 학술)라면 격식체를 유지하되 자연스럽게 하세요
- 텍스트가 반말(블로그, 소셜 미디어)이라면 더 대화적으로 만드세요
- 리듬을 위해 같은 격식 범위 내에서 어투를 다양화할 수 있습니다

### 자연스러움 집중
- 목표는 인간이 작성한 것처럼 들리게 하는 것이지, 완벽하게 만드는 것이 아닙니다
- 자연스러운 한국어 글쓰기는 약간의 불규칙성과 개인적 스타일을 가지고 있습니다
- 작성자의 목소리를 바꿀 정도로 과도하게 교정하지 마세요

### 근거 설명
- 주요 변경 사항에 대해서는 간단히 이유를 설명하세요
- 40가지 검출 기준의 특정 패턴을 참조하세요
- 사용자가 무엇이 텍스트를 AI 생성처럼 vs 인간처럼 들리게 하는지 이해하도록 도와주세요

### 문화적 감수성
- 한국 문화적 맥락과 기대를 인식하세요
- 한국어 독자는 직접성과 효율성을 중시합니다
- 원래의 톤과 일치하지 않는 한 지나치게 화려하거나 극적인 언어를 피하세요
- 영어와 다른 한국어 글쓰기 관습을 존중하세요

### 과학적 근거
- 당신의 교정은 실증적 언어학 연구에 기반합니다
- KatFishNet 논문은 대부분의 패턴에 대한 과학적 검증을 제공합니다
- 주관적인 스타일 선호가 아니라 측정 가능한 언어적 차이를 적용하는 것입니다

---

## 특수 상황

### 매우 짧은 텍스트
텍스트가 1-2문장만 있는 경우:
- 모든 패턴을 감지하기에 충분한 내용이 없을 수 있습니다
- 명백한 마커(쉼표, 어휘, 띄어쓰기)에 집중하세요
- 한계를 인정하세요: "이 텍스트는 매우 짧아서 패턴 분석이 제한적입니다."

### 이미 자연스러운 텍스트
텍스트가 AI 마커를 거의 또는 전혀 보이지 않는 경우:
- 텍스트가 잘 작성되었음을 인정하세요
- 있다면 사소한 제안을 제공하세요
- 불필요한 경우 교정을 강요하지 마세요

### 기술적이거나 전문 분야별 텍스트
텍스트에 전문 용어가 포함된 경우:
- 한자어라도 기술 용어는 보존하세요
- 문장 구조와 문장부호 패턴에 집중하세요
- 기술 글쓰기는 다른 관습을 가지고 있음을 인정하세요

### 언어 혼합
텍스트에 영어 단어나 구문이 포함된 경우:
- 영어 내용은 그대로 보존하세요
- 한국어 부분에 집중하세요
- 부자연스러운 코드 전환 패턴을 확인하세요

### 사용자가 특정 격식 요청
사용자가 격식체 또는 반말 출력을 요청하는 경우:
- 요청대로 격식 수준을 조정하세요
- 여전히 40가지 패턴 교정을 모두 적용하세요
- 요청에 따라 격식을 조정했음을 설명하세요

---

## 분석 예시

실제 적용 사례는 [examples/](examples/) 디렉토리 참조:
- [before-1.md](examples/before-1.md) / [after-1.md](examples/after-1.md): 격식체 교육 분야 텍스트
- [before-2.md](examples/before-2.md) / [after-2.md](examples/after-2.md): 다양한 패턴 예시
- [before-3.md](examples/before-3.md) / [after-3.md](examples/after-3.md): 번역투 풍부 비즈니스 보고서 (v1.4.0 신규 카테고리 시연 + S1/S2/S3 심각도 출력 형식)
- [before-4.md](examples/before-4.md) / [after-4.md](examples/after-4.md): AI 결말 관용구·~적 N·~것이다 풍부 텍스트 (v1.5.0/v1.6.0 신규 패턴 시연 + 자연도 등급 출력 형식)

각 예시는 감지된 패턴, 분석, 자연스러운 버전, 주요 변경 사항을 포함합니다.

---

## 최종 알림

- **철저하게**: 40가지 패턴을 모두 체계적으로 확인하세요
- **과학적으로**: 가능한 경우 언어학 연구에 근거하여 교정하세요
- **도움이 되게**: 사용자가 배울 수 있도록 근거를 설명하세요
- **존중하며**: 작성자의 의미와 의도를 보존하세요
- **자연스럽게**: 목표는 로봇 같은 완벽함이 아니라 인간다운 한국어입니다

당신의 전문성은 한국어 작성자가 의도된 메시지와 스타일을 유지하면서 진정으로 인간이 작성한 것처럼 들리는 텍스트를 만들도록 돕습니다. 한국어 언어학에 대한 지식과 AI 텍스트 감지 뒤의 과학적 연구를 사용하여 가치 있고 정확한 교정을 제공하세요.

---

## 답변

요청하신 문서를 작성했습니다: `/app/dstone/docs/temp/knowledge분석프로세스.md`

[분석 시작] 클릭부터 분석이 끝날 때까지를 코드 순서대로 따라가며 썼습니다. `docs/temp`는 git 관리 밖이라 커밋하지 않았습니다.

**전체 흐름**
1. **화면**: `project.js`의 `startAnalysis()`가 라벨, 증분 여부, 다시 돌릴 단계를 담아 `K.call()`로 보냅니다. 브라우저는 dstone-knowledge를 직접 부르지 않고 dstone-boot의 `/knowledge/api/call.do` 하나로만 요청합니다.
2. **dstone-boot 프록시**: `KnowledgeProxyService`가 `/api/` 아래 경로인지 검사하고 `X-API-Key`를 붙여 넘깁니다. 응답은 상태 코드와 본문을 손대지 않고 그대로 돌려줍니다.
3. **시작 API**: `AnalysisService.startAnalysis()`가 리비전을 새로 만들거나 이어 쓰고, Job을 READY로 등록한 뒤 202를 바로 돌려줍니다. 화면은 이후 3초마다 Job 상태를 다시 읽습니다.
4. **백그라운드 실행**: `AnalysisJobRunner`가 SCAN → CARRY → DECLARE → RESOURCE → CARRY_RESOLVE → RESOLVE → LINK → SEMANTIC → DOCUMENT 순서로 돌립니다. 이미 DONE인 단계는 건너뛰어서 같은 라벨로 다시 시작하면 이어서 분석합니다. 파일 단위 단계는 "읽고 푸는 일(DB 쓰기 없음)"과 "저장(트랜잭션 하나)"으로 나뉘어 있습니다. 이 구조 덕분에 끝나지 않는 파일 하나가 분석 전체를 멈추게 하지 못합니다.

**엣지 처리 (문서 6장)**
- **두 단계로 만듭니다.** DECLARE는 파일 하나만 보고 "이름만 적힌 참조"를 `analysis_reference`에 남깁니다. 실제 대상을 찾아 `analysis_relation`에 엣지로 만드는 일은 모든 선언이 DB에 들어간 뒤 RESOLVE가 합니다.
- **RESOLVE**는 단계를 차례로 내려가며 대상을 찾습니다. 타입 해석기로 풀리면 HIGH, 대상의 타입을 알아내 그 타입에서 찾으면 MEDIUM, 프로젝트 전체에서 같은 이름의 후보가 5개 이하일 때만 LOW로 잇고, 그래도 안 되면 UNRESOLVED와 이유를 남깁니다. 해석기가 찾은 선언은 이름이 아니라 파일과 줄 위치로 메소드 ID에 맞춥니다.
- **LINK**는 파일을 읽지 않고 SQL만으로 이미 만든 엣지끼리 엮습니다. 상속 사슬을 따라 `OVERRIDES`를 만들고, 그것을 바탕으로 `CALLS_POSSIBLE_IMPLEMENTATION`을 만듭니다. 후자는 인터페이스나 abstract 메소드를 부를 때만 만듭니다.
- **SEMANTIC** 플러그인들이 의미 엣지를 붙입니다. 주입 관계(`INJECTS`), 메소드가 실행하는 SQL(`EXECUTES_SQL`, SQL 이름은 상수를 따라가 계산), SQL이 읽고 쓰는 테이블(`READS_TABLE`/`WRITES_TABLE`), 화면과 서버 사이의 관계(`RENDERS`/`REQUESTS`/`INCLUDES`)입니다.
- 엣지 종류 14가지를 "출발 → 도착, 만드는 단계, 근거, 신뢰도" 표로 정리했고, JSP 화면에서 테이블까지 이어지는 예시와 다시 돌려도 엣지가 중복되지 않는 이유도 넣었습니다.

**코드를 읽다 확인한 점**
- 스키마 주석에는 `HAS_METHOD`/`HAS_FIELD` 엣지가 적혀 있지만 실제로 만드는 코드는 없습니다. 타입과 메소드, 필드의 소속은 엣지가 아니라 `owner_symbol_id` 컬럼으로 표현합니다. 문서에도 이 내용을 적어 두었습니다.

---

## 질문

This session is being continued from a previous conversation that ran out of context. The summary below covers the earlier portion of the conversation.

<system-reminder>
<tools-used-before-summary>Bash, Skill, Write</tools-used-before-summary>
</system-reminder>
Summary:
1. Primary Request and Intent:
   The user (pasted text, the whole message) asked: "dstone-boot> knowledge > 프로젝트·분석 메뉴에서 [분석 시작]을 클릭했을 때 흘러가는 로직을 아주 자세하게 추적해서 쉽게 설명해줘. 프론트 영역에서 백엔드 영역까지 아주 자세했으면 좋겠어. 특히 엣지영역을 어떻게 처리하여 상호 관계를 정의하는지 궁금해. <아웃풋> /app/dstone/docs/temp/knowledge분석프로세스.md"

2. Key Technical Concepts:
   - Browser → dstone-boot proxy (`/knowledge/api/call.do`) → dstone-knowledge REST (`POST /api/projects/{id}/analyses`, returns 202)
   - Job pipeline:
     - `AnalysisJobRunner` runs `AnalysisPass` beans in order: SCAN 100, CARRY 150, DECLARE 200, RESOURCE 250, CARRY_RESOLVE 290, RESOLVE 300, LINK 400, SEMANTIC 500, DOCUMENT 600.
     - Passes already DONE are skipped (resume works through revisionLabel; rerunFrom resets later passes).
   - `FilePassRunner` splits each file into prepare (no DB writes, watched by a watchdog) and write (one transaction). Failures are classified as HUNG, TOO_DEEP or INTERNAL.
   - DECLARE writes declarations plus unresolved `analysis_reference` rows, keyed by the position of the name token. It writes no edges. Ownership is stored in the `owner_symbol_id` column; HAS_METHOD exists only in a schema comment.
   - RESOLVE resolves references through a ladder:
     1. solver → HIGH
     2. owner type → HIGH (synthetic), MEDIUM (single) or LOW (multiple)
     3. external type → MEDIUM EXTERNAL_METHOD
     4. project-wide name match with ≤5 candidates → LOW
     5. otherwise UNRESOLVED
     - The solver chain is DbTypeSolver → ReflectionTypeSolver(JdkClassFilter) → JarTypeSolver, with NoCache.
     - Rows are stored by deleting all of the file's relations and re-inserting them.
   - LINK: OVERRIDES is built with a recursive CTE (depth 20). CALLS_POSSIBLE_IMPLEMENTATION is created only for abstract targets. Metrics are written too.
   - SEMANTIC: one transaction that clears previous results and then runs the plugins. Rules come from `conf/rules/*.yml`.
     - INJECTS = annotated field + USES_TYPE
     - EXECUTES_SQL comes from the mapper interface or from SQL names computed by ConstantEvaluator
     - READS_TABLE/WRITES_TABLE come from JSQLParser, with a regex fallback
     - RENDERS/REQUESTS/INCLUDES come from JSP and rich-client screens
   - Node IDs are SHA-1 for types, methods and fields; `S{mapper_id}` for SQL; `F{file_id}` for files. Tables exist only as `to_external`. `kg_node`/`kg_edge` are views.

3. Files and Code Sections:
   - Created: `/app/dstone/docs/temp/knowledge분석프로세스.md`, a full Korean document. Sections:
     - 0 overview diagram and table of the edge-producing passes
     - 1 frontend (index.jsp elements, project.js `startAnalysis`/`watchJob`/`renderJob`, knowledge-common.js `call`)
     - 2 the dstone-boot proxy (validation, X-API-Key, 502 handling)
     - 3 AnalysisService.startAnalysis, as a table of steps
     - 4 executor and AnalysisJobRunner
     - 5 per-pass details
     - 6 edges in detail: the analysis_relation columns, the reference→edge two-step design, the RESOLVE ladder table, the LINK SQL, each SEMANTIC plugin, a summary table of all edge types, an example chain, idempotency
     - 7 what happens after completion
     - 8 a table of code locations
   - Read only, no changes:
     - `dstone-boot/.../views/knowledge/index.jsp`
     - `webapp/knowledge/assets/js/project.js` and `knowledge-common.js`
     - `KnowledgeProxyController.java` and `KnowledgeProxyService.java`
     - the dstone-knowledge `ProjectController`, `AnalysisService`, `AnalysisJobRunner`, `ConfigJob`, `FilePassRunner`, `ScanPass`, `DeclarePass`, `DeclarationCollector`, `ResolvePass`, `ReferenceResolver`, `LinkPass`, `SemanticPass`, `SpringPlugin`, `MyBatisPlugin`, `ConstantEvaluator`, `JspPlugin`, `RichClientPlugin`, `CarryPass`, `CarryResolvePass`, `ResourcePass`, `DocumentPass`
     - `sqlmap/analysis/RelationDao.xml`, `SemanticDao.xml`, `ResourceDao.xml`
     - `schema/02-create-table-postgresql-dstone-knowledge.sql`
     - `.claude/docs/knowledge-core.md`

4. Errors and fixes: None.

5. Problem Solving: The flow was traced end to end and documented. The document points out that DECLARE creates no edges.

6. All user messages:
   - The pasted request quoted in section 1. It contained no security constraints. Standing rules from memory and CLAUDE.md still apply: respond in Korean; docs/temp is outside git (do not commit or stage files there, and do not report them as repo changes); work only in /app/dstone; follow humanizer for Korean writing.

7. Pending Tasks: Only the final reply to the user is left.

8. Current Work: The output file `/app/dstone/docs/temp/knowledge분석프로세스.md` was just written successfully with the Write tool.

9. Optional Next Step: Send the user a short final message in Korean. It should give the file path, outline the document's structure (frontend → proxy → API → Job → per-pass details → section 6 on edges), and point out the key finding: edges are created only in RESOLVE, LINK and SEMANTIC, and DECLARE only stores references. Do not commit anything, because docs/temp is outside git.

If you need specific details from before compaction (like exact code snippets, error messages, or content you generated), read the full transcript at: /home/jysn007/.claude/projects/-app-dstone/d2dd5ddb-e729-4ea7-b496-02546e99fc18.jsonl
Continue the conversation from where it left off without asking the user any further questions. Resume directly — do not acknowledge the summary, do not recap what was happening, do not preface with "I'll continue" or similar. Pick up the last task as if the break never happened.

The messages after this summary are the most recent messages from before compaction, kept verbatim. The summary was written without seeing them, so something it says has not happened yet may already have happened in them.

---

## 질문

5.3 CARRY (증분일 때만) 에 대해서 조금 더 명확하게 설명해줘. 그래서 하는 일이 뭐야?

---

## 답변

문서 5.3절을 보강하고 있습니다. CARRY 소스와 SQL을 확인했고, 이어서 설명을 정리합니다.

---

## 답변

CARRY가 하는 일은 한마디로 **"지난 분석과 내용이 똑같은 파일은 다시 파싱하지 말고, 지난 결과를 이번 리비전으로 복사해 두는 것"**입니다.

### 왜 필요한가
리비전 하나에는 항상 프로젝트 전체 분석 결과가 들어 있어야 합니다. 그런데 파일 1,000개 중 3개만 고쳤는데 1,000개를 다 다시 파싱하면 시간이 아깝습니다. 그래서 CARRY가 997개의 결과를 기준 리비전에서 복사해 두고, 뒤에 오는 DECLARE와 RESOURCE는 3개만 처리합니다.

### 언제 도나
- 화면에서 "증분"을 체크하고 **새 라벨**로 시작할 때만 돕니다.
- 기준 리비전은 따로 지정하면 그것을 쓰고, 지정하지 않으면 분석이 끝난 가장 최근 리비전을 씁니다.
- 기준 리비전이 없거나 분석기 버전이 다르면 전체 분석으로 바뀝니다. 이때 CARRY는 아무것도 하지 않습니다.

### 실제로 하는 일 (`CarryPass`, 트랜잭션 하나)
1. **같은 파일 찾기** (`markCarriedFiles`): SCAN이 만든 새 파일 행마다 기준 리비전에서 같은 파일을 찾습니다. 경로와 SHA-256이 같아야 하고, 언어·파일 종류·모듈·인코딩까지 같아야 합니다. 짝을 찾으면 `carried_from_file_id`에 옛 파일 ID를 적어 둡니다.
2. **"끝남" 표시 옮기기** (`carryFilePasses`): 기준 리비전에서 DECLARE와 RESOURCE가 DONE이었던 파일만 이번에도 DONE으로 표시합니다. 그때 실패했거나 건너뛴 파일은 표시하지 않아서 이번에 다시 처리합니다.
3. **결과 행 복사** (`copyRows`): 선언·참조 테이블 5개와 매퍼·설정·자원 테이블 3개에서 옛 파일의 행을 복사합니다. 이때 `revision_id`와 `file_id`만 새 값으로 바꿉니다.
4. **통계 갱신** (`ANALYZE`): 복사가 끝나면 DB 통계를 새로 냅니다. 이 작업을 빼면 DB가 실행 계획을 잘못 골라서, 1초면 끝나던 LINK의 SQL이 2분 넘게 걸린 적이 있습니다.

복사한 행의 ID를 그대로 써도 되는 이유가 있습니다. 메소드 ID 같은 값은 `SHA-1(프로젝트|소스 루트|FQN…)`으로 만들고 리비전 번호가 들어가지 않습니다. 그래서 복사한 선언과 새로 파싱한 선언이 같은 ID 체계를 씁니다.

### 하지 않는 일
- **엣지(관계)는 복사하지 않습니다.** 내 파일이 그대로여도 내가 호출하는 파일이 바뀌면 호출 대상이 달라질 수 있기 때문입니다. 이 판단은 바뀐 파일의 선언이 모두 들어온 뒤에 CARRY_RESOLVE가 따로 합니다.
- LINK, SEMANTIC, DOCUMENT는 증분 분석이어도 매번 리비전 전체를 다시 만듭니다.
- 지워진 파일은 새 SCAN 결과에 없으므로 복사되지 않고 저절로 빠집니다.

이 내용으로 문서의 5.3절도 표와 함께 다시 썼습니다(`/app/dstone/docs/temp/knowledge분석프로세스.md`).

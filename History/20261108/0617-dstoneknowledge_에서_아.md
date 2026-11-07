# dstoneknowledge_에서_아

> 생성일시: 2026-11-08 06:17

## 질문

dstone-knowledge 에서 아래와 같은 표현이 나오는데 "풀린 관계"란 무엇인가?
CARRY_RESOLVE (290) 증분 분석일 때만: 풀린 관계 복사

---

## 답변

**"풀린 관계"는 RESOLVE 단계(300)에서 이름만 있던 참조를 실제 대상으로 이어 만든 관계(`analysis_relation` 행)를 말합니다.** 여기서 "풀다"는 참조 해석(resolve)을 뜻합니다.

## 참조가 관계로 바뀌는 과정

1. **DECLARE 단계**에서는 파일 하나만 파싱합니다. 그래서 `orderService.save(dto)`라는 호출이 있으면 "`save`라는 이름을 부른다"는 사실만 `analysis_reference`에 남깁니다. 이때는 **이름만 있고**, 그 `save`가 어느 클래스의 어떤 메소드인지는 모릅니다.
2. **RESOLVE 단계**에서 심볼 색인과 해석기로 그 이름이 가리키는 대상을 찾습니다. 예를 들어 "`OrderServiceImpl.save(OrderDto)` 메소드 ID 1234"를 찾으면 `CALLS` 관계를 만듭니다. 이렇게 만들어진 관계가 "풀린 관계"입니다.

| 참조(이름만 있음) | 풀린 관계 |
|---|---|
| `CALL`, `METHOD_REF` | `CALLS` (대상이 프로젝트 밖이면 `EXTERNAL_METHOD`) |
| `CREATE` | `CREATES` + 생성자 `CALLS` |
| `FIELD_ACCESS` | `ACCESSES_FIELD` |
| `TYPE_USE`, `EXTENDS`, `IMPLEMENTS`, `THROWS` | `USES_TYPE`, `EXTENDS`, `IMPLEMENTS`, `THROWS` |

## CARRY_RESOLVE가 따로 있는 이유

선언과 풀린 관계는 복사해도 되는 조건이 다릅니다.

- **선언**(CARRY 단계)은 그 파일만 보면 정해집니다. 그래서 파일 내용이 같으면 그대로 복사합니다.
- **풀린 관계**는 다른 파일에 따라 달라집니다. `A.java`가 그대로여도 A가 부르는 `B.java`의 메소드가 바뀌었다면 A의 호출을 다시 풀어야 합니다.

그래서 `CarryResolvePass`는 바뀐 파일의 선언이 모두 들어온 뒤, RESOLVE 바로 앞(290)에서 실행됩니다. 아래 두 경우에 해당하는 파일은 관계를 복사하지 않고 RESOLVE가 다시 풉니다.

1. 앞 리비전에서 고쳐졌거나 삭제된 파일의 선언을 가리키던 파일
2. 앞 리비전에서 확실히 풀지 못한 참조 이름이 새로 생겼거나 고쳐진 파일의 선언 이름과 같은 파일. 지난번에는 프로젝트 밖의 것으로 봤지만 이번에는 프로젝트 안에서 풀릴 수 있습니다.

나머지 파일은 앞 리비전의 관계를 그대로 복사하고 "RESOLVE 끝남"으로 표시합니다. RESOLVE는 이 표시가 없는 파일만 처리합니다.

다만 LINK나 SEMANTIC이 만드는 관계(`OVERRIDES`, `INJECTS`, `EXECUTES_SQL`, `READS_TABLE`, `RENDERS` 등)는 풀린 관계가 아니므로 복사하지 않습니다. 이 관계들은 리비전 전체를 봐야 정해지기 때문에 매번 새로 만듭니다.

근거 자료는 `dstone-knowledge/src/main/java/net/dstone/knowledge/incremental/CarryResolvePass.java`의 클래스 주석과 `docs/11.dstone-knowledge.md`의 0.12절(증분 분석 한 장)과 4.5절입니다.

---

## 질문

analysis_file.checksum 컬럼은 언제 바뀌나?

---

## 답변

`checksum`에 값을 쓰는 곳을 찾았습니다. SCAN 단계 하나뿐입니다. 이미 분석한 리비전에 SCAN을 다시 돌릴 때 어떻게 되는지만 마저 확인하겠습니다.

---

## 답변

`analysis_file.checksum`을 쓰는 곳은 **SCAN 단계 하나**(`ScanPass`)뿐입니다. 다른 단계는 이 값을 읽기만 합니다.

## 값이 정해지는 때

SCAN 단계가 디스크의 파일 내용을 읽어 SHA-256을 구하고(`ScanPass.java:188`, `:197`), `AnalysisFileDao.xml`의 `upsertFile`로 행에 넣습니다. 분석 대상에서 빠지는 너무 큰 파일도 내용을 끝까지 읽어 해시를 구합니다.

## 실제로 값이 달라지는 경우

`analysis_file`은 리비전마다 따로 행을 둡니다(`UNIQUE (revision_id, path)`). 그래서 "바뀐다"는 말은 경우를 나눠 봐야 합니다.

| 경우 | checksum |
|---|---|
| 새 리비전을 분석함(전체·증분 모두) | 그 리비전의 행이 새로 생기고, 그때 디스크 내용으로 계산합니다. 앞 리비전의 행은 그대로 남습니다. |
| 같은 리비전에서 SCAN이 중간에 죽은 뒤 이어서 분석함 | SCAN을 처음부터 다시 하고, `ON CONFLICT ... DO UPDATE`가 지금 디스크 내용으로 **덮어씁니다**. |
| 같은 리비전에 `rerunFrom: "SCAN"`을 줌 | 위와 같이 다시 스캔하고 덮어씁니다. |
| 그 밖의 단계(CARRY, DECLARE, RESOLVE 등) | 바꾸지 않습니다. DECLARE의 `updateFileParsed`와 CARRY의 `carryFileParsed`는 파싱 결과, 패키지, 소스 루트만 고칩니다. |

정리하면, **한 행의 checksum이 실제로 바뀌는 경우는 같은 리비전에서 SCAN을 다시 돌렸는데 그사이 디스크의 파일이 달라졌을 때뿐입니다.**

## checksum을 읽는 곳

- **CARRY**(`CarryDao.xml`의 `markCarriedFiles`): 경로, checksum, 분류(언어·종류·모듈·인코딩)가 앞 리비전과 모두 같으면 "같은 파일"로 보고 결과를 옮겨 옵니다.
- **리비전 비교**(`DiffDao.xml:21`): checksum이 다르면 바뀐 파일로 셉니다.
- **SEMANTIC의 `MethodTitlePlugin`**: 소스를 다시 읽기 전에 checksum을 맞춰 봅니다. SCAN 뒤에 파일이 바뀌었으면 줄 번호가 어긋나므로 그 파일은 읽지 않고 이유를 남깁니다.

참고로 이 코드의 증분 분석은 앞 리비전 행의 checksum을 고치는 방식이 아닙니다. 새 리비전 행의 checksum과 비교해서 바뀐 파일을 찾습니다.

# dstoneknowledge_에서_아

> 생성일시: 2026-11-08 05:53

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

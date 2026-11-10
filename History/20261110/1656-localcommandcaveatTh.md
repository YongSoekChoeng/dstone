# localcommandcaveatTh

> 생성일시: 2026-11-10 16:56

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="ea2b">
/docs/11.dstone-knowledge.md 의 아래 내용중 질문에 대답해줘.

<내용>
##### `entry:` 입구(진입점)

키가 진입점 종류다(영문 대문자와 `_`, 20자 이내). 새 종류를 만들어도 된다(`BATCH_JOB`).

**① HTTP — 클래스 주소 + 메소드 주소** (`common/spring.yml`)

```yaml
entry:
  HTTP:
    class: "@RequestMapping(value|path = URL)"                # 클래스에 붙은 주소가 앞에 붙는다
    ANY: "@RequestMapping(value|path = URL, method = VERB)"   # HTTP 메소드는 method 속성. 비었으면 ALL
    GET: "@GetMapping(value|path = URL)"
    POST: "@PostMapping(value|path = URL)"
```

```java
@RestController @RequestMapping("/order")
class OrderController { @GetMapping("/list") List<Order> list() { … } }      // → GET /order/list
```

- 키는 `class`, `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS`, `ANY`.
- 클래스 주소가 `/*`로 끝나면 떼고 잇는다(`/sample/user/*` + `/list.do` → `/sample/user/list.do`). Spring과 같다.
- 주소를 상수로 적었으면 상수를 따라가 계산한다(`@RequestMapping(Urls.ORDER)`). 못 하면 `properties_json.unresolvedPath`에 남는다.
- 상위 클래스·인터페이스의 주소 메소드는 그것을 물려받은 **애노테이션 컨트롤러**(`layer:`의 `"@Controller"` 같은 애노테이션 규칙으로 CONTROLLER가 된 클래스)의 진입점이 된다. 사내 컨트롤러 애노테이션을 쓰면 `layer:`에도 적어야 물려받는다.

<질문>
1. 키가 진입점 종류다. 라고 되어 있는데 여기서 키 는 무엇을 가리키는가? 그리고 yaml에 표현되는 내용중에 뭐가 키인가?
</pasted_content id="ea2b">

---

## 답변

## 답

**"키"는 `entry:` 바로 아래에 오는 YAML 맵의 키를 말합니다.** 이 예시에서는 `HTTP`가 그 키이고, 이 값이 곧 진입점 종류가 됩니다.

```yaml
entry:            # 절 이름 (처리기: EntryFinder)
  HTTP:           # ← 1단계 키 = 진입점 종류. "키가 진입점 종류다"는 이것을 말함
    class: ...    # ← 2단계 키 = HTTP 안에서 주소를 어디서 읽을지 정하는 자리
    ANY:   ...
    GET:   ...
    POST:  ...
```

| 단계 | 예 | 뜻 | 규칙 |
|---|---|---|---|
| 절 이름 | `entry` | 어느 처리기가 읽을지 정함 | 정해진 이름 |
| **1단계 키** | `HTTP`, `BATCH_JOB` | **진입점 종류** | 영문 대문자와 `_`, 20자 이내. 새로 만들어도 됨 |
| 2단계 키 | `class`, `GET`, `POST`, `ANY` … | HTTP 종류 안에서 클래스 주소 또는 HTTP 메소드 | 정해진 9개 중 하나 |
| 값 | `"@GetMapping(value\|path = URL)"` | 코드에 적힌 모양 그대로 쓴 패턴 | — |

## 헷갈리는 이유

이 절에서 "키"라는 말이 두 번 나오는데, 가리키는 단계가 서로 다릅니다.

- 맨 위 문장 "**키가** 진입점 종류다" → 1단계 키 (`HTTP`)
- 아래 목록 "**키는** `class`, `GET`, `POST` …" → 2단계 키 (`HTTP:` 아래)

그래서 첫 문장만 읽으면 `GET`이나 `POST`도 진입점 종류처럼 보입니다. 사실 그것들은 `HTTP`라는 한 종류 안에서 HTTP 메소드를 고르는 자리입니다.

문서를 고치길 원하시면 두 문장을 이렇게 바꾸겠습니다.
- 첫 문장: "`entry:` 바로 아래 키가 진입점 종류다(예: `HTTP`)."
- 아래 목록: "`HTTP:` 아래 키는 `class`, `GET`, …"

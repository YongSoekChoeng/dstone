# localcommandcaveatTh

> 생성일시: 2026-11-11 14:27

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="e6cc">
/docs/11.dstone-knowledge.md 의 아래 내용중 질문에 대답해줘.

<내용>
#### 처리기별 상세설명

##### 1. layer: 계층 (LayerResolver)

**1-1. 계층 종류.** 쓸 수 있는 이름은 12개로 정해져 있다(`RuleFileParser.LAYERS`). 노드 맵의 열은 3.5절에 있다.

| 계층           | 뜻                                             | 정하는 곳               |
| ------------ | --------------------------------------------- | ------------------- |
| `CONTROLLER` | 밖의 요청을 처음 받는 클래스. Spring 컨트롤러, 서블릿, Struts 액션 | 규칙, `StrutsEntries` |
| `SERVICE`    | 업무 로직                                         | 규칙                  |
| `REPOSITORY` | DB에 접근하는 클래스. DAO, 매퍼 인터페이스, 저장소              | 규칙                  |
| `MODEL`      | 데이터를 담는 클래스. VO, DTO, 엔티티. 관계 트리에서 기본으로 뺀다    | 규칙                  |
| `VIEW`       | JSP에서 만든 타입(`JspToJava`)                      | 코드(`LayerResolver`) |
| `CONFIG`     | 설정 클래스                                        | 규칙                  |
| `WEB_FILTER` | 서블릿 필터, 인터셉터. 노드 맵에서는 컨트롤러 열에 놓인다             | 규칙                  |
| `ASPECT`     | AOP 애스펙트                                      | 규칙                  |
| `UTIL`       | 공용 도구 클래스                                     | 짐작 규칙               |
| `EXCEPTION`  | 예외 클래스                                        | 짐작 규칙               |
| `BATCH`      | 배치, 잡, 스케줄러                                   | 짐작 규칙               |
| `COMPONENT`  | `@Component`만 붙어 무엇인지 더 모르는 클래스               | 짐작 규칙               |

<질문>
CONTROLLER 레이어를 정하는게 rule의 layer 라는 것은 알겠어. 그런데 StrutsEntries 의 어디서 레이어를 정하지?
</pasted_content id="e6cc">

---

## 답변

`StrutsEntries.addAction()` 안에서 정합니다. 위치는 `dstone-knowledge/src/main/java/net/dstone/knowledge/analysis/pass08/semantic/entry/StrutsEntries.java:140`입니다.

```java
Map<String, Object> location = action.className == null ? null : symbolDao.selectTypeLocation(revisionId, action.className);
if (location != null) {
    symbolId = (String) location.get("symbolId");
    layered = semanticDao.updateLayerBySymbol(revisionId, symbolId, "CONTROLLER", "struts-config");
    ...
```

**순서**

1. `StrutsConfigReader`가 `struts-config.xml` 또는 `struts.xml`을 읽습니다. 그러면 `<action path="..." type="com.acme.OrderAction">`(Struts 2는 `class="..."`)마다 `Action` 객체가 하나씩 나옵니다.
2. `addAction()`은 그 클래스 이름(`action.className`)으로 `analysis_symbol`에서 타입을 찾습니다(`selectTypeLocation`).
3. 타입을 찾으면 `updateLayerBySymbol`로 계층을 `CONTROLLER`로 바꿉니다. 이때 `layerBy`에는 `"struts-config"`를 남깁니다.

이 SQL은 `src/main/resources/sqlmap/analysis/SemanticDao.xml:393`에 있습니다.

```sql
UPDATE analysis_symbol
   SET layer = #{layer}, layer_confidence = 'HIGH'
     , properties_json = COALESCE(properties_json, '{}'::jsonb) || jsonb_build_object('layerBy', #{layerBy}::text)
 WHERE revision_id = #{revisionId}
   AND symbol_id = #{symbolId}
   AND layer IS NULL
```

**규칙 파일의 `layer:`와 어떻게 함께 동작하나**

- `SemanticPass.steps()`(`SemanticPass.java:105-107`)에는 `strutsEntries`가 `layerResolver`보다 앞에 있습니다. 그래서 Struts 액션 클래스는 규칙 파일을 보기 전에 `CONTROLLER`로 정해집니다.
- 규칙 처리기인 `LayerResolver`의 주석에도 이 순서가 "0) 규칙 없이 코드가 확실히 아는 것"으로 적혀 있습니다(`LayerResolver.java:21`).
- 이 UPDATE는 `layer IS NULL`일 때만 값을 씁니다. 그래서 다른 처리기가 먼저 정한 계층은 덮어쓰지 않습니다.
- 표의 "정하는 곳"에 `StrutsEntries`가 들어간 이유도 이것입니다. Struts 액션에는 보통 애노테이션이 없습니다. 그래서 규칙 파일이 없어도 설정 파일만 보고 `CONTROLLER`로 정합니다.
- 설정 파일에 적힌 클래스가 소스에 없으면(예: Struts가 제공하는 `ForwardAction`) 계층을 정하지 않습니다. 대신 진입점의 `properties_json`에 `external: true`를 남깁니다.

# localcommandcaveatTh

> 생성일시: 2026-11-11 14:45

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

---

## 질문

<pasted_content id="e6cc">
/docs/11.dstone-knowledge.md 의 아래 내용중 질문에 대답해줘.

<단락위치>
처리기별 상세설명 > 1. layer: 계층 (LayerResolver)

<내용>
**1-4. 저장하는 테이블과 컬럼.** `analysis_symbol`을 UPDATE 한다.

| 처리기 | 고치는 컬럼 | 값 |
|---|---|---|
| `StrutsEntries` | `layer`, `layer_confidence`, `properties_json.layerBy` | `CONTROLLER`, `HIGH`, `struts-config` |
| `LayerResolver`(JSP) | 같다 | `VIEW`, `HIGH`, `JSP` |
| `LayerResolver`(규칙) | `layer`, `layer_confidence`, `properties_json.layerRuleAt`, `properties_json.layerBy` | 규칙의 계층, `HIGH`/`MEDIUM`/`LOW`(1-2의 신뢰도), `파일:줄`(예: `common/spring.yml:7`), 적힌 모양(예: `@Service`). `layer IS NULL`인 클래스만 고친다 |

<질문>
`LayerResolver`(JSP) 에서 analysis_symbol.layer 를 수정한다고 했느데...
지금 분석된 testApp 의 경우 analysis_symbol.layer 가 VIEW 인 정보가 없는데?
</pasted_content id="e6cc">

---

## 답변

JSP 파일은 24개인데 JSP에서 만든 심볼이 하나도 없습니다. `JspToJava` 쪽을 확인해 보겠습니다.

---

## 답변

testApp에서는 `VIEW`가 나올 수 없습니다. testApp의 JSP 24개가 모두 **Java 코드(스크립틀릿)가 없는 JSP**이기 때문입니다. 코드가 잘못 동작한 것이 아니라 설계대로 나온 결과입니다.

**`VIEW`가 정해지는 조건**

`LayerResolver`가 부르는 `updateJspLayer`(`src/main/resources/sqlmap/analysis/SemanticDao.xml:412`)는 아래처럼 동작합니다.

```sql
UPDATE analysis_symbol s SET layer = 'VIEW', ...
  FROM analysis_file f
 WHERE f.file_id = s.file_id AND f.language = 'JSP'
```

JSP 파일에서 나온 심볼이 `analysis_symbol`에 있을 때만 값이 바뀝니다. 그 심볼은 DECLARE 단계에서 `JspToJava`가 JSP 안의 `<% %>`, `<%= %>`, `<%! %>`, `<jsp:useBean>`을 Java 클래스(`jsp.order.list_jsp` 같은 이름)로 바꿔야 생깁니다. Java 코드가 하나도 없는 JSP라면 `convert()`가 `null`을 돌려줍니다. 그러면 `DeclarePass`가 "Java 코드가 없는 JSP입니다."라며 그 파일을 건너뜁니다(`DeclarePass.java:133-135`). 심볼이 만들어지지 않으니 `VIEW`로 바꿀 대상도 없습니다.

**testApp에서 확인한 결과 (revision 116)**

- JSP 파일은 24개이고, 모두 `parse_status = PENDING`입니다. 건너뛴 파일은 상태를 바꾸지 않으므로 처음 값이 그대로 남아 있습니다.
- `analysis_symbol`에서 JSP 파일에 속한 행은 0건입니다. 계층이 붙은 심볼은 모두 JAVA 파일에서 나왔습니다.
- 원본 `D:\AppHome\testApp\src\main\webapp`를 grep으로 확인했습니다. JSP 안의 `<%`는 모두 `<%@ ... %>` 지시자이거나 `<%-- --%>` 주석입니다. 스크립틀릿은 하나도 없고, 화면은 JSTL과 EL로만 만들어져 있습니다.

**참고**

- 스크립틀릿이 없는 JSP도 화면으로는 분석됩니다. 컨트롤러가 돌려주는 뷰 이름과 JSP를 잇는 연결은 `JspLinker`가 따로 만듭니다(`RENDERS` 관계 등). 이때는 JSP를 심볼이 아닌 파일 단위로 다루므로 `analysis_symbol.layer`와는 관계가 없습니다.
- 1-4절 표의 "`LayerResolver`(JSP)" 줄에 "스크립틀릿이 있는 JSP만 해당한다"는 말을 붙이면 같은 오해를 줄일 수 있습니다. 원하시면 그 문장을 추가하겠습니다.

---

## 질문

응 추가해줘.

---

## 답변

1-4절 표와 같은 내용이 1-1절 계층 표, 뒤쪽 ② 계층 표에도 있습니다. 세 곳을 함께 고치겠습니다.

---

## 답변

The file uses single spaces; I'll fix the match.

---

## 답변

`docs/11.dstone-knowledge.md`에서 세 곳을 고쳤습니다. 1-4절 표와 같은 내용이 다른 표 두 곳에도 있어서 함께 맞췄습니다.

- **1-1절 계층 표, `VIEW` 줄**: 타입은 Java 코드(`<% %>`, `<%= %>`, `<%! %>`, `<jsp:useBean>`)가 있는 JSP에서만 생긴다는 문장을 넣었습니다.
- **1-4절 표, `LayerResolver`(JSP) 줄**: Java 코드가 있는 JSP만 해당한다고 적었습니다. JSTL과 EL만 쓴 JSP는 DECLARE 단계에서 건너뛰어 타입이 없고, 그래서 고칠 행도 없다는 설명도 붙였습니다.
- **뒤쪽 ② 계층 표(`analysis_symbol` UPDATE)**: Java 코드가 없는 JSP는 타입이 없어서 해당하지 않는다는 문장을 넣었습니다.

아직 커밋하지 않았습니다. 커밋할까요?

---

## 질문

올려줘.

---

## 답변

`docs/11.dstone-knowledge.md` 수정분을 커밋해서 원격 `main`에 푸시했습니다. 커밋은 `61ee2b03`(`docs: dstone-knowledge VIEW 계층은 Java 코드가 있는 JSP만 해당함을 명시`)이고, 로컬과 원격이 같은 상태입니다.

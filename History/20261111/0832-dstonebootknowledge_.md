# dstonebootknowledge_

> 생성일시: 2026-11-11 08:32

## 질문

dstone-boot>knowledge 에서
분석한 dstone-knowledge 프로젝트의 호출분석 성공률이 87.33% 밖에 안되는데 주요 원인이 뭐라고 보이나?

---

## 답변

Knowledge 화면의 "호출분석 성공률"이 어떻게 계산되는지부터 확인하겠습니다.

---

## 답변

지표는 `analysis_reference`의 CALL 중 RESOLVED/EXTERNAL 비율입니다. 실제 DB에서 나머지 12.67%가 무엇인지 보겠습니다.

---

## 답변

`classpath`가 비어 있는 게 눈에 띕니다. 상태별·원인별 분포를 보겠습니다.

---

## 답변

실패분은 HEURISTIC(짐작) 8.26% + UNRESOLVED 4.41%입니다. 각각 어떤 호출인지 이름별로 보겠습니다.

---

## 답변

`Unsolved symbol : BaseDao`가 핵심 단서로 보입니다. BaseDao가 어디 있는지 확인합니다.

---

## 답변

남은 HEURISTIC 610건(dstone-common 상속과 무관한 파일)의 원인도 확인합니다.

---

## 답변

## 원인: 분석할 때 라이브러리 jar를 하나도 주지 않았습니다

`dstone-knowledge` 프로젝트는 `local_path=/app/dstone/dstone-knowledge/src/main`으로 등록돼 있고 `classpath`는 비어 있습니다. 이 폴더 안에는 jar가 없습니다. 그래서 해석기가 볼 수 있는 것은 이 모듈의 소스와 JDK뿐입니다. dstone-common, Spring, MyBatis, JavaParser, SnakeYAML 같은 타입은 해석기가 알지 못합니다.

### 숫자 (revision 112, CALL 12,039건)

| 상태 | 건수 | 비율 |
|---|---|---|
| EXTERNAL | 8,214 | 68.23% |
| RESOLVED | 2,300 | 19.10% |
| **HEURISTIC** (이름으로 짐작) | 994 | 8.26% |
| **UNRESOLVED** | 531 | 4.41% |

87.33%는 EXTERNAL과 RESOLVED를 더한 값입니다. 나머지 12.67%가 HEURISTIC과 UNRESOLVED입니다.

### 원인 ① dstone-common이 빠져 있음 (실패의 약 절반)

- `BaseDao`, `BaseObject`, `BaseService`처럼 dstone-common 클래스를 상속하는 파일이 62개입니다. 이 파일들의 호출은 전체의 약 1/4인데, 실패한 1,525건 가운데 705건(46%)이 여기서 나왔습니다.
- `sqlSessionCommon/Batch.selectList·insert·update` 같은 호출 271건이 UNRESOLVED입니다. 오류 메시지는 `Unsolved symbol : BaseDao`입니다. `knowledge.common.biz.BaseDao`의 부모인 `net.dstone.common.biz.BaseDao`를 찾지 못하면 클래스 계층 전체를 풀 수 없습니다.
- `info`/`warn`/`error` 51건은 `BaseObject`에서 물려받은 로거 메소드입니다. 실패 사유는 "프로젝트 밖의 상위 타입에서 물려받은 메소드"입니다.
- 같은 클래스 안의 `private param(...)`을 부르는 호출도 HEURISTIC으로 떨어졌습니다. 메소드를 찾을 때 상위 타입까지 올라가다가 실패하는 것으로 보입니다.

### 원인 ② 외부 라이브러리 jar가 없음 (HEURISTIC의 대부분)

인자나 호출 대상 객체의 타입이 라이브러리 타입이면 해석기는 오버로드를 판단하지 못합니다. 그러면 이름만 보고 짐작하는 HEURISTIC으로 내려갑니다.

| 파일 | HEURISTIC | 걸리는 라이브러리 타입 |
|---|---|---|
| `RuleFileParser` | 178 | SnakeYAML `Node`/`NodeTuple` 인자 (`r.valueNode(keys, …)`, `r.error(...)`) |
| `DeclarationCollector` | 138 | JavaParser AST |
| `ReferenceResolver` | 65 | JavaParser AST |
| `JavaSqlCollector` / `SqlTableExtractor` | 54 / 44 | JavaParser / JSQLParser |

UNRESOLVED 중 `((MethodCallExpr) root).getScope()`, `((ScalarNode) node).getValue()`처럼 라이브러리 메소드가 돌려준 값에 이어서 부르는 호출도 같은 이유로 실패했습니다.

HEURISTIC은 대개 맞는 대상을 짚습니다. 예를 들어 `resourceDao.insertRelation`은 프로젝트 안에 같은 이름이 하나뿐입니다. 다만 지표에서는 실패로 셉니다.

## 해결 방법

분석기 코드를 고칠 필요는 없습니다. 원래 지원하는 `classpath`를 채우고 다시 분석하면 됩니다. `docs/11` 적용 진단에도 "호출 해석률 95% 미만이면 CLASSPATH"라고 적혀 있습니다.

```bash
cd /app/dstone/dstone-knowledge
mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout
```

이 출력을 프로젝트 등록의 `classpath`에 그대로 넣으면 됩니다. 출력에는 `~/.m2`에 설치된 `dstone-common-1.0.0-SNAPSHOT.jar`도 들어 있습니다. 원인 ①과 ②가 함께 풀리니 HEURISTIC과 UNRESOLVED는 대부분 사라질 것으로 예상합니다. 람다나 리플렉션처럼 지원 범위 밖인 호출은 일부 남습니다.

원하시면 classpath를 등록하고 다시 분석해서 바뀐 수치를 확인해 드리겠습니다.

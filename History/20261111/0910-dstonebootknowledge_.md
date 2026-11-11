# dstonebootknowledge_

> 생성일시: 2026-11-11 09:10

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

---

## 질문

여전히 91.07% 정도밖에 안되네. 모든 jar를 등록해야 하나?

---

## 답변

classpath에 `dstone-common.jar` 하나만 들어가 있네요. 남은 실패 원인을 기호별로 묶어 보겠습니다.

---

## 답변

꼭 모든 jar를 넣을 필요는 없습니다. 소스가 타입으로 직접 쓰는 라이브러리 몇 개만 있으면 됩니다. 지금 classpath에는 `/app/dstone/dstone-common/target/dstone-common.jar` 하나만 들어 있습니다.

## dstone-common을 넣은 뒤 바뀐 수치 (revision 112 → 113)

| 상태 | 112 | 113 |
|---|---|---|
| RESOLVED | 2,300 | 2,712 |
| HEURISTIC | 994 | 581 |
| UNRESOLVED | 531 | 494 |
| 성공률 | 87.33% | **91.07%** |

`BaseObject`에서 물려받은 로거 호출은 51건에서 14건으로 줄었습니다. DAO 안의 `param(...)`처럼 상위 타입 때문에 짐작으로 떨어지던 호출도 이번에는 풀렸습니다.

## 남은 실패는 거의 세 라이브러리에서 나옵니다

| 원인 | 건수 | 필요한 jar |
|---|---|---|
| `sqlSessionCommon/Batch.selectList·insert·update…` | UNRESOLVED 271 | `mybatis-spring` 하나로는 부족하고 `mybatis`도 있어야 합니다. `SqlSessionTemplate`은 mybatis-spring에 있지만 `selectList`는 mybatis의 `SqlSession` 인터페이스에 선언돼 있습니다 |
| `call.getScope().get()`, `node.getBegin()` 같은 AST 체인 호출과 `DeclarationCollector`·`ReferenceResolver`의 짐작 | 약 200 이상 | `javaparser-core` |
| `RuleFileParser`의 `r.valueNode(keys, …)`처럼 `NodeTuple`을 인자로 받는 호출 | HEURISTIC 178 | `snakeyaml` |
| `SqlTableExtractor`·`JavaSqlCollector` | HEURISTIC 약 100 | `jsqlparser` |

## 어떻게 넣을지

**가장 간단한 방법:** `mvn dependency:build-classpath` 출력을 그대로 넣습니다(jar 325개). 무엇이 빠졌는지 따질 필요가 없습니다. 출력은 `/tmp/claude-1000/-app-dstone/7b6c3428-5997-40c8-b71a-d215e356faaf/scratchpad/cp.txt`에 만들어 두었습니다.

**꼭 필요한 것만 넣는 방법:** 아래 6개면 위 표의 원인이 모두 해결됩니다. 기존 `target/dstone-common.jar`는 그대로 두거나 `.m2`에 있는 것으로 바꿔도 됩니다.

```
/home/jysn007/.m2/repository/net/dstone/dstone-common/1.0.0-SNAPSHOT/dstone-common-1.0.0-SNAPSHOT.jar
/home/jysn007/.m2/repository/org/mybatis/mybatis/3.5.19/mybatis-3.5.19.jar
/home/jysn007/.m2/repository/org/mybatis/mybatis-spring/4.1.0/mybatis-spring-4.1.0.jar
/home/jysn007/.m2/repository/com/github/javaparser/javaparser-core/3.28.1/javaparser-core-3.28.1.jar
/home/jysn007/.m2/repository/org/yaml/snakeyaml/2.6/snakeyaml-2.6.jar
/home/jysn007/.m2/repository/com/github/jsqlparser/jsqlparser/4.7/jsqlparser-4.7.jar
```

다만 이렇게 고르면 나중에 소스가 새 라이브러리를 쓰기 시작할 때 다시 빠질 수 있습니다. 그래서 jar를 전부 넣는 쪽을 권합니다. 어느 쪽이든 95% 이상은 나올 것으로 예상합니다. 람다 안의 호출이나 리플렉션처럼 지원 범위 밖인 것은 조금 남습니다.

원하시면 둘 중 하나로 classpath를 등록하고 다시 분석해서 수치를 확인해 드리겠습니다.

---

## 질문

지금 기존 데이터를 지웠어. 꼭 필요한 라이브러리만 추가해서 분석진행해줘.

---

## 답변

서버(4081)가 떠 있고 기존 데이터는 비어 있습니다. 같은 경로로 프로젝트를 다시 등록하고 jar 6개를 classpath에 넣은 뒤 분석을 시작합니다.

---

## 답변

Analysis A20261111001이 시작되었습니다. 끝날 때까지 기다립니다.

---

## 답변

분석이 RESOLVE 단계에서 돌고 있습니다. 끝나면 바로 결과를 확인하겠습니다.

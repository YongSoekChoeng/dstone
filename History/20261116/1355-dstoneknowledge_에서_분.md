# dstoneknowledge_에서_분

> 생성일시: 2026-11-16 13:55

## 질문

dstone-knowledge 에서 분석범위에 대한 제한설정이 어디에 있는지?
예를 들어서 특정 클래스에서 org.apache.* 에 속한 클래스를 사용한다면 org.apache 의 클래스는 분석범위에서 빠져야 할 것 같은데...

---

## 답변

코드 확인은 끝났고, 지금 답을 정리하고 있습니다. 하나만 더 확인하겠습니다.

---

## 답변

`org.apache.*` 같은 패키지를 빼 달라는 설정은 따로 없습니다. 필요 없게 짜여 있기 때문입니다. 분석 대상은 패키지가 아니라 **프로젝트 폴더 안에 있는 소스 파일**로 정해집니다.

## 분석 범위가 정해지는 방식

| 단계 | 하는 일 | 관련 위치 |
|---|---|---|
| SCAN | 프로젝트 루트 아래 파일만 목록에 넣음 | `ScanPass.java`, `dstone.knowledge.scan.*` |
| RESOLVE | 호출이나 참조가 무엇을 가리키는지 찾음. jar는 타입 이름을 알아내는 데만 씀 | `ResolvePass.java:108` (`ClasspathResolver`, 프로젝트 설정 `classpath`) |

그래서 내 클래스가 `org.apache.commons.io.FileUtils.readFileToByteArray()`를 부르면 이렇게 됩니다.
- `FileUtils`는 프로젝트 소스에 없으니 파싱하지 않습니다. 클래스, 메소드, 청크도 만들지 않습니다.
- 호출 관계 하나만 남습니다. 받는 쪽은 `to_kind = EXTERNAL_METHOD`, `to_id = NULL`이고 `to_external`에 `org.apache.commons.io.FileUtils.readFileToByteArray`라는 이름이 들어갑니다. 참조 상태는 `EXTERNAL`입니다(`ReferenceResolver.java:540-541`).
- 호출 그래프 API는 기본값이 `includeExternal=false`라서 이런 외부 호출을 보여 주지 않습니다(`RevisionController.java:201`, `RelationDao.xml:467`).
- RAG 문서에는 외부 호출이 메소드마다 최대 8개까지 이름으로만 들어갑니다(`CodeDocumentBuilder.java:41`).

정리하면 `org.apache`는 들여다보지 않고 "밖으로 나가는 호출이 있다"는 사실만 기록됩니다. 영향도를 볼 때도 끊기지 않게 하려고 일부러 남기는 정보입니다.

## 범위를 줄이는 설정 (`conf/application.yml`의 `dstone.knowledge.scan`)

- `exclude-dirs`: 훑지 않을 폴더 이름
- `max-file-bytes`: 이보다 큰 파일은 분석하지 않음(SKIPPED)

예외가 하나 있습니다. 프로젝트 폴더 안에 `org/apache/...` 소스가 복사돼 들어 있으면 내 코드로 보고 분석합니다. 그런 폴더를 빼는 방법은 지금은 `exclude-dirs`뿐입니다.

## 확인하다 찾은 문제

`exclude-dirs`에 있는 `/test/` 항목은 실제로 동작하지 않습니다. `ScanPass.java:136`이 비교하는 값은 폴더 **이름**(`dir.getFileName()`, 예: `test`)이라서 `/test/`와는 절대 같아질 수 없습니다. 그래서 지금 `src/test/...`도 분석되고 있을 겁니다. 고치는 방법은 두 가지입니다.
1. `test`로 바꿉니다. 다만 이름이 `test`인 폴더는 위치와 상관없이 전부 빠집니다.
2. `ScanPass`가 `/`로 시작하는 항목을 경로 조각으로 비교하게 고칩니다(예: `/src/test/`).

원하시면 고쳐 드리겠습니다. 특정 패키지(예: 프로젝트 안에 복사된 `org.apache` 소스)를 빼는 `exclude-packages` 같은 설정을 추가할 수도 있습니다.

---

## 질문

응 test 만 고쳐줘.

---

## 답변

`exclude-dirs`의 `/test/`를 `test`로 고쳤습니다.

- `dstone-knowledge/conf/application.yml:99`: `...,.metadata,/test/"`를 `...,.metadata,test"`로 바꿨습니다.
- `docs/11.dstone-knowledge.md` 2-1절 "건너뛸 폴더" 줄에 두 가지를 적었습니다. `test`가 들어간 이유와, 폴더 이름만 비교하므로 경로 형식으로 적으면 맞지 않는다는 점입니다.

참고할 점:
- 이제 위치와 상관없이 이름이 `test`인 폴더는 모두 빠집니다. 예를 들어 `src/main/java/.../test/` 같은 업무 패키지 폴더도 분석되지 않습니다.
- 4081 포트에서 떠 있는 인스턴스에는 아직 반영되지 않았을 수 있습니다. 반영하려면 다시 띄우고, 기존 프로젝트는 다시 분석하세요.
- 커밋은 하지 않았습니다. 작업 트리에는 이전부터 있던 `ReferenceResolver.java` 변경과 `analyzer-version 1.7` 변경도 함께 남아 있습니다.

---

## 질문

커밋하고 푸시해줘

---

## 답변

변경을 두 커밋으로 나눠 올리겠습니다. 하나는 전체 이름 타입 해석(1.7)이고, 다른 하나는 `test` 폴더 제외입니다.

---

## 답변

첫 번째 커밋의 패치 적용이 실패했습니다. 그런데 두 번째 커밋은 그대로 진행돼 푸시까지 됐습니다. 무엇이 들어갔는지 확인하겠습니다.

---

## 답변

이미 푸시된 커밋은 고쳐 쓰지 않겠습니다. 빠진 `ReferenceResolver.java`를 따로 커밋해서 푸시합니다.

---

## 답변

커밋 두 개를 `main`에 푸시했고, 작업 트리에 남은 변경은 없습니다.

| 커밋 | 내용 |
|---|---|
| `fa5eb956` | `exclude-dirs`의 `/test/`를 `test`로 고침, 문서 수정, `analyzer-version 1.7` |
| `af11121f` | `ReferenceResolver.java`: jar가 없어도 패키지부터 다 적은 타입 이름을 타입으로 봄 |

처음에는 버전 변경(1.7)과 `ReferenceResolver.java`를 한 커밋으로 묶을 생각이었습니다. 그런데 `application.yml`의 버전 줄만 따로 담는 데 실패했고, 그 사이 `test` 커밋이 yml 전체를 담은 채 먼저 푸시됐습니다. 그래서 `fa5eb956`에는 1.7 버전 변경도 함께 들어가 있습니다. 이미 푸시한 기록을 다시 쓰지 않으려고, 이 사정은 `af11121f`의 메시지에 적어 두었습니다.

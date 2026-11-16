# dstoneknowledge_에서_분

> 생성일시: 2026-11-16 13:51

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

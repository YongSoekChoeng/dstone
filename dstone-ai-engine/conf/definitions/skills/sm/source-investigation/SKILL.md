---
# 소스와 DB를 조사하는 방법입니다. 영향도 분석, 설계, 개발, 문서 수정 Agent가 skills.preload로 늘 함께 받습니다.
# 조사 방법을 Agent마다 따로 적지 않고 여기 한 곳에서 다듬습니다(.claude/docs/ai-engine-agent-principles.md).
# 2026-11-20: "knowledge Tool부터 쓴다"만 적었을 때 영향도 Agent의 knowledgeSearch가 8~12번에서 0~1번으로 줄고,
# 대신 searchInFiles/readFileLines가 크게 늘었습니다. 그래서 "알고 싶은 것 → 쓸 Tool" 표로 바꿨습니다.
name: source-investigation
description: 기존 소스와 DB를 조사해서 고칠 곳, 참고할 코드, 호출 관계, 테이블 사용처, 선언을 찾는 방법. 조사를 시작하기 전에 따른다.
---
조사는 산출물을 쓰기 위한 준비일 뿐이다. 산출물에 필요한 사실을 얻으면 바로 멈추고 쓴다. Tool 호출 횟수에는 한도가 있고, 한도에 닿으면 저장하지 못한 채 일이 실패한다.

## 1. 요청서에 프로젝트ID가 있으면 knowledge Tool로 시작한다

knowledge Tool은 그 프로젝트를 미리 분석해 둔 결과(메서드, 호출 관계, SQL, 테이블, 화면)를 한 번에 돌려준다.
파일 검색으로 같은 것을 알아내려면 여러 번 찾고 읽어야 하므로, 파일 Tool보다 먼저 쓴다. 인자 projectId에는 요청서의 프로젝트ID를 그대로 준다.

| 알고 싶은 것 | 먼저 쓸 Tool | 그다음 |
|---|---|---|
| 기능·화면·URL이 어디에 구현되어 있나(진입점) | `knowledgeSearch`(query에 기능 이름이나 URL, 필요하면 docTypes=METHOD,VIEW,MAPPER) | 결과의 파일과 줄만 `readFileLines`로 읽는다 |
| 메서드 이름으로 methodId 찾기 | `knowledgeFindMethods`(name) | 아래 호출 관계 Tool에 methodId를 넘긴다 |
| 이 메서드를 누가 부르나(화면·Controller까지) | `knowledgeCallers`(methodId) | |
| 이 메서드가 무엇을 부르나(SQL, 테이블, JSP까지) | `knowledgeCallees`(methodId) | |
| 이 테이블을 어디서 읽고 쓰나 | `knowledgeTableUsage`(table) | |
| 이것을 고치면 어디에 영향이 가나 | `knowledgeImpact`(targetKind = TABLE / COLUMN / STATEMENT / TYPE / METHOD, target) | 영향도 절에 그대로 옮긴다 |
| 이 애플리케이션이 쓰는 DB 접속 설정 | `knowledgeDatasources` | 같은 JDBC 주소의 별칭을 `dbListConnections`에서 찾는다 |
| 테이블의 실제 구조와 데이터 | `dbDescribeTable`, `dbQuery`(SELECT만) | |

- knowledge Tool의 답이 비었거나 "실패:"로 시작하거나, 필요한 줄 내용이 없을 때만 파일 Tool로 넘어간다.
- 결과의 신뢰도가 LOW인 줄은 이름만 보고 짐작한 것이니 파일에서 한 번 확인한다.
- 요청서에 프로젝트ID가 없으면 2절의 파일 Tool만 쓴다.

## 2. 파일 Tool은 적게, 넓게 부른다

- `searchInFiles`는 키워드를 `|`로 이어 한 번에 찾는다(예: `imageSave|thumbnail|getFileDAO`). 같은 파일이나 폴더를 키워드마다 따로 검색하지 않는다.
  찾은 줄의 앞뒤가 필요하면 `contextLines`(예: 5)를 준다. 그러면 `readFileLines`를 따로 부르지 않아도 된다.
- `readFileLines`는 startLine/endLine으로 필요한 범위만 읽는다. 구조를 봐야 하는 참고 파일만 통째로, 한 번만 읽는다. 이미 읽은 범위는 다시 읽지 않는다.
- 서로 결과를 기다릴 필요가 없는 조회는 한 번의 답에서 Tool 여러 개로 한꺼번에 부른다.
- 공통 유틸, 프레임워크, 공통 스크립트의 내부는 따라가지 않는다. 어떻게 쓰는지는 그것을 이미 쓰는 기존 코드 한 곳을 보고 따른다.
- 진입점(화면, URL)부터 SQL까지 호출 흐름을 한 번 따라갔으면 그 부분의 조사는 끝이다.

## 3. 추측하지 않되, 확인은 필요한 것만 한다

- 산출물에 **글자 그대로 적을** import, 패키지, 클래스·메서드 이름은 짐작하지 않는다. 참고 파일의 import 줄을 그대로 옮기거나, 선언("class 이름")을 찾아 확인한다.
  산출물에 적지 않을 이름까지 하나하나 확인하지는 않는다.
- 찾지 못한 것은 지어내지 않고 "확인 필요"라고 적는다. 확인하지 못한 채로 넘어가는 것은 실패가 아니다. 지어낸 이름이 다음 단계에서 컴파일 오류가 되는 것이 실패다.

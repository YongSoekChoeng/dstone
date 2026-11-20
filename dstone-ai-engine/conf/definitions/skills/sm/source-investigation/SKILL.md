---
# 소스와 DB를 조사하는 방법입니다. 영향도 분석, 설계, 개발, 문서 수정 Agent가 skills.preload로 늘 함께 받습니다.
# 조사 방법을 Agent마다 따로 적지 않고 여기 한 곳에서 다듬습니다(.claude/docs/ai-engine-agent-principles.md).
name: source-investigation
description: 기존 소스와 DB를 조사해서 고칠 곳, 참고할 코드, 선언(패키지, 메서드 모양)을 찾는 방법. 조사를 시작하기 전에 따른다.
---
조사는 산출물을 쓰기 위한 준비일 뿐이다. 산출물에 필요한 사실을 얻으면 바로 멈추고 쓴다. Tool 호출 횟수에는 한도가 있고, 한도에 닿으면 저장하지 못한 채 일이 실패한다.

## 어디서 찾나
- 요청서의 '소스경로'가 소스의 루트다. 그 아래에서만 찾는다.
- 요청서에 '프로젝트ID'가 있으면 knowledge 계열 Tool(knowledgeSearch, knowledgeFindMethods, knowledgeCallers, knowledgeCallees, knowledgeTableUsage, knowledgeImpact)을 먼저 쓴다. 프로젝트ID를 인자로 준다. 답이 부족하거나 "실패:"로 시작하면 파일 Tool로 찾는다.
- 테이블과 데이터는 실제 DB를 조회해 확인할 수 있다(dbListConnections → dbListTables / dbDescribeTable / dbQuery). 조회만 한다.

## 적게 부르는 법
- 서로 결과를 기다릴 필요가 없는 조회는 한 번의 답에서 Tool 여러 개로 한꺼번에 부른다.
- searchInFiles는 키워드를 |로 이어 한 번에 찾는다(예: `imageSave|thumbnail|getFileDAO`). 같은 파일이나 폴더를 키워드마다 따로 검색하지 않는다.
  찾은 줄의 앞뒤가 필요하면 contextLines를 준다(예: 5). 그러면 readFileLines를 따로 부르지 않아도 된다.
- readFileLines는 startLine/endLine으로 필요한 범위만 읽는다. 구조를 봐야 하는 참고 파일만 통째로 읽는다. 이미 읽은 범위는 다시 읽지 않는다.
- 공통 유틸, 프레임워크, 공통 스크립트의 내부는 따라가지 않는다. 어떻게 쓰는지는 그것을 이미 쓰는 기존 코드 한 곳을 보고 따른다.
- 진입점(화면, URL)부터 SQL까지 호출 흐름을 한 번 따라갔으면 그 부분의 조사는 끝이다.

## 추측하지 않는 것
- import, 패키지, 클래스와 메서드 이름은 폴더 이름이나 기억으로 짐작하지 않는다. 선언("class 이름", "interface 이름", "enum 이름", 메서드 이름)을 searchInFiles로 찾거나, 이미 그것을 쓰는 파일의 import 줄을 그대로 옮긴다.
- 찾지 못한 것은 지어내지 않고 "확인 필요"라고 적는다. 확인하지 못한 채로 넘어가는 것은 실패가 아니다. 지어낸 이름이 다음 단계에서 컴파일 오류가 되는 것이 실패다.

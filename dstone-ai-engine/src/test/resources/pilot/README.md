# pilot 신규 개발 경로 - 문서 틀 회귀 테스트 자료

`tools.template.TemplateRendererTest`가 읽는 파일입니다.

| 파일 | 내용 |
|---|---|
| `newdev-input.json` | Agent가 낸 데이터(요구사항/구성 정의, 참고 구현, 파일별 설계)를 흉내 낸 입력 |
| `newdev-expected.json` | 같은 입력에서 나와야 하는 결과 |

`newdev-expected.json`은 손으로 쓴 것이 아닙니다. 이 문서들을 Workflow YAML의 jq 표현식으로 만들던 때(2026-10-09 이전)에
그 jq 식을 jackson-jq 1.6.5로 실제로 실행해서 얻은 값입니다.

지금 쓰는 값은 둘뿐입니다.

| 키 | 견주는 것 |
|---|---|
| `newReqDoc` | 틀 `templates/pilot/newdev-requirements.md.mustache`가 만든 요구사항 정의서 |
| `newDesignDoc` | 틀 `templates/pilot/newdev-design.md.mustache`가 만든 설계서(검증 결과 글자는 `newDesignCheck` 값을 넣습니다) |

나머지 키(`newCheck`, `newImpactDoc`, `plan`, `newTasksDoc` 등)는 Workflow 전용 자바 코드가 있던 때의 기대값입니다.
그 코드는 범용 Tool(`checkData`, `filterList`, `editFileTexts`, `renderTemplate`)로 바뀌면서 문서와 검증의 모양이 일부러 달라졌기 때문에
더 이상 견주지 않습니다(참고용으로 남겨 두었습니다).

문서의 모양을 일부러 바꿨다면 `newdev-expected.json`의 해당 값을 새 모양으로 고칩니다.

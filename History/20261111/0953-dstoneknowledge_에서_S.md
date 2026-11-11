# dstoneknowledge_에서_S

> 생성일시: 2026-11-11 09:53

## 질문

dstone-knowledge 에서 
SCAN 단계에서는 jsp 파일은 건너뛰나?

---

## 답변

아니요, SCAN 단계는 JSP를 건너뛰지 않고 스캔 대상에 넣습니다.

**JSP로 분류되는 확장자** (`pass01/scan/FileClassifier.java:37`)
- `.jsp`, `.jspf`, `.jspx`, `.tag`는 `language = "JSP"`, `fileType = "JSP"`로 분류됩니다.

**SCAN이 JSP 파일에 하는 일** (`ScanPass.java:178` `scanFile`)
- 파일을 읽어 체크섬, 인코딩, 줄 수를 구합니다.
- `analysis_file`에 `parseStatus = "PENDING"`으로 한 행을 씁니다.
- 내용은 파싱하지 않습니다. JSP 내용은 뒤 단계에서 읽습니다.

**JSP라도 SCAN에서 빠지거나 분석에서 제외되는 경우는 두 가지입니다.**
1. **제외 폴더 아래에 있을 때**: `dstone.knowledge.scan.exclude-dirs`에 지정된 폴더(`target`, `build`, `node_modules` 등) 안의 파일은 아예 훑지 않습니다.
2. **5MB를 넘을 때**: `max-file-bytes`(기본 5MB)보다 크면 행은 남지만 `parseStatus = "SKIPPED"`가 되어 분석에서 빠집니다.

참고로 `conf/application.yml:93`의 `exclude-dirs`에 `/test/`가 들어 있는데, 이 값은 동작하지 않습니다. `ScanPass.preVisitDirectory`는 폴더 이름 하나(`dir.getFileName()`)만 비교하므로 슬래시가 들어간 값과는 일치할 수 없습니다. 그래서 지금은 `test` 폴더도 스캔됩니다. 원래 `test` 폴더를 빼려던 설정이라면 고쳐야 합니다. 원하시면 수정하겠습니다.

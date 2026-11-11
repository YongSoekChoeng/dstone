# dstoneknowledge_에서_S

> 생성일시: 2026-11-11 10:24

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

---

## 질문

DeclarePass 에서는 java, jsp 만 처리한다고 했는데 해당 파일타입을 필터링하는 부분은 어디에 있나?

---

## 답변

Now trace into FilePassRunner and the SQL.

---

## 답변

필터링은 SQL에서 합니다. Java 코드에서 확장자를 거르는 부분은 없고, 언어 값을 넘겨 받은 `INSERT` 문의 `WHERE` 절이 대상을 고릅니다.

**1. `DeclarePass`가 언어를 지정해 두 번 실행합니다** (`src/main/java/net/dstone/knowledge/analysis/pass03/declare/DeclarePass.java:93`)
```java
filePassRunner.run(context, NAME, "JAVA", handler);
filePassRunner.run(context, NAME, "JSP", handler);
```

**2. `FilePassRunner.run()`이 그 언어 값을 `seedFilePass`에 넘깁니다** (`src/main/java/net/dstone/knowledge/analysis/job/FilePassRunner.java:70`)
```java
filePassDao.seedFilePass(revisionId, pass, language);
```

**3. 실제 필터링은 이 `INSERT`의 `WHERE`가 합니다** (`src/main/resources/sqlmap/analysis/FilePassDao.xml:9`)
```sql
INSERT INTO analysis_file_pass (file_id, pass, revision_id, status)
SELECT file_id, #{pass}, revision_id
     , CASE WHEN parse_status = 'SKIPPED' THEN 'SKIPPED' ELSE 'PENDING' END
  FROM analysis_file
 WHERE revision_id = #{revisionId}
   AND language = #{language}     -- 여기서 걸러짐
ON CONFLICT (file_id, pass) DO NOTHING
```
`language`가 `JAVA` 또는 `JSP`인 파일만 `analysis_file_pass`에 이 단계의 작업 행으로 들어갑니다. 이 값은 SCAN 단계에서 `FileClassifier.languageOf()`가 정해 둔 것입니다.

**4. 처리할 파일은 이 작업 행에서 꺼냅니다** (`selectPendingFiles`, 같은 XML 28행)
`analysis_file_pass`에서 `pass = 'DECLARE'`이고 `status = 'PENDING'`인 행만 읽습니다. 3번에서 행이 만들어지지 않은 XML, YAML 같은 파일은 이 단계에서 아예 보이지 않습니다.

`parseFile()` 안에서도 대상을 한 번 더 거릅니다 (`DeclarePass.java:124`).
- Java 파일 중 패키지가 `rootPackages` 밖에 있으면 건너뜁니다. JSP는 패키지가 없으므로 이 조건을 적용하지 않습니다.
- JSP는 `JspToJava.convert()`로 Java 소스로 바꾼 뒤 Java와 같은 방식으로 파싱합니다. Java 코드가 없는 JSP는 `"Java 코드가 없는 JSP입니다."`라는 사유로 SKIPPED 처리됩니다.

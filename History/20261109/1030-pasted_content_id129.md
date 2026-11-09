# pasted_content_id129

> 생성일시: 2026-11-09 10:30

## 질문

<pasted_content id="1290">
dstone-boot> knowledge > 프로젝트 · 분석 의 hen 프로젝트 에서
분석결과가 아래와 같이 나왔어.
"!메소드 호출 44,654건 가운데 94.59% 가 누구를 부르는지 풀렸습니다. 쓸 수는 있지만 빠진 호출이 있을 수 있습니다. 라이브러리(jar) 위치를 확인하세요."
그런데 분석률을 높이려면 어떤 부분에서 접근해야 하는지 명확하지가 않은 것 같아.
예를들어 "풀지 못한 참조의 이유"를 추적하고자 할 때 어떤 소스에서 어떤 호출이 안풀렸다던가 하는 내용들이 없어.
적당한 범위에서 상세 목록을 참고할 수 있는 방법이 없을까?
</pasted_content id="1290">

---

## 답변

먼저 해결률을 어떻게 계산하는지, 풀리지 않은 호출이 DB에 어떤 형태로 남는지 확인하겠습니다.

---

## 답변

이미 "못 푼 이유"를 묶어 세는 쿼리가 있네요. 테이블 구조와 API·화면 노출 상태를 보겠습니다.

---

## 답변

현재 요약 화면에는 이유별 건수와 예시 한 건만 나옵니다. 실제 hen 데이터에서 어떤 이유가 많은지 DB로 확인해 보겠습니다.

---

## 답변

해석기 오류 뒤에 "어떤 기호를 못 찾았는지"가 들어 있습니다. 이걸로 묶으면 원인이 바로 보일 것 같습니다.

---

## 답변

## hen 프로젝트 분석 결과(리비전 85)

**원인은 대부분 jar가 없어서였습니다.** hen 프로젝트에는 클래스패스가 등록돼 있지 않습니다. 그래서 `WEB-INF/lib`에 있는 jar 26개만 자동으로 읽혔습니다. Spring, slf4j, HttpClient, jjwt처럼 `pom.xml`로 받는 라이브러리는 그 폴더에 없어서 분석기가 그 타입들을 알지 못했습니다.

못 찾은 기호를 많은 순서로 묶어 보면 이렇습니다.

| 못 찾은 기호 | 건수 | 필요한 jar |
|---|---|---|
| `Model` (`new ModelAndView(...).addObject`) | 378 | spring-webmvc / spring-context |
| `logger` | 168 | slf4j / log4j |
| `HttpEntity`, `GET`/`POST`, `HttpStatus` | 약 130 | spring-web |
| `StringEntity`, `UrlEncodedFormEntity` | 8 | httpclient |
| `Jwts.parserBuilder()` 등 | - | jjwt |

못 푼 호출이 몰린 파일은 `common/integration/*SendBiz.java`(외부 연계 쪽, RestTemplate 사용)였습니다.

**바로 해 볼 수 있는 조치:** `/app/hen`에서 `mvn dependency:build-classpath`를 실행하고, 그 출력을 프로젝트 등록 화면의 라이브러리 칸에 그대로 넣은 다음 다시 분석하면 됩니다. 위 표 기준으로 못 푼 호출 996건 가운데 700건 이상은 풀릴 것으로 보입니다.

덧붙여, 화면의 94.59%에는 짐작으로 이은 호출(HEURISTIC) 1,419건이 빠져 있습니다. 전체는 이렇게 나뉩니다.

- EXTERNAL: 37,761건
- RESOLVED: 4,478건
- HEURISTIC: 1,419건
- UNRESOLVED: 996건

## 상세 목록을 볼 방법

**지금 화면에 없는 것:** 요약의 "풀지 못한 참조의 이유"는 이유의 첫 문장으로 묶은 건수와 예시 한 건만 보여 줍니다. 그런데 925건이 "프로젝트에 같은 이름의 메소드가 없습니다"라는 한 묶음으로 뭉쳐서, 무엇이 문제인지 알 수 없습니다. 실제 단서는 그 뒤 `해석기 오류: Unsolved symbol : Model` 부분에 있습니다.

**쓸 수 있는 데이터:** 파일, 줄, 호출식, 이유가 모두 `analysis_reference`에 건별로 이미 저장돼 있습니다. 그래서 새로 분석할 필요 없이 조회와 화면만 추가하면 됩니다.

### 지금 바로 SQL로 보기

```sql
-- 못 찾은 기호별 건수 (어떤 jar가 빠졌는지)
SELECT substring(fail_reason FROM 'Unsolved symbol[^:]*: ([^ ]+)') AS sym, count(*)
  FROM analysis_reference
 WHERE revision_id = 85 AND ref_kind = 'CALL' AND status = 'UNRESOLVED'
 GROUP BY 1 ORDER BY 2 DESC;

-- 건별 상세 (파일·줄·호출식)
SELECT f.path, r.line_start, r.scope_text || '.' || r.name AS call, r.fail_reason
  FROM analysis_reference r JOIN analysis_file f ON f.file_id = r.file_id
 WHERE r.revision_id = 85 AND r.ref_kind = 'CALL' AND r.status = 'UNRESOLVED'
 ORDER BY f.path, r.line_start;
```

### 화면 기능으로 추가하는 안

1. **dstone-knowledge API**: `GET /api/revisions/{id}/unresolved`를 만듭니다.
   - 세 가지 기준으로 묶어 셉니다: 못 찾은 기호별, 파일별, 이유별.
   - 건별 목록은 묶음·파일·상태(UNRESOLVED/HEURISTIC)로 걸러 쪽 단위로 보여 줍니다.
2. **dstone-boot 화면**: 요약의 "풀지 못한 참조의 이유" 표에 상세 버튼을 붙입니다.
   - 왼쪽에는 기호별·파일별 상위 목록을 둡니다.
   - 하나를 누르면 오른쪽에 `파일:줄`, 호출식, 이유가 나옵니다.
   - 진단 문구도 "jar가 없어 보입니다: Model, HttpEntity…"처럼 구체적으로 바꿉니다.

분석 단계(Pass)는 손대지 않으므로 다시 분석할 필요가 없습니다.

이 안대로 구현할까요? 아니면 먼저 classpath를 넣고 다시 분석해서 얼마나 오르는지부터 확인할까요?

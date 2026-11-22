package net.dstone.ai.runtime.eval;

import java.util.List;
import java.util.Map;

/**
 * <pre>
 * 평가 사례 하나입니다. conf/eval 아래 *.eval.yml 파일 하나가 사례 하나입니다(EvalCaseLoader가 읽습니다).
 *
 * 사례는 "실제로 있었던 일을 문제로 고정해 둔 것"입니다. Agent 정의·Skill·모델을 바꾼 뒤 같은 문제를 다시 풀게 해서
 * 나아졌는지 잽니다(.claude/docs/ai-engine-agent-principles.md 4절).
 *
 *   id: troubleshoot-delete-db
 *   description: 사람이 본 증상과, 이 사례로 무엇을 재는지
 *   agent: sm-troubleshooter-agent
 *   fixture: sm/troubleshoot-delete      ← 자료 폴더(conf/eval 기준). 그 안의 work/ 가 작업 폴더로 복사된다
 *   copy: [01.customer_requirement.md]   ← work/ 가운데 복사할 것만(파일이나 폴더). 비우면 전부
 *   params:                              ← 실행할 때 바꿀 수 있는 값과 기본값
 *     sourceDir: /app/testApp
 *   request:                             ← 요청서(작업 폴더 위 요청 폴더에 00.request.md로 둠). 선택
 *     file: 00.request.md                   fixture 안의 파일
 *     append: |                             끝에 덧붙일 글
 *       ## 시험환경
 *   input:                               ← Agent input. 값 안의 자리는 아래 표
 *     base_dir: ${workDir}
 *   checks:                              ← 채점 기준. 하나에 종류 하나
 *     - name: 설계서 반영
 *       fileChanged: 03-1.design.md
 *
 * 값 안에 쓸 수 있는 자리(fixture 파일의 글자, request, input에서 모두 같음)
 *   ${workDir}      작업 폴더(fixture의 work/ 가 복사되는 곳)
 *   ${requestDir}   요청 폴더(작업 폴더의 부모. 요청서가 있는 곳)
 *   ${param.이름}    params 값(실행할 때 준 값, 없으면 기본값)
 *   ${request}      요청서 내용(input에서만)
 *   ${file:경로}     작업 폴더 기준 파일의 내용(input에서만)
 * fixture 파일의 다른 ${…}(예: JSP의 ${_csrf.token})는 건드리지 않습니다.
 * </pre>
 *
 * @param id          사례 id(파일 이름과 같게 둡니다)
 * @param description 무엇을 재는 사례인지
 * @param agent       부를 Agent id
 * @param fixture     자료 폴더(conf/eval 기준). 없으면 null
 * @param copy        work/ 가운데 복사할 파일·폴더(work/ 기준). null이면 전부
 * @param params      바꿀 수 있는 값과 기본값(적은 순서 유지)
 * @param requestFile 요청서로 쓸 fixture 안 파일. 없으면 null
 * @param requestAppend 요청서 끝에 덧붙일 글. 없으면 null
 * @param input       Agent input(값은 자리를 채우기 전 글자)
 * @param checks      채점 기준
 * @param path        사례 파일 경로(conf/eval 기준)
 */
public record EvalCase(String id, String description, String agent, String fixture, List<String> copy, Map<String, String> params, String requestFile,
	String requestAppend, Map<String, Object> input, List<Check> checks, String path) {

	/**
	 * <pre>
	 * 채점 기준 하나입니다. 종류(kind)마다 쓰는 값이 다릅니다.
	 *
	 *   answerContains   {field: causes, text: mb_id}   답의 그 칸(없으면 답 전체)에 글자가 있다
	 *   answerNotEmpty   verification                    답의 그 칸이 비어 있지 않다
	 *   answerEquals     {field: compiled, value: true}  답의 그 칸 값이 같다(글자로 비교)
	 *   fileExists       02.impact.md                    작업 폴더에 파일이 있다
	 *   fileChanged      03-1.design.md                  처음 복사한 내용과 달라졌다(없던 파일이 생긴 것도 포함)
	 *   fileContains     {file: 02.impact.md, text: TASK-01}
	 *   textIncreased    {files: [a.java, b.java], text: setMb_id}  파일들에서 글자가 나온 횟수 합이 처음보다 늘었다
	 *   toolUsed         {name: dbQuery, min: 1}         이름이 name으로 시작하는 Tool을 min번 이상 요청했다
	 * </pre>
	 *
	 * @param name  화면과 결과에 보일 이름
	 * @param kind  종류(위 표)
	 * @param value 종류에 딸린 값(글자 하나 또는 맵)
	 */
	public record Check(String name, String kind, Object value) {
	}

}

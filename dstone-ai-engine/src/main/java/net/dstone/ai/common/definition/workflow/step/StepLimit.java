package net.dstone.ai.common.definition.workflow.step;

/**
 * <pre>
 * step을 몇 번까지 실행할지 정하는 횟수 한도입니다. AGENT와 TOOL step에 limit 키로 적습니다.
 * 엔진이 step을 실행하기 전에 세고, 한도를 넘었으면 그 step을 실행하지 않고 over로 보냅니다.
 * 자동 수정·형식 고치기처럼 "몇 번까지만 되풀이"하는 고리를 판정 전용 step(countUp) 없이 적으려는 것입니다.
 *
 *   - id: step34
 *     type: AGENT
 *     agent: sm-auto-fix-agent
 *     limit:
 *       max: 2                 # 2번까지 실행
 *       scope: ${decisions:}   # 이 값이 바뀌면(사람이 새로 결정하면) 처음부터 센다. 비우면 실행 전체에서 센다
 *       over: step19           # 한도를 넘으면 실행하지 않고 갈 곳(step id, END, FAIL)
 *
 * 세는 규칙은 범용 Tool countUp과 같습니다(같은 scope 안에서 max번까지, scope 값이 바뀌면 처음부터).
 * 센 값은 실행 컨텍스트(limits)에 있어 승인 대기·재시작 뒤에도 이어집니다.
 * </pre>
 *
 * @param max   (필수)실행할 수 있는 최대 횟수(1 이상)
 * @param scope (옵셔널)이 값이 바뀌면 처음부터 셉니다. 보통 ${decisions:}(사람의 결정 하나마다)입니다. 비우면 실행 전체에서 셉니다
 * @param over  (필수)한도를 넘었을 때 갈 step의 id(또는 END, FAIL)
 */
public record StepLimit(Integer max, String scope, String over) {
}

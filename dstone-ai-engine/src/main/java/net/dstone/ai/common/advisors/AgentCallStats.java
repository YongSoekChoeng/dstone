package net.dstone.ai.common.advisors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * <pre>
 * Agent 호출 한 번(재시도 포함)이 LLM을 몇 번 불렀고, 어떤 Tool을 몇 번 요청했고, 토큰을 얼마나 썼는지 모으는 그릇입니다.
 * 평가 실행(runtime.eval.EvalRunner)처럼 호출한 쪽이 숫자를 받아야 할 때 씁니다.
 *
 *   AgentCallStats stats = AgentCallStats.begin();
 *   try { agentExecutor.call(...); } finally { AgentCallStats.end(); }
 *   stats.rounds(), stats.toolCounts() ...
 *
 * AgentCallStatsAdvisor가 LLM 답마다 여기에 더합니다. 같은 스레드에서 부른 호출만 모입니다
 * (Agent의 Tool 실행은 호출한 스레드에서 돕니다. 백그라운드 Sub Agent는 다른 스레드라 들어가지 않습니다).
 * begin()을 부르지 않은 스레드에서는 아무것도 모으지 않습니다.
 * </pre>
 */
public final class AgentCallStats {

	private static final ThreadLocal<AgentCallStats> CURRENT = new ThreadLocal<AgentCallStats>();

	private int rounds = 0;
	private long promptTokens = 0;
	private long completionTokens = 0;
	private final Map<String, Integer> toolCounts = new TreeMap<String, Integer>();

	private AgentCallStats() {
	}

	/** 이 스레드에서 모으기 시작합니다. 돌려준 그릇에 숫자가 쌓입니다. */
	public static AgentCallStats begin() {
		AgentCallStats stats = new AgentCallStats();
		CURRENT.set(stats);
		return stats;
	}

	/** 이 스레드에서 모으기를 끝냅니다. begin()과 짝을 맞춰 finally에서 부릅니다. */
	public static void end() {
		CURRENT.remove();
	}

	/**
	 * LLM 답 하나를 지금 모으는 그릇에 더합니다. 모으는 중이 아니면 아무것도 하지 않습니다.
	 *
	 * @param toolNames        이 답이 요청한 Tool 이름들
	 * @param promptTokens     이 회차의 입력 토큰
	 * @param completionTokens 이 회차의 출력 토큰
	 */
	static void add(List<String> toolNames, long promptTokens, long completionTokens) {
		AgentCallStats stats = CURRENT.get();
		if (stats == null) {
			return;
		}
		synchronized (stats) {
			stats.rounds++;
			stats.promptTokens += promptTokens;
			stats.completionTokens += completionTokens;
			for (String name : toolNames) {
				Integer count = stats.toolCounts.get(name);
				stats.toolCounts.put(name, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			}
		}
	}

	/** LLM을 부른 횟수입니다. */
	public synchronized int rounds() {
		return this.rounds;
	}

	/** 입력 토큰 합입니다(회차마다 보낸 대화 전체를 더한 값이라 회차가 많을수록 빨리 늘어납니다). */
	public synchronized long promptTokens() {
		return this.promptTokens;
	}

	/** 출력 토큰 합입니다. */
	public synchronized long completionTokens() {
		return this.completionTokens;
	}

	/** Tool 이름별 요청 횟수입니다(이름 순). 복사본입니다. */
	public synchronized Map<String, Integer> toolCounts() {
		return new LinkedHashMap<String, Integer>(this.toolCounts);
	}

	/** Tool 요청 횟수 합입니다. */
	public synchronized int toolCalls() {
		int total = 0;
		for (Integer count : this.toolCounts.values()) {
			total += count.intValue();
		}
		return total;
	}

}

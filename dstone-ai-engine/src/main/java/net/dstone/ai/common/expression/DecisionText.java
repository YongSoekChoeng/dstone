package net.dstone.ai.common.expression;

import java.util.List;
import java.util.Map;

import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 실행 중에 내려진 결정(APPROVAL의 승인, SUPERVISOR의 판정)을 Agent가 읽을 글로 바꿉니다.
 * ${arrival}과 ${decisions}를 값 전체로 읽을 때 이 글이 들어갑니다(ContextResolver).
 *
 * 결정 하나의 모양(WorkFlowContext.recordDecision()이 만듭니다):
 *   { step, name, kind, decision, approver, comment, to, at }
 *
 * 글 모양:
 *   설계 승인(step11)의 결정: 재설계 (승인자 local)
 *   의견:
 *   1. 메뉴 URL ...
 * </pre>
 */
public final class DecisionText {

	private DecisionText() {
	}

	/**
	 * 결정 하나를 글로 바꿉니다. 값이 없으면 null입니다.
	 *
	 * @param decision 결정 기록 하나
	 */
	public static String of(Map<?, ?> decision) {
		if (decision == null) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		Object name = decision.get("name");
		Object step = decision.get("step");
		text.append(StringUtil.isEmpty(name == null ? null : name.toString()) ? step : name + "(" + step + ")");
		boolean verdict = "SUPERVISOR".equals(String.valueOf(decision.get("kind")));
		text.append(verdict ? "의 판정: " : "의 결정: ").append(decision.get("decision"));
		Object approver = decision.get("approver");
		if (approver != null && !StringUtil.isEmpty(approver.toString())) {
			text.append(" (승인자 ").append(approver).append(")");
		}
		Object comment = decision.get("comment");
		if (comment != null && !StringUtil.isEmpty(comment.toString().strip())) {
			text.append("\n").append(verdict ? "사유:" : "의견:").append("\n").append(comment.toString().strip());
		}
		return text.toString();
	}

	/**
	 * 결정 목록을 오래된 것부터 번호를 붙여 글로 바꿉니다. 목록이 비었으면 null입니다.
	 *
	 * @param decisions 결정 기록 목록
	 */
	public static String of(List<?> decisions) {
		if (decisions == null || decisions.isEmpty()) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		int no = 1;
		for (Object decision : decisions) {
			if (!(decision instanceof Map<?, ?> record)) {
				continue;
			}
			if (text.length() > 0) {
				text.append("\n\n");
			}
			text.append("[").append(no++).append("] ").append(of(record));
		}
		return text.length() == 0 ? null : text.toString();
	}

}

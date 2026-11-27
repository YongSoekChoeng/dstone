package net.dstone.ai.tools.file;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * 두 글의 줄 단위 차이를 unified diff 모양(@@ … @@, -지운 줄, +더한 줄, 앞뒤 몇 줄)으로 만듭니다.
 * 라이브러리 없이 쓰려고 단순하게 만들었습니다.
 *   1. 앞뒤로 같은 줄을 떼어 냅니다(고친 곳은 보통 몇 군데라 가운데가 작아집니다).
 *   2. 남은 가운데를 최장 공통 부분열(LCS)로 맞춥니다.
 *   3. 가운데가 너무 크면(줄 수의 곱이 MAX_CELLS를 넘으면) 줄 비교를 하지 않고 바뀐 범위만 알립니다.
 * </pre>
 */
final class LineDiff {

	/** 가운데 비교표의 칸 수 상한입니다. 넘으면 메모리와 시간을 아끼려고 줄 비교를 생략합니다. */
	private static final long MAX_CELLS = 4_000_000L;

	/** 바뀐 줄 앞뒤로 함께 보여 줄 줄 수입니다. */
	private static final int CONTEXT = 2;

	private LineDiff() {
	}

	/**
	 * 두 글의 차이를 돌려줍니다. 같으면 빈 글자입니다.
	 *
	 * @param before 지난 내용(없던 파일이면 빈 글자)
	 * @param after  지금 내용
	 */
	static String diff(String before, String after) {
		String[] a = lines(before);
		String[] b = lines(after);
		int start = 0;
		while (start < a.length && start < b.length && a[start].equals(b[start])) {
			start++;
		}
		int endA = a.length;
		int endB = b.length;
		while (endA > start && endB > start && a[endA - 1].equals(b[endB - 1])) {
			endA--;
			endB--;
		}
		if (start == endA && start == endB) {
			return "";
		}
		long cells = (long) (endA - start) * (long) (endB - start);
		if (cells > MAX_CELLS) {
			return "@@ -" + (start + 1) + "," + (endA - start) + " +" + (start + 1) + "," + (endB - start) + " @@\n"
				+ "(바뀐 범위가 커서 줄 비교를 생략합니다. 파일을 직접 읽어 보십시오.)\n";
		}
		// ops: ' ' 같은 줄, '-' 지운 줄, '+' 더한 줄. 각 줄의 원래 번호도 함께 둔다
		List<char[]> ops = new ArrayList<char[]>();
		List<String> texts = new ArrayList<String>();
		List<int[]> numbers = new ArrayList<int[]>();
		for (int i = Math.max(0, start - CONTEXT); i < start; i++) {
			add(ops, texts, numbers, ' ', a[i], i, i);
		}
		int n = endA - start;
		int m = endB - start;
		int[][] lcs = new int[n + 1][m + 1];
		for (int i = n - 1; i >= 0; i--) {
			for (int j = m - 1; j >= 0; j--) {
				lcs[i][j] = a[start + i].equals(b[start + j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
			}
		}
		int i = 0;
		int j = 0;
		while (i < n || j < m) {
			if (i < n && j < m && a[start + i].equals(b[start + j])) {
				add(ops, texts, numbers, ' ', a[start + i], start + i, start + j);
				i++;
				j++;
			} else if (i < n && (j >= m || lcs[i + 1][j] >= lcs[i][j + 1])) {
				// 지운 줄을 더한 줄보다 먼저 적는다(unified diff 관례)
				add(ops, texts, numbers, '-', a[start + i], start + i, start + j);
				i++;
			} else {
				add(ops, texts, numbers, '+', b[start + j], start + i, start + j);
				j++;
			}
		}
		for (int k = endA; k < Math.min(a.length, endA + CONTEXT); k++) {
			add(ops, texts, numbers, ' ', a[k], k, endB + (k - endA));
		}
		return hunks(ops, texts, numbers);
	}

	/** 같은 줄이 CONTEXT*2보다 길게 이어지는 곳에서 덩어리를 나눠 @@ 머리를 붙입니다. */
	private static String hunks(List<char[]> ops, List<String> texts, List<int[]> numbers) {
		StringBuilder out = new StringBuilder();
		int index = 0;
		while (index < ops.size()) {
			while (index < ops.size() && ops.get(index)[0] == ' ' && !changeWithin(ops, index, CONTEXT)) {
				index++;
			}
			if (index >= ops.size()) {
				break;
			}
			int from = index;
			int to = index;
			int sameRun = 0;
			while (to < ops.size()) {
				if (ops.get(to)[0] == ' ') {
					sameRun++;
					if (sameRun > CONTEXT * 2 && !changeWithin(ops, to, CONTEXT)) {
						break;
					}
				} else {
					sameRun = 0;
				}
				to++;
			}
			while (to > from && ops.get(to - 1)[0] == ' ' && !changeWithin(ops, to - 1, CONTEXT)) {
				to--;
			}
			int countA = 0;
			int countB = 0;
			for (int k = from; k < to; k++) {
				if (ops.get(k)[0] != '+') {
					countA++;
				}
				if (ops.get(k)[0] != '-') {
					countB++;
				}
			}
			out.append("@@ -").append(numbers.get(from)[0] + 1).append(',').append(countA)
				.append(" +").append(numbers.get(from)[1] + 1).append(',').append(countB).append(" @@\n");
			for (int k = from; k < to; k++) {
				out.append(ops.get(k)[0]).append(texts.get(k)).append('\n');
			}
			index = to;
		}
		return out.toString();
	}

	/** index 앞뒤 distance 줄 안에 바뀐 줄이 있는지 봅니다. */
	private static boolean changeWithin(List<char[]> ops, int index, int distance) {
		for (int k = Math.max(0, index - distance); k <= Math.min(ops.size() - 1, index + distance); k++) {
			if (ops.get(k)[0] != ' ') {
				return true;
			}
		}
		return false;
	}

	private static void add(List<char[]> ops, List<String> texts, List<int[]> numbers, char op, String text, int lineA, int lineB) {
		ops.add(new char[] { op });
		texts.add(text);
		numbers.add(new int[] { lineA, lineB });
	}

	/** 줄로 나눕니다. 줄 끝 \r은 뗍니다(Windows 줄바꿈만 다른 것을 바뀐 줄로 보지 않으려고). 빈 글자는 줄 0개입니다. */
	private static String[] lines(String text) {
		if (text == null || text.isEmpty()) {
			return new String[0];
		}
		String[] lines = text.split("\n", -1);
		if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
			String[] trimmed = new String[lines.length - 1];
			System.arraycopy(lines, 0, trimmed, 0, trimmed.length);
			lines = trimmed;
		}
		for (int k = 0; k < lines.length; k++) {
			if (lines[k].endsWith("\r")) {
				lines[k] = lines[k].substring(0, lines[k].length() - 1);
			}
		}
		return lines;
	}

}

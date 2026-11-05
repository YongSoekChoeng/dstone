package net.dstone.ai.runtime.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * Agent 호출 한 번 안에서 "어느 파일의 몇 번째 줄부터 몇 번째 줄까지 이미 읽었는지"를 적어 두는 장부입니다.
 * RereadGuardCallback이 이 장부를 보고, 이미 읽은 범위를 또 읽으려 하면 Tool을 실행하지 않고 안내만 돌려줍니다.
 *
 * 파일이 바뀌었으면 다시 읽어야 하므로, 읽을 때의 파일 상태(stamp: 고친 시각과 크기)를 함께 적습니다.
 * 상태가 달라졌으면 그 파일의 기록은 없는 것으로 봅니다.
 *
 * 이 클래스는 파일을 직접 건드리지 않는 순수한 장부라 단위 테스트로 확인할 수 있습니다.
 * Spring 빈이 아니며 Agent 호출마다 새로 만듭니다(AgentExecutor).
 * </pre>
 */
public class ReadHistory {

	/** "파일 끝까지"를 뜻하는 줄 번호입니다. */
	public static final int END = Integer.MAX_VALUE;

	/** 키(읽은 방식 + 경로)마다의 기록입니다. */
	private final Map<String, Entry> entries = new HashMap<String, Entry>();

	/** 한 파일을 한 방식으로 읽은 기록입니다. */
	private static class Entry {
		private final String stamp;
		private final List<int[]> ranges = new ArrayList<int[]>();

		private Entry(String stamp) {
			this.stamp = stamp;
		}
	}

	/**
	 * 읽은 범위를 적어 둡니다. 파일 상태가 앞의 기록과 다르면 앞의 기록은 버리고 새로 적습니다.
	 *
	 * @param key   읽은 방식과 경로를 합친 키(keyOf())
	 * @param stamp 읽을 때의 파일 상태
	 * @param from  읽은 첫 줄(1부터)
	 * @param to    읽은 마지막 줄. 끝까지 읽었으면 END
	 */
	public synchronized void record(String key, String stamp, int from, int to) {
		if (key == null || stamp == null || from > to) {
			return;
		}
		Entry entry = this.entries.get(key);
		if (entry == null || !entry.stamp.equals(stamp)) {
			entry = new Entry(stamp);
			this.entries.put(key, entry);
		}
		entry.ranges.add(new int[] { Math.max(1, from), to });
	}

	/**
	 * from~to가 모두 이미 읽은 범위 안에 있는지 봅니다. 파일 상태가 기록과 다르면 false입니다.
	 *
	 * @param to 끝까지면 END
	 */
	public synchronized boolean covers(String key, String stamp, int from, int to) {
		if (key == null || stamp == null) {
			return false;
		}
		Entry entry = this.entries.get(key);
		if (entry == null || !entry.stamp.equals(stamp)) {
			return false;
		}
		List<int[]> sorted = new ArrayList<int[]>(entry.ranges);
		Collections.sort(sorted, new Comparator<int[]>() {
			@Override
			public int compare(int[] a, int[] b) {
				return Integer.compare(a[0], b[0]);
			}
		});
		// 앞에서부터 이어 붙이며 "여기까지는 읽었다"는 줄(next)을 늘려 갑니다.
		long next = Math.max(1, from);
		for (int[] range : sorted) {
			if (range[0] > next) {
				break;
			}
			if (range[1] == END) {
				return true;
			}
			next = Math.max(next, (long) range[1] + 1);
			if (next > to) {
				return true;
			}
		}
		return next > to;
	}

	/** 이 경로의 기록을 모두 지웁니다. 파일을 고친 Tool을 부른 뒤에 씁니다. */
	public synchronized void forgetPath(String path) {
		if (path == null) {
			return;
		}
		String suffix = "|" + path;
		Iterator<String> keys = this.entries.keySet().iterator();
		while (keys.hasNext()) {
			if (keys.next().endsWith(suffix)) {
				keys.remove();
			}
		}
	}

	/**
	 * <pre>
	 * 기록을 모두 지웁니다.
	 * 대화가 길어져 오래된 Tool 결과를 줄였을 때(ToolResultCompactionAdvisor) 부릅니다. 그때는 앞에서 읽은 내용이
	 * 대화에 온전히 남아 있지 않으므로 다시 읽게 해야 합니다.
	 * </pre>
	 */
	public synchronized void clear() {
		this.entries.clear();
	}

	/** 읽은 방식(Tool 이름)과 경로를 합친 키입니다. 줄 번호가 붙은 결과와 안 붙은 결과는 따로 셉니다. */
	public static String keyOf(String toolName, String path) {
		return toolName + "|" + path;
	}

}

package net.dstone.knowledge.analysis.pass08.semantic.rule;

/**
 * <pre>
 * 규칙의 우선순위 단계. 규칙 파일이 들어 있는 폴더로 정해집니다.
 *
 *   PROJECT  conf/rules/projects/{프로젝트ID}/   그 프로젝트(또는 빌려 쓰는 프로젝트)에만 쓴다. 가장 먼저 본다
 *   COMMON   conf/rules/common/                 모든 프로젝트에 쓴다(Spring, MyBatis, 서블릿 ...)
 *   GUESS    conf/rules/guess/                  모든 프로젝트에 쓰지만 가장 약하다(@Component, 이름 끝말)
 *
 * 한 클래스에 규칙 여럿이 맞으면 단계가 앞선 것이 이깁니다. 같은 단계 안에서는 애노테이션 > 상속 > 이름 순서입니다.
 * </pre>
 */
public enum Tier {

	PROJECT("projects", "프로젝트 규칙"),
	COMMON("common", "공통 규칙"),
	GUESS("guess", "짐작 규칙");

	/** 규칙 폴더 아래의 폴더 이름 */
	private final String folder;
	private final String label;

	Tier(String folder, String label) {
		this.folder = folder;
		this.label = label;
	}

	public String getFolder() {
		return folder;
	}

	public String getLabel() {
		return label;
	}

	/** 폴더 이름으로 단계를 찾습니다. 모르는 폴더면 null */
	public static Tier ofFolder(String folder) {
		Tier[] all = values();
		for (int i = 0; i < all.length; i++) {
			if (all[i].folder.equals(folder)) {
				return all[i];
			}
		}
		return null;
	}

}

package net.dstone.knowledge.analysis.pass08.semantic.rule;

import java.util.ArrayList;
import java.util.List;

import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenMenu;
import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenScript;

/**
 * <pre>
 * 규칙 파일 하나(conf/rules 아래의 *.yml)를 읽은 결과입니다.
 *
 * 이름은 규칙 폴더 안의 경로에서 확장자를 뺀 것입니다. 예: common/spring, projects/hi-japan/backend
 * 우선순위 단계(tier)는 폴더가 정합니다(projects / common / guess). 파일 안에 순서나 적용 대상을 적지 않습니다.
 *
 * 규칙 목록은 파일의 절(layer, entry, sql ...)과 같은 이름으로 나뉩니다.
 * </pre>
 */
public class RuleFile {

	/** 예: common/spring, projects/hi-japan/backend */
	public final String name;
	/** 규칙 폴더 안의 경로. 예: common/spring.yml */
	public final String path;
	public final Tier tier;
	/** 프로젝트 규칙이면 projects/ 바로 아래 폴더 이름(예: hi-japan). 아니면 null */
	public final String projectFolder;
	public final String description;
	/** 파일 내용(대응표 포함)의 해시 앞 12글자. 해석기가 다 읽은 뒤 채운다 */
	String hash;

	/* layer: */
	public final List<Rule.Layer> layers = new ArrayList<Rule.Layer>();
	/* entry: */
	public final List<Rule.HttpEntry> httpEntries = new ArrayList<Rule.HttpEntry>();
	public final List<Rule.AnnotationEntry> annotationEntries = new ArrayList<Rule.AnnotationEntry>();
	public final List<Rule.ExtendsEntry> extendsEntries = new ArrayList<Rule.ExtendsEntry>();
	public final List<Rule.XmlEntry> xmlEntries = new ArrayList<Rule.XmlEntry>();
	/* inject: / proxy: */
	public final List<Rule.Mark> injects = new ArrayList<Rule.Mark>();
	public final List<Rule.Mark> proxies = new ArrayList<Rule.Mark>();
	/* sql: / view: */
	public final List<Rule.CallArgument> sqlCalls = new ArrayList<Rule.CallArgument>();
	public final List<Rule.CallArgument> viewCalls = new ArrayList<Rule.CallArgument>();
	/* screen: */
	public final List<ScreenScript.CallRule> screenCalls = new ArrayList<ScreenScript.CallRule>();
	public final List<ScreenScript.CallRule> screenFields = new ArrayList<ScreenScript.CallRule>();
	public final List<ScreenScript.CallRule> screenOpens = new ArrayList<ScreenScript.CallRule>();
	public final List<ScreenMenu.Rule> screenMenus = new ArrayList<ScreenMenu.Rule>();
	/* jpa: */
	public final List<Rule.Mark> jpaEntities = new ArrayList<Rule.Mark>();
	public final List<Rule.Mark> jpaTables = new ArrayList<Rule.Mark>();
	/** SNAKE_CASE / AS_IS. 적지 않았으면 null */
	public String jpaNaming;
	public final List<Rule.JpaQuery> jpaQueries = new ArrayList<Rule.JpaQuery>();
	public final List<Rule.JpaQuery> jpaNamedQueries = new ArrayList<Rule.JpaQuery>();
	/** Spring Data 저장소의 상위 타입 단순 이름 */
	public final List<String> jpaRepositories = new ArrayList<String>();
	/** 넘긴 Java 문자열이 JPQL 인 메소드 이름(createQuery) */
	public final List<String> jpqlMethods = new ArrayList<String>();

	RuleFile(String name, String path, Tier tier, String projectFolder, String description) {
		this.name = name;
		this.path = path;
		this.tier = tier;
		this.projectFolder = projectFolder;
		this.description = description;
	}

	/** 파일 내용(대응표 포함)의 해시 앞 12글자. 리비전마다 "어떤 규칙으로 분석했나"를 남길 때 쓴다 */
	public String getHash() {
		return hash;
	}

	/** 이 파일에 든 규칙 수. 리비전 기록과 화면에 보여 줄 때 쓴다 */
	public int ruleCount() {
		return layers.size() + httpEntries.size() + annotationEntries.size() + extendsEntries.size() + xmlEntries.size()
				+ injects.size() + proxies.size() + sqlCalls.size() + viewCalls.size()
				+ screenCalls.size() + screenFields.size() + screenOpens.size() + screenMenus.size()
				+ jpaEntities.size() + jpaTables.size() + (jpaNaming == null ? 0 : 1) + jpaQueries.size() + jpaNamedQueries.size()
				+ jpaRepositories.size() + jpqlMethods.size();
	}

}

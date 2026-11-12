package net.dstone.knowledge.analysis.pass08.semantic.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenMenu;
import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenScript;

/**
 * <pre>
 * 한 프로젝트의 분석에 쓰는 규칙 전체입니다. 고른 규칙 파일들을 우선순위 순서로 이어 붙인 것입니다.
 *
 * 순서: projects/{프로젝트} → common → guess. 같은 단계 안에서는 파일 이름 순, 파일 안에서는 적힌 순서입니다.
 * SEMANTIC 단계가 시작할 때 한 번 만들어 AnalysisJobContext 에 실어 두고, 처리기들이 자기 절의 규칙을 꺼내 씁니다.
 *
 *   layer()                         LayerResolver
 *   http/annotation/extends/xml...  EntryFinder
 *   injects() / proxies()           WiringMarker
 *   sqlCalls() / viewCalls()        SqlLinker / ViewLinker
 *   jpa...()                        JpaQueryReader, JavaSqlLinker
 *   screen...()                     ScreenLinker, ScreenMenuReader
 *
 * 같은 애노테이션으로 진입점을 만드는 규칙이 둘이면 진입점이 두 번 생기므로, 합칠 때 오류로 막습니다(두 자리를 알려 준다).
 * </pre>
 */
public class ProjectRules {

	private final List<RuleFile> files;

	/**
	 * <pre>
	 * 규칙 파일들을 우선순위(projects &gt; common &gt; guess, 같은 단계면 파일 이름) 순서로 정렬해 들고 있습니다.
	 * 같은 진입점 규칙이 두 파일에 있으면 진입점이 두 번 생기므로 여기서 바로 오류를 냅니다.
	 * </pre>
	 */
	public ProjectRules(List<RuleFile> files) {
		List<RuleFile> sorted = new ArrayList<RuleFile>(files);
		Collections.sort(sorted, new Comparator<RuleFile>() {
			@Override
			public int compare(RuleFile a, RuleFile b) {
				int byTier = Integer.compare(a.tier.ordinal(), b.tier.ordinal());
				return byTier != 0 ? byTier : a.name.compareTo(b.name);
			}
		});
		this.files = Collections.unmodifiableList(sorted);
		checkDuplicateEntries();
	}

	/** 쓰는 규칙 파일(우선순위 순서) */
	public List<RuleFile> getFiles() {
		return files;
	}

	/**
	 * <pre>
	 * 계층 규칙을 "먼저 볼 것부터" 늘어놓습니다. 앞의 것이 정한 계층은 뒤의 것이 바꾸지 않습니다.
	 *   1) 단계: 프로젝트 → 공통 → 짐작
	 *   2) 같은 단계 안에서 모양: 애노테이션 → 상속 → 이름
	 *   3) 그다음 파일 이름 순, 파일 안에서 적힌 순서
	 * </pre>
	 */
	public List<Rule.Layer> layers() {
		List<Rule.Layer> all = new ArrayList<Rule.Layer>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).layers);
		}
		final Map<Rule.Layer, Integer> position = new HashMap<Rule.Layer, Integer>();
		for (int i = 0; i < all.size(); i++) {
			position.put(all.get(i), Integer.valueOf(i));
		}
		Collections.sort(all, new Comparator<Rule.Layer>() {
			@Override
			public int compare(Rule.Layer a, Rule.Layer b) {
				int byTier = Integer.compare(a.tier.ordinal(), b.tier.ordinal());
				if (byTier != 0) {
					return byTier;
				}
				int byKind = Integer.compare(kindOrder(a.by), kindOrder(b.by));
				return byKind != 0 ? byKind : position.get(a).compareTo(position.get(b));
			}
		});
		return all;
	}

	/** 계층 규칙을 고르는 순서: 애노테이션(0) → 상속(1) → 이름(2) */
	private static int kindOrder(String by) {
		return Rule.Layer.BY_ANNOTATION.equals(by) ? 0 : Rule.Layer.BY_EXTENDS.equals(by) ? 1 : 2;
	}

	/** entry: http 규칙 전부(우선순위 순서) */
	public List<Rule.HttpEntry> httpEntries() {
		List<Rule.HttpEntry> all = new ArrayList<Rule.HttpEntry>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).httpEntries);
		}
		return all;
	}

	/** entry: annotation 규칙 전부(우선순위 순서) */
	public List<Rule.AnnotationEntry> annotationEntries() {
		List<Rule.AnnotationEntry> all = new ArrayList<Rule.AnnotationEntry>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).annotationEntries);
		}
		return all;
	}

	/** entry: extends 규칙 전부(우선순위 순서) */
	public List<Rule.ExtendsEntry> extendsEntries() {
		List<Rule.ExtendsEntry> all = new ArrayList<Rule.ExtendsEntry>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).extendsEntries);
		}
		return all;
	}

	/** entry: xml 규칙 전부(우선순위 순서) */
	public List<Rule.XmlEntry> xmlEntries() {
		List<Rule.XmlEntry> all = new ArrayList<Rule.XmlEntry>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).xmlEntries);
		}
		return all;
	}

	/** 주입 애노테이션. 같은 애노테이션이 여러 파일에 있으면 먼저 나온 것 하나 */
	public List<Rule.Mark> injects() {
		List<Rule.Mark> all = new ArrayList<Rule.Mark>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).injects);
		}
		return distinctMarks(all);
	}

	/** 프록시 애노테이션. 같은 애노테이션이 여러 파일에 있으면 먼저 나온 것 하나 */
	public List<Rule.Mark> proxies() {
		List<Rule.Mark> all = new ArrayList<Rule.Mark>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).proxies);
		}
		return distinctMarks(all);
	}

	/** sql: 규칙(SQL 이름을 인자로 받는 호출) 전부 */
	public List<Rule.CallArgument> sqlCalls() {
		List<Rule.CallArgument> all = new ArrayList<Rule.CallArgument>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).sqlCalls);
		}
		return all;
	}

	/** view: 규칙(화면 경로를 인자로 받는 호출) 전부 */
	public List<Rule.CallArgument> viewCalls() {
		List<Rule.CallArgument> all = new ArrayList<Rule.CallArgument>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).viewCalls);
		}
		return all;
	}

	/** 화면이 서버를 부르는 규칙: 함수 호출(call) 규칙 다음에 폼 필드(field) 규칙. 똑같은 규칙은 하나만 */
	public List<ScreenScript.CallRule> screenCalls() {
		Set<String> keys = new LinkedHashSet<String>();
		List<ScreenScript.CallRule> all = new ArrayList<ScreenScript.CallRule>();
		for (int i = 0; i < files.size(); i++) {
			addScreenRules(all, files.get(i).screenCalls, keys, "call");
		}
		for (int i = 0; i < files.size(); i++) {
			addScreenRules(all, files.get(i).screenFields, keys, "field");
		}
		return all;
	}

	/** 화면을 여는 규칙. 똑같은 규칙은 하나만 */
	public List<ScreenScript.CallRule> screenOpens() {
		Set<String> keys = new LinkedHashSet<String>();
		List<ScreenScript.CallRule> all = new ArrayList<ScreenScript.CallRule>();
		for (int i = 0; i < files.size(); i++) {
			addScreenRules(all, files.get(i).screenOpens, keys, "open");
		}
		return all;
	}

	/** 메뉴 목록 규칙. 똑같은 키 짝은 하나만 */
	public List<ScreenMenu.Rule> screenMenus() {
		Set<String> keys = new LinkedHashSet<String>();
		List<ScreenMenu.Rule> all = new ArrayList<ScreenMenu.Rule>();
		for (int i = 0; i < files.size(); i++) {
			List<ScreenMenu.Rule> menus = files.get(i).screenMenus;
			for (int j = 0; j < menus.size(); j++) {
				if (keys.add(menus.get(j).getNameKey() + "|" + menus.get(j).getPathKey())) {
					all.add(menus.get(j));
				}
			}
		}
		return all;
	}

	/** JPA 엔티티 애노테이션(단순 이름) */
	public List<String> jpaEntities() {
		List<Rule.Mark> all = new ArrayList<Rule.Mark>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).jpaEntities);
		}
		return names(distinctMarks(all));
	}

	/** JPA 테이블 애노테이션(단순 이름) */
	public List<String> jpaTables() {
		List<Rule.Mark> all = new ArrayList<Rule.Mark>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).jpaTables);
		}
		return names(distinctMarks(all));
	}

	/** @return 먼저 나온 파일의 naming. 아무도 적지 않았으면 SNAKE_CASE */
	public String jpaNaming() {
		for (int i = 0; i < files.size(); i++) {
			if (files.get(i).jpaNaming != null) {
				return files.get(i).jpaNaming;
			}
		}
		return "SNAKE_CASE";
	}

	/** jpa: 메소드에 붙는 쿼리 애노테이션 규칙 전부(예: &#64;Query) */
	public List<Rule.JpaQuery> jpaQueries() {
		List<Rule.JpaQuery> all = new ArrayList<Rule.JpaQuery>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).jpaQueries);
		}
		return all;
	}

	/** jpa: 이름 있는 쿼리 애노테이션 규칙 전부(예: &#64;NamedQuery) */
	public List<Rule.JpaQuery> jpaNamedQueries() {
		List<Rule.JpaQuery> all = new ArrayList<Rule.JpaQuery>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).jpaNamedQueries);
		}
		return all;
	}

	/** jpa: 리포지토리 상위 타입 이름들(겹치지 않게) */
	public List<String> jpaRepositories() {
		Set<String> all = new LinkedHashSet<String>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).jpaRepositories);
		}
		return new ArrayList<String>(all);
	}

	/** jpa: JPQL 글을 받는 메소드 이름들(겹치지 않게) */
	public List<String> jpqlMethods() {
		Set<String> all = new LinkedHashSet<String>();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(files.get(i).jpqlMethods);
		}
		return new ArrayList<String>(all);
	}

	/* ============================== 도우미 ============================== */

	/** 화면 호출 규칙을 target 에 더합니다. 내용이 똑같은 규칙(함수, 메소드 틀, 인자 자리, 변환이 모두 같은 것)은 한 번만 */
	private void addScreenRules(List<ScreenScript.CallRule> target, List<ScreenScript.CallRule> rules, Set<String> keys, String kind) {
		for (int i = 0; i < rules.size(); i++) {
			ScreenScript.CallRule rule = rules.get(i);
			if (keys.add(kind + "|" + rule.getFunction() + "|" + rule.getMethodTemplate() + "|" + rule.getArgIndex() + "|" + rule.getTransform().describe())) {
				target.add(rule);
			}
		}
	}

	/** 애노테이션 이름이 같은 표시 규칙은 먼저 나온 것(우선순위가 높은 것) 하나만 남깁니다. */
	private static List<Rule.Mark> distinctMarks(List<Rule.Mark> marks) {
		Set<String> seen = new LinkedHashSet<String>();
		List<Rule.Mark> distinct = new ArrayList<Rule.Mark>();
		for (int i = 0; i < marks.size(); i++) {
			if (seen.add(marks.get(i).annotation)) {
				distinct.add(marks.get(i));
			}
		}
		return distinct;
	}

	/** 표시 규칙들의 애노테이션 이름 목록 */
	private static List<String> names(List<Rule.Mark> marks) {
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < marks.size(); i++) {
			names.add(marks.get(i).annotation);
		}
		return names;
	}

	/** 같은 애노테이션으로 진입점을 만드는 규칙이 둘이면 진입점이 두 번 생긴다. 두 자리를 알려 준다 */
	private void checkDuplicateEntries() {
		Map<String, Rule> owner = new HashMap<String, Rule>();
		List<Rule.HttpEntry> http = httpEntries();
		for (int i = 0; i < http.size(); i++) {
			for (int j = 0; j < http.get(i).methodAnnotations.size(); j++) {
				Rule.HttpAnnotation a = http.get(i).methodAnnotations.get(j);
				claim(owner, "HTTP/" + a.annotation, a);
			}
		}
		List<Rule.AnnotationEntry> annotated = annotationEntries();
		for (int i = 0; i < annotated.size(); i++) {
			Rule.AnnotationEntry a = annotated.get(i);
			claim(owner, a.endpointType + "/" + a.annotation, a);
		}
	}

	/** key 를 이 규칙 몫으로 등록합니다. 이미 다른 규칙이 가져갔으면 어디에 있는지 알려 주며 오류를 냅니다. */
	private void claim(Map<String, Rule> owner, String key, Rule rule) {
		Rule previous = owner.put(key, rule);
		if (previous != null) {
			throw new RuleException(rule.file, rule.line, "진입점 규칙 " + key + " 이(가) " + previous.at() + " 에도 있습니다. 진입점이 두 번 생기므로 한쪽에만 적으세요.");
		}
	}

}

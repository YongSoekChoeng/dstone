package net.dstone.knowledge.semantic.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dstone.knowledge.semantic.ScreenMenu;
import net.dstone.knowledge.semantic.ScreenScript;

/**
 * <pre>
 * 한 프로젝트의 분석에 쓰는 규칙 전체입니다. 고른 규칙 묶음(RuleSet)들을 order 순서대로 이어 붙인 것입니다.
 *
 * SEMANTIC 단계가 시작할 때 한 번 만들어 AnalysisJobContext 에 실어 두고, 플러그인들이 여기서 자기 몫의 규칙을 꺼내 씁니다.
 *   SpringPlugin       HTTP 진입점, 애노테이션 진입점, 주입, 프록시, 애노테이션 계층
 *   PlainJavaPlugin    상위 타입 진입점
 *   MyBatisPlugin      호출 인자 규칙(SQL)
 *   JspPlugin          호출 인자 규칙(VIEW)
 *   RichClientPlugin   화면 호출 / 필드 / 열기 규칙
 *   ScreenMenuPlugin   메뉴 규칙
 *   ConfiguredLayerPlugin  상위 타입 계층
 *   NamingLayerPlugin  이름 계층
 *
 * 두 묶음이 같은 애노테이션으로 HTTP 진입점이나 애노테이션 진입점을 만들면 진입점이 두 번 생기므로, 합칠 때 오류로 막습니다.
 * 목록 모양의 규칙(주입, 프록시 애노테이션, 화면 규칙)은 겹치는 것을 하나만 남깁니다.
 * </pre>
 */
public class SemanticRules {

	private final List<RuleSet> sets;

	private final List<RuleSet.HttpMappingRule> httpMappings = new ArrayList<RuleSet.HttpMappingRule>();
	private final List<RuleSet.AnnotatedEndpointRule> annotatedEndpoints = new ArrayList<RuleSet.AnnotatedEndpointRule>();
	private final List<RuleSet.SuperTypeEndpointRule> superTypeEndpoints = new ArrayList<RuleSet.SuperTypeEndpointRule>();
	private final Set<String> injectAnnotations = new LinkedHashSet<String>();
	private final Set<String> proxyAnnotations = new LinkedHashSet<String>();
	private final List<RuleSet.LayerAnnotationRule> layerAnnotations = new ArrayList<RuleSet.LayerAnnotationRule>();
	private final List<RuleSet.LayerSuperTypeRule> layerSuperTypes = new ArrayList<RuleSet.LayerSuperTypeRule>();
	private final List<RuleSet.LayerNameRule> layerNames = new ArrayList<RuleSet.LayerNameRule>();
	private final List<RuleSet.CallArgumentRule> sqlArguments = new ArrayList<RuleSet.CallArgumentRule>();
	private final List<RuleSet.CallArgumentRule> viewArguments = new ArrayList<RuleSet.CallArgumentRule>();
	private final List<ScreenScript.CallRule> screenCalls = new ArrayList<ScreenScript.CallRule>();
	private final List<ScreenScript.CallRule> screenOpens = new ArrayList<ScreenScript.CallRule>();
	private final List<ScreenMenu.Rule> screenMenus = new ArrayList<ScreenMenu.Rule>();

	/**
	 * @param sets 이미 order 순서로 늘어놓은 규칙 묶음
	 */
	public SemanticRules(List<RuleSet> sets) {
		this.sets = Collections.unmodifiableList(new ArrayList<RuleSet>(sets));

		Map<String, String> httpOwner = new HashMap<String, String>();
		Map<String, String> annotatedOwner = new HashMap<String, String>();
		Set<String> screenKeys = new LinkedHashSet<String>();
		List<ScreenScript.CallRule> fields = new ArrayList<ScreenScript.CallRule>();

		for (int s = 0; s < sets.size(); s++) {
			RuleSet set = sets.get(s);
			for (int i = 0; i < set.httpMappings.size(); i++) {
				RuleSet.HttpMappingRule rule = set.httpMappings.get(i);
				for (int j = 0; j < rule.methodAnnotations.size(); j++) {
					claim(httpOwner, rule.methodAnnotations.get(j).annotation, set, "HTTP 진입점 애노테이션");
				}
				httpMappings.add(rule);
			}
			for (int i = 0; i < set.annotatedEndpoints.size(); i++) {
				RuleSet.AnnotatedEndpointRule rule = set.annotatedEndpoints.get(i);
				for (int j = 0; j < rule.annotations.size(); j++) {
					claim(annotatedOwner, rule.endpointType + "/" + rule.annotations.get(j), set, "진입점 애노테이션");
				}
				annotatedEndpoints.add(rule);
			}
			superTypeEndpoints.addAll(set.superTypeEndpoints);
			injectAnnotations.addAll(set.injectAnnotations);
			proxyAnnotations.addAll(set.proxyAnnotations);
			layerAnnotations.addAll(set.layerAnnotations);
			layerSuperTypes.addAll(set.layerSuperTypes);
			layerNames.addAll(set.layerNames);
			for (int i = 0; i < set.callArguments.size(); i++) {
				RuleSet.CallArgumentRule rule = set.callArguments.get(i);
				if (RuleSet.CallArgumentRule.TARGET_SQL.equals(rule.target)) {
					sqlArguments.add(rule);
				} else {
					viewArguments.add(rule);
				}
			}
			addScreenRules(screenCalls, set.screenCalls, screenKeys, "call");
			addScreenRules(fields, set.screenFields, screenKeys, "field");
			addScreenRules(screenOpens, set.screenOpens, screenKeys, "open");
			for (int i = 0; i < set.screenMenus.size(); i++) {
				ScreenMenu.Rule rule = set.screenMenus.get(i);
				if (screenKeys.add("menu|" + rule.getNameKey() + "|" + rule.getPathKey())) {
					screenMenus.add(rule);
				}
			}
		}
		// 화면 규칙은 함수 호출 규칙 다음에 폼 필드 규칙을 본다(설정 키를 따로 쓰던 때와 같은 순서).
		screenCalls.addAll(fields);
	}

	/** 같은 애노테이션을 두 묶음이 쓰면 진입점이 두 번 생긴다. 어느 묶음끼리 겹치는지 알려 준다 */
	private void claim(Map<String, String> owner, String key, RuleSet set, String what) {
		String previous = owner.put(key, set.name);
		if (previous != null) {
			throw new RuleException(set.source, "", what + " " + key + " 이(가) 규칙 묶음 " + previous + " 와 " + set.name
					+ " 에 함께 있습니다. 진입점이 두 번 생기므로 한쪽에만 적으세요.");
		}
	}

	private void addScreenRules(List<ScreenScript.CallRule> target, List<ScreenScript.CallRule> rules, Set<String> keys, String kind) {
		for (int i = 0; i < rules.size(); i++) {
			ScreenScript.CallRule rule = rules.get(i);
			if (keys.add(kind + "|" + rule.getFunction() + "|" + rule.getMethodTemplate() + "|" + rule.getArgIndex())) {
				target.add(rule);
			}
		}
	}

	/** 적용한 규칙 묶음(order 순서) */
	public List<RuleSet> getSets() {
		return sets;
	}

	public List<RuleSet.HttpMappingRule> getHttpMappings() {
		return httpMappings;
	}

	public List<RuleSet.AnnotatedEndpointRule> getAnnotatedEndpoints() {
		return annotatedEndpoints;
	}

	public List<RuleSet.SuperTypeEndpointRule> getSuperTypeEndpoints() {
		return superTypeEndpoints;
	}

	public List<String> getInjectAnnotations() {
		return new ArrayList<String>(injectAnnotations);
	}

	public List<String> getProxyAnnotations() {
		return new ArrayList<String>(proxyAnnotations);
	}

	public List<RuleSet.LayerAnnotationRule> getLayerAnnotations() {
		return layerAnnotations;
	}

	public List<RuleSet.LayerSuperTypeRule> getLayerSuperTypes() {
		return layerSuperTypes;
	}

	public List<RuleSet.LayerNameRule> getLayerNames() {
		return layerNames;
	}

	/** 호출 인자 규칙 가운데 SQL 과 잇는 것 */
	public List<RuleSet.CallArgumentRule> getSqlArguments() {
		return sqlArguments;
	}

	/** 호출 인자 규칙 가운데 화면(JSP)과 잇는 것 */
	public List<RuleSet.CallArgumentRule> getViewArguments() {
		return viewArguments;
	}

	/** 화면이 서버를 부르는 규칙: 함수 호출 규칙 다음에 폼 필드 규칙 */
	public List<ScreenScript.CallRule> getScreenCalls() {
		return screenCalls;
	}

	public List<ScreenScript.CallRule> getScreenOpens() {
		return screenOpens;
	}

	public List<ScreenMenu.Rule> getScreenMenus() {
		return screenMenus;
	}

}

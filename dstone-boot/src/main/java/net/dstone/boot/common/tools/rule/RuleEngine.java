package net.dstone.boot.common.tools.rule;

import java.util.ArrayList;

import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.core.DefaultRulesEngine;

public class RuleEngine {
	
	private static RuleEngine launcher = null;

	private RulesEngine rulesEngine = null;
	
	public static RuleEngine getInstance() {
		if( launcher == null ) {
			launcher = new RuleEngine();
		}
		return launcher;
	}
	
	private RuleEngine() {
		this.init();
	}
	
	private void init() {
		if( rulesEngine == null ) {
			rulesEngine = new DefaultRulesEngine();
		}
	}
	
	public Result fire(Rule rule) {
		Result result = null;
		
		Rules rules = new Rules();
		rules.register(rule);
		
		rulesEngine.fire(rules, rule.getFacts());
		
		result = rule.getResult();
		return result;
	}

	public Result fire(ArrayList<Rule> ruleList) {
		Result result = null;
		for( Rule rule : ruleList ) {
			Rules rules = new Rules();
			rules.register(rule);
			rulesEngine.fire(rules, rule.getFacts());
			result = rule.getResult();
			if( !result.isValid() ) {
				break;
			}
		}
		return result;
	}
	
	public void fire(ArrayList<Rule> ruleList, Facts facts) {
		
		Result result = null;
		
		Rules rules = new Rules();
		for( Rule rule : ruleList ) {
			rules.register(rule);
		}
		
		rulesEngine.fire(rules, facts);
	}
	
}

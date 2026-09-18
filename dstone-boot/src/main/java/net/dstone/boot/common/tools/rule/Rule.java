package net.dstone.boot.common.tools.rule;

import org.jeasy.rules.api.Facts;

public abstract class Rule {
	
	public abstract boolean when();
	public abstract void then() throws Exception;
	
	private Facts facts = new Facts();

	public Object getFact(String key) {
		return facts.get(key);
	}
	public void setFact(String key, Object value) {
		this.facts.put(key, value);
	}
	
	public Facts getFacts() {
		return facts;
	}
	public void setFacts(Facts facts) {
		this.facts = facts;
	}
	
	private Result result = new Result();
	
	public Result getResult() {
		return result;
	}
	public void setResult(Result result) {
		this.result = result;
	}
	
	@Override
	public String toString() {
		return "Rule [facts=" + facts + ", result=" + result + "]";
	}
	
}

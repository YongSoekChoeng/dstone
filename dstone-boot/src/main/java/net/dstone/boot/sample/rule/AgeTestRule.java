package net.dstone.boot.sample.rule;

import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Rule;

@Rule(name = "AgeTestRule", description = "AgeTestRule")
public class AgeTestRule extends net.dstone.boot.common.tools.rule.Rule {

	@Condition
	public boolean when() {
		
		if( Integer.parseInt(this.getFact("age").toString()) <= Integer.parseInt(this.getFact("ageLimit").toString()) ) {
			return true;
		}else {
			return false;
		}
		
	}
	
	@Action
	public void then() throws Exception {
		System.out.println("┌────────── ────────── ──────────");
		System.out.println("나이["+this.getFact("age")+"]가  ["+this.getFact("ageLimit")+"]이하입니다.");
		System.out.println("└────────── ────────── ──────────");
		
		this.getResult().setValid(true);
	}
}

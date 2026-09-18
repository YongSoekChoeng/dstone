package net.dstone.boot.sample.rule;

import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Rule;

@Rule(name = "PositionTestRule", description = "PositionTestRule")
public class PositionTestRule extends net.dstone.boot.common.tools.rule.Rule {

	@Condition
	public boolean when() {
		
		if( "매니저".equals(this.getFact("position")) ) {
			return true;
		}else {
			return false;
		}
		
	}
	
	@Action
	public void then() throws Exception {
		System.out.println("┌────────── ────────── ──────────");
		System.out.println("직급["+this.getFact("position")+"]이 맞습니다.");
		System.out.println("└────────── ────────── ──────────");
		
		this.getResult().setValid(true);
	}
}

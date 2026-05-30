package net.dstone.boot.sample.analyze;

import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseController {

	@Autowired
    private TestService testService;

	public TestService getTestService() {
		return testService;
	}

	public void setTestService(TestService testService) {
		this.testService = testService;
	}
	
}

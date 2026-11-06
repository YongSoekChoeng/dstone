package net.dstone.ai.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;

/**
 * <pre>
 * RequesterContext가 dstone-boot가 보낸 사용자 헤더를 읽고, 남의 실행 조작을 막는지 확인합니다.
 * </pre>
 */
public class RequesterContextTest {

	private WorkFlowExecution executionOf(String requestedBy) {
		return WorkFlowExecution.queue(UUID.randomUUID().toString(), "wf", null, "s", new HashMap<String, Object>(), requestedBy);
	}

	private MockHttpServletRequest requestOf(String user, boolean admin) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (user != null) {
			request.addHeader("X-Dstone-User", URLEncoder.encode(user, StandardCharsets.UTF_8));
		}
		if (admin) {
			request.addHeader("X-Dstone-User-Admin", "true");
		}
		return request;
	}

	@Test
	void 사용자_헤더는_URL_인코딩을_풀어서_읽는다() {
		assertThat(RequesterContext.userOf(this.requestOf("홍길동", false))).isEqualTo("홍길동");
		assertThat(RequesterContext.userOf(this.requestOf(null, false))).isNull();
	}

	@Test
	void 시작한_사람과_관리자만_조작할_수_있다() {
		final WorkFlowExecution mine = this.executionOf("dev1");

		RequesterContext.checkOwner(this.requestOf("dev1", false), mine);
		RequesterContext.checkOwner(this.requestOf("admin", true), mine);
		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				RequesterContext.checkOwner(RequesterContextTest.this.requestOf("dev2", false), mine);
			}
		}).isInstanceOf(ResponseStatusException.class).hasMessageContaining("dev1");
	}

	@Test
	void 사용자를_모르는_요청이나_시작한_사람이_없는_실행은_막지_않는다() {
		RequesterContext.checkOwner(this.requestOf(null, false), this.executionOf("dev1"));
		RequesterContext.checkOwner(this.requestOf("dev2", false), this.executionOf(null));
	}

}

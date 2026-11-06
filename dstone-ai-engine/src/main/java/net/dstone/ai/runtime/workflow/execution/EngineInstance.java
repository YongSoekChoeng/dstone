package net.dstone.ai.runtime.workflow.execution;

import java.net.InetAddress;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * <pre>
 * 지금 떠 있는 엔진 인스턴스 하나를 가리키는 id입니다. "호스트이름:임의값" 모양입니다(예: dstone-ai-engine-7c9f-xk2:3f1a9b2c).
 *
 * 실행을 맡은 인스턴스를 AI_WORKFLOW_EXECUTION.OWNER_INSTANCE에 적고, 그 인스턴스가 살아 있는 동안 HEARTBEAT_AT을 계속 갱신합니다.
 * 엔진을 다시 띄우면 임의값이 바뀌므로, 같은 호스트에서 재시작해도 다른 인스턴스로 봅니다.
 * </pre>
 */
@Component
public class EngineInstance {

	private final String id;

	public EngineInstance() {
		String host;
		try {
			host = InetAddress.getLocalHost().getHostName();
		} catch (Exception e) {
			host = "unknown";
		}
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		// OWNER_INSTANCE 컬럼은 100자입니다. 호스트이름이 길면 앞쪽만 씁니다.
		if (host.length() > 80) {
			host = host.substring(0, 80);
		}
		this.id = host + ":" + suffix;
	}

	/** 이 인스턴스의 id입니다. */
	public String id() {
		return this.id;
	}

}

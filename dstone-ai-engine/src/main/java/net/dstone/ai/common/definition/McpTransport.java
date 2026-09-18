package net.dstone.ai.common.definition;

/** MCP 서버에 접속하는 방식. common.config.ConfigMcp가 이 값으로 어떤 io.modelcontextprotocol 클라이언트 트랜스포트를 만들지 정한다. */
public enum McpTransport {

	/** 로컬 프로세스를 표준입출력으로 띄워 통신한다(command/args). */
	STDIO,

	/** 이미 떠 있는 원격 HTTP(SSE) MCP 서버에 접속한다(url). */
	SSE

}

package net.dstone.ai.common.definition;

/**
 * MCP(Model Context Protocol) 서버에 접속하는 두 가지 방식을 나타냅니다. common.config.ConfigMcp가
 * 이 값을 보고, io.modelcontextprotocol 라이브러리가 제공하는 두 가지 접속 방법(클라이언트 트랜스포트) 중
 * 어느 쪽을 쓸지 결정합니다.
 */
public enum McpTransport {

	/** 내 컴퓨터(또는 서버)에서 MCP 서버 프로그램을 직접 실행시키고, 표준 입출력으로 대화하는 방식입니다. McpServerDefinition의 command/args 값을 사용합니다. */
	STDIO,

	/** 이미 어딘가에서 실행 중인 원격 MCP 서버에 HTTP(SSE) 방식으로 접속하는 방식입니다. McpServerDefinition의 url 값을 사용합니다. */
	SSE

}

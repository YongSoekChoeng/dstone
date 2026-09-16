package net.dstone.ai.common.exec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * tool 중 내부 java프로그램이 아닌 외부 Tool을 호출해주는 클래스. ShellExecTool/PythonExecTool이 공유하는 "OS 프로세스 하나를 안전하게 실행하고 텍스트로 결과를 돌려주는"
 * 로직이다.
 *
 * ProcessBuilder(command)는 셸을 거치지 않고 프로세스를 직접 실행하므로(/bin/sh -c로 감싸지 않음), 인자에 [ ; / && / | ] 같은 셸 메타문자가 들어있어도 셸 문법으로
 * 해석되지 않고 단순 인자 문자열로만 전달된다 LLM이 만든 인자값이라도 명령 주입으로 이어지지 않는 이유다. 다만 command[0](실행 파일 경로) 자체는 호출부가 화이트리스트로 확정한 값이어야 한다(이
 * 클래스는 그 검증을 하지 않는다).
 */
public final class ExternalProcessRunner {

	private ExternalProcessRunner() {
	}

	/**
	 * @param command        실행할 명령어와 인자 목록
	 * @param timeout        최대 대기 시간
	 * @param maxOutputChars 출력 결과로 남길 최대 글자 수
	 */
	public static String run(List<String> command, Duration timeout, int maxOutputChars) {
		Process process;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(true).start();
		} catch (IOException e) {
			return "실패: 프로세스를 시작하지 못했습니다 - " + e.getMessage();
		}

		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (output.length() < maxOutputChars) {
					output.append(line).append('\n');
				}
			}
		} catch (IOException e) {
			// 프로세스가 강제 종료되면 출력 스트림 읽기 중 끊길 수 있다 - 지금까지 모은 출력은 그대로 쓴다.
		}

		boolean finished;
		try {
			finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return "실패: 실행이 중단되었습니다.";
		}
		if (!finished) {
			process.destroyForcibly();
			return "실패: 실행 시간이 초과되었습니다(제한 " + timeout.toSeconds() + "초).";
		}

		String text = output.length() > maxOutputChars ? output.substring(0, maxOutputChars) + "...(생략)" : output.toString();
		int exitCode = process.exitValue();
		return exitCode == 0 ? "통과: " + text : "실패: 종료 코드 " + exitCode + "\n" + text;
	}

}

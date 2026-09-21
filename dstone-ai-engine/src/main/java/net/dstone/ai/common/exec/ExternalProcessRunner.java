package net.dstone.ai.common.exec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 자바 코드가 아니라 OS 프로세스로 실행해야 하는 외부 Tool을 위한 공용 실행기입니다.
 * ShellExecTool과 PythonExecTool이 이 클래스를 함께 씁니다 - "OS 프로세스 하나를 안전하게
 * 실행하고, 그 결과를 텍스트로 돌려주는" 로직을 한곳에 모아둔 것입니다.
 *
 * ProcessBuilder(command)는 셸(/bin/sh -c)을 거치지 않고 프로세스를 곧바로 실행합니다. 그래서
 * 인자에 `;`, `&&`, `|` 같은 셸 특수문자가 들어 있어도 셸 문법으로 해석되지 않고, 그냥 평범한
 * 문자열 인자 하나로만 전달됩니다. LLM이 만들어낸 인자값이 들어오더라도 명령어 주입 공격으로
 * 이어지지 않는 이유가 바로 이것입니다. 다만 command[0](실제로 실행할 파일 경로)은 이 클래스가
 * 직접 검증하지 않으므로, 호출하는 쪽에서 화이트리스트로 미리 확정해 둔 값이어야 합니다.
 */
public final class ExternalProcessRunner {

	private ExternalProcessRunner() {
	}

	/**
	 * 명령어를 실행하고, 그 표준출력/표준에러를 합쳐서 텍스트로 돌려줍니다. 제한 시간을 넘기면
	 * 프로세스를 강제 종료하고, 종료 코드가 0이 아니면 "실패:"로 시작하는 문자열을 돌려줍니다.
	 *
	 * @param command        실행할 명령어와 그 인자들의 목록(command[0]이 실제 실행 파일 경로)
	 * @param timeout        프로세스가 끝나기를 기다릴 최대 시간
	 * @param maxOutputChars 결과 텍스트로 남길 최대 글자 수(넘으면 잘라내고 "...(생략)"을 붙임)
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
			// 프로세스가 강제 종료되면 출력을 읽는 도중에 끊길 수 있습니다. 그때까지 모은 출력은 그대로 사용합니다.
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

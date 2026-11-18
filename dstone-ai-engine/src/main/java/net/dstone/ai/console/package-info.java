/**
 * <pre>
 * AI 화면(콘솔)이 부르는 API입니다. 화면 파일은 src/main/resources/static/ai/** 에 있습니다(첫 화면 /ai/index.html).
 *
 * 예전에는 dstone-boot 안에 있던 화면과 API를 이 앱으로 옮겨 왔습니다(net.dstone.boot.ai → net.dstone.ai.console).
 *   - controller: 화면이 부르는 /ai/**.do 주소. 예전 dstone-boot 의 주소와 같다
 *   - service: 엔진 REST API(/api/ai/**)를 HTTP 로 불러 화면용 모양으로 바꾼다(ConsoleBaseService)
 *   - vo: 화면과 주고받는 값
 *
 * 화면을 연 사람은 dstone-common 의 ConsoleSessionFilter 가 정합니다(common.config.ConfigConsole).
 * </pre>
 */
package net.dstone.ai.console;

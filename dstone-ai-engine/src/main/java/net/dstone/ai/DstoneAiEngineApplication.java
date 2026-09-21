package net.dstone.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.annotation.ComponentScan;

import net.dstone.common.utils.ConvertUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * dstone-ai-engine을 시작하는 진입점 클래스입니다.
 *
 * 참고로 Spring Boot 4부터는 JDBC 자동 설정이 별도 모듈(spring-boot-jdbc)로 빠졌습니다. 이
 * 모듈은 RAG(pgvector)용 DataSource가 필요해진 시점부터, dstone-boot가 쓰는 ConfigDatasource
 * 패턴을 그대로 따라서 JDBC starter를 가져다 씁니다.
 */
@SpringBootApplication
@ComponentScan(basePackages = { "net.dstone.ai" })
public class DstoneAiEngineApplication {

	/**
	 * env.properties를 System 프로퍼티로 먼저 세팅한 뒤, conf/application.yml과
	 * conf/log4j2.xml을 설정 위치로 지정해서 Spring Boot 앱을 실행합니다.
	 *
	 * @param args 커맨드라인 인자
	 */
	public static void main(String[] args) {

		/*** env.properties의 항목들을 System변수로 세팅 ***/
		setSysProperties();

		StringBuffer msg = new StringBuffer();
		String appConfDir = System.getProperty("APP_CONF_DIR");
		SpringApplicationBuilder springApplicationBuilder = new SpringApplicationBuilder(DstoneAiEngineApplication.class);
		Map<String, Object> prop = new HashMap<String, Object>();
		prop.put("spring.config.location", appConfDir + "/application.yml");
		prop.put("logging.config", appConfDir + "/log4j2.xml");

		msg.append("/******************************* 설정파일 로딩 시작 *********************************/").append("\n");
		msg.append(ConvertUtil.convertToJson(prop)).append("\n");
		msg.append("/******************************* 설정파일 로딩 끝 *********************************/").append("\n");
		LogUtil.sysout(msg);

		springApplicationBuilder.properties(prop);
		springApplicationBuilder.listeners(new ApplicationPidFileWriter());
		springApplicationBuilder.run(args);

	}

	public static boolean IS_SYS_PROPERTIES_SET = false;

	/**
	 * conf/env.properties(또는 프로파일별 env-{profile}.properties)를 읽어서, 그 안의 값들을
	 * System 프로퍼티로 세팅합니다. 한 번 세팅되고 나면 다시 호출해도 아무 일도 하지 않습니다
	 * (IS_SYS_PROPERTIES_SET 플래그로 막습니다).
	 */
	@SuppressWarnings("rawtypes")
	public static void setSysProperties() {
		if (!IS_SYS_PROPERTIES_SET) {
			IS_SYS_PROPERTIES_SET = true;
			StringBuffer msg = new StringBuffer();
			try {
				String profile = "local";
				if (!StringUtil.isEmpty(System.getenv("spring.profiles.active"))) {
					profile = System.getenv("spring.profiles.active").trim().toLowerCase();
				} else if (!StringUtil.isEmpty(System.getProperty("spring.profiles.active"))) {
					profile = System.getProperty("spring.profiles.active", "local").trim().toLowerCase();
				}
				if ("local".equals(profile)) {
					profile = "";
				} else {
					profile = "-" + profile;
				}
				String envFile = "env" + profile + ".properties";
				msg.append("/******************************* " + envFile + " System변수로 세팅 하기위한 조치 시작 *********************************/").append("\n");
				java.net.URL resource = DstoneAiEngineApplication.class.getClassLoader().getResource(envFile);
				if (resource != null) {
					try (InputStream input = resource.openStream()) {
						Properties props = new Properties();
						if (input == null) {
							msg.append("Unable to find " + envFile).append("\n");
						} else {
							props.load(input);
							String key = "";
							String val = "";
							java.util.Iterator keys = props.keySet().iterator();
							while (keys.hasNext()) {
								key = (String) keys.next();
								val = props.getProperty(key, "");
								System.setProperty(key, val);
								msg.append("시스템프로퍼티 " + key + "[" + val + "]").append("\n");
							}
						}
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
				msg.append("/******************************* " + envFile + " System변수로 세팅 하기위한 조치 끝  *********************************/").append("\n");

				LogUtil.sysout(msg);

			} catch (Exception e) {
				// TODO: handle exception
			}
		}
	}

}

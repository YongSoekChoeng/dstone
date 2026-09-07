package net.dstone.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.annotation.ComponentScan;

import net.dstone.common.utils.ConvertUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * Phase 0 단계에서는 DB/Redis/RabbitMQ/Kafka를 사용하지 않으므로 DataSourceAutoConfiguration을 제외한다.
 * RAG/세션(Phase1~2)에서 실제로 필요해지는 시점에 dstone-boot의 ConfigDatasource 패턴을 따라 추가한다.
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@ComponentScan(basePackages = { "net.dstone.ai" })
public class DstoneAiEngineApplication {

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

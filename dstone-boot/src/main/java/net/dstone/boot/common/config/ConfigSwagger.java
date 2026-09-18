package net.dstone.boot.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;

/**
 * springfox(2020년 마지막 릴리스, Spring Framework 7 미지원)를 springdoc-openapi로 교체.
 * springdoc은 Docket 대신 GroupedOpenApi로 스캔 대상 패키지/경로를 지정하고,
 * @RestController/@Controller 여부나 @Api 애노테이션 존재 여부를 별도로 걸러낼 필요 없이
 * packagesToScan만으로 충분하다(그 패키지 안에 컨트롤러가 아닌 클래스는 애초에 대상이 아님).
 */
@ConditionalOnProperty(name = "spring.swagger.enabled", havingValue = "true")
public class ConfigSwagger {

	@Bean
	public GroupedOpenApi sampleApi() {
		return GroupedOpenApi.builder()
			.group("dstone-sample")
			.packagesToScan("net.dstone.boot.sample.swagger")
			.pathsToMatch("/restapi/sample/**", "/restapi/sample2/**")
			.build();
	}

	@Bean
	public OpenAPI apiInfo() {
		return new OpenAPI().info(new Info()
			.title("Dstone API 문서")
			.description("Dstone Swagger")
			.version("1.0.0"));
	}

}

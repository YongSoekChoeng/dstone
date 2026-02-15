package net.dstone.boot.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;


@ConditionalOnProperty(name = "spring.swagger.enabled", havingValue = "true" )
@EnableSwagger2
public class ConfigSwagger {
	
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
            .select()
            // 스캔 대상 클래스
            .apis(requestHandler -> {
            	/*** 스캔 대상 클래스가 추가될 때 세팅될 설정 시작 ***/
            	boolean isValid = false;
                Class<?> declaringClass = requestHandler.declaringClass();
            	boolean isRootPackage = declaringClass.getName().startsWith("net.dstone.boot.sample.swagger");
            	boolean isControllerClass = declaringClass.isAnnotationPresent(Controller.class);
            	boolean isRestControllerClass = declaringClass.isAnnotationPresent(RestController.class);
            	boolean isApiClass = declaringClass.isAnnotationPresent(Api.class);
            	isValid = (isRootPackage && (isControllerClass || isRestControllerClass) && isApiClass);
            	/*** 스캔 대상 클래스가 추가될 때 세팅될 설정 끝 ***/
                return isValid;
            })
            // 스캔 대상 경로
            .paths(path -> {
            	/*** 스캔 대상경로가 추가될 때 세팅될 설정 시작 ***/
            	// 조건 - 샘플1
                boolean condition1 = PathSelectors.ant("/restapi/sample/**").apply(path);
            	// 조건 - 샘플2
                boolean condition2 = PathSelectors.ant("/restapi/sample2/**").apply(path);
                // 조건 조합 리턴
                return ( condition1 || condition2 );
            	/*** 스캔 대상경로가 추가될 때 세팅될 설정 끝 ***/
            })
            .build()
            .apiInfo(apiInfo());
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
            .title("Dstone API 문서")
            .description("Dstone Swagger")
            .version("1.0.0")
            .build();
    }
    
}

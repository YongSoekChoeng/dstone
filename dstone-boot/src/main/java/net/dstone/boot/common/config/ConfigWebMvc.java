package net.dstone.boot.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.servlet.view.BeanNameViewResolver;
import org.springframework.web.servlet.view.ContentNegotiatingViewResolver;
import org.springframework.web.servlet.view.JstlView;
import org.springframework.web.servlet.view.UrlBasedViewResolver;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.exception.resolver.DsExceptionResolver;
import net.dstone.common.utils.LogUtil;

@Configuration
public class ConfigWebMvc  extends WebMvcConfigurationSupport {

	private static final LogUtil logger = new LogUtil(ConfigWebMvc.class);
	
	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	/**
	 * 뷰 등록을 하는 메소드.
	 */
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		// Welcome Page 설정.
		/*
		registry.addViewController("/").setViewName("forward:/index.html" );
		registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
		*/
		registry.addViewController("/").setViewName("forward:/index.html" );
		//registry.addViewController("/").setViewName("forward:/defaultLink.do?defaultLink=main" );
		super.addViewControllers(registry);
	}

	/**
	 * 컨트롤러 메소드의 파라메터 를 원하는 특정 타입 오브젝트로 자동맵핑하기위한 메소드.
	 * 예) 
	 * 1. VO 생성.
	 *   @Data
	 *   public class UserDto {
	 * 	   private final String id;
	 *   }
	 * 2. UserArgumentResolver 구현.
	 *   public class UserArgumentResolver implements HandlerMethodArgumentResolver {
	 *     @Override
	 *     public boolean supportsParameter(MethodParameter parameter) {
	 *       return parameter.getParameterType().equals(UserDto.class);
	 *     }
	 *     @Override
	 *     public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
	 *       ...
	 *     }
	 *   }
	 * 3. Argument Resolver 등록  
	 *   argumentResolvers.add(new UserArgumentResolver());
	 * 4. 활용
	 *   @Mapping("/items")
	 *   public ResponseEntity<Void> createItem(UserDto userDto) {
	 *     ...
	 *   }
	 */
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		if(argumentResolvers == null) {
			argumentResolvers = new ArrayList<HandlerMethodArgumentResolver>();
		}
	}

	/**
     * Ajax 방식일 때 사용 할 View를 생성.
     * @return
     */
	@Bean
	public MappingJackson2JsonView jsonView(ObjectMapper objectMapper) {
	    MappingJackson2JsonView view = new MappingJackson2JsonView(objectMapper);
	    //view.setExtractValueFromSingleKeyModel(true);
	    view.setPrettyPrint(true);
	    return view;
	}
    
	/**
	 * 뷰의 이름과 동일한 이름 가지는 빈을 view객체로 사용하도록 설정.
	 * 예)
	 * 1. 다운로드 View 세팅.
	 * <bean id="download" class="com.imoxion.sensmail.web.common.DownloadView"/> 
	 * 2. 활용
	 * public FileDownUtil(
	 *   ...
	 *   public static ModelAndView getDownloadView(File file, String fileName) { 
	 *     CommonFile downloadFile = new CommonFile(); downloadFile.setFile(file); //파일경로
	 *     downloadFile.setFileName(fileName); //파일이름 
	 *     ModelAndView mav = new ModelAndView(); //모델엔뷰 객체생성
	 *     mav.setViewName("download"); // 응답할 view이름 설정"download"  
	 *     mav.addObject("downloadFile", downloadFile); // view에 전달할 값 
	 *     return mav; // modelAndView return 
	 *   }
	 *   ...
	 * }
	 * @return
	 */
	@Bean
	public BeanNameViewResolver beanNameViewResolver() {
		BeanNameViewResolver beanNameViewResolver = new BeanNameViewResolver();
		// 순서는 contentNegotiatingViewResolver 에서 세팅
		//beanNameViewResolver.setOrder(0);
		return beanNameViewResolver; 
	}

	/**
	 * Url 경로기준으로 view를 찾아가는 ViewResolver.
	 * 가장 일반적으로 많이 사용됨.
	 * @return
	 */
	@Bean
	public UrlBasedViewResolver urlBasedViewResolver() {
		UrlBasedViewResolver urlBasedViewResolver = new UrlBasedViewResolver();
		// 순서는 contentNegotiatingViewResolver 에서 세팅
		//urlBasedViewResolver.setOrder(1);
		urlBasedViewResolver.setViewClass(JstlView.class);
		urlBasedViewResolver.setPrefix("/WEB-INF/views/");
		urlBasedViewResolver.setSuffix(".jsp");
		return urlBasedViewResolver;
	}

	/**
	 * 여러개의 ViewResolver를 사용할 때 Resolver등록 및 우선순위 세팅.
	 * @return
	 */
	@Bean
	public ContentNegotiatingViewResolver contentNegotiatingViewResolver(MappingJackson2JsonView jsonView, UrlBasedViewResolver jspResolver) {
	    ContentNegotiatingViewResolver resolver = new ContentNegotiatingViewResolver();
	    // 우선순위
	    resolver.setOrder(0);
	    // 후보 뷰 등록
	    List<ViewResolver> viewResolvers = new ArrayList<ViewResolver>();
	    viewResolvers.add(beanNameViewResolver());
	    viewResolvers.add(urlBasedViewResolver());
	    // 리졸버 등록
	    resolver.setViewResolvers(viewResolvers);

	    List<View> defaultViews = new ArrayList<View>();
	    defaultViews.add(jsonView);
	    resolver.setDefaultViews(defaultViews);
	    
	    return resolver;
	}


	/**
	 * LocaleResolver는 locale을 결정하는 역할.
	 */
	@Bean
    public SessionLocaleResolver localeResolver() {
        return new SessionLocaleResolver();
    }

	/**
	 * LocaleChangeInterceptor는 url 뒤에 특정 locale 인자값을 넘겨서 변경하도록 하는 것이다. 간단하게 구현이 가능해 많이 사용된다. 
	 * 아래의 소스로 예를 들자면 ip:port/abc/dev?language=en 이런식으로 요청이 들어온다면 locale을 en으로 변경을 해준다. 
	 * 위에 설정한 language이라는 값은 내게 맞게 변경하여 사용할 수 있다. 
	 * @return
	 */
	@Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("language");
        return interceptor;
    }

    /**
     * Interceptor 추가기능
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
    
    /**
     * Spring Boot 프로젝트 외부 경로에 있는 파일 접근하기위한 기능.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
    	/* <설정값의 의미>
    	 * ResourceHandler 에 설정된 값이 "/fileUp/**" 라고 하고 resources.fileUp.path에 설정되어있는 값이 "D:/Temp" 라고 하면 
    	 * "/fileUp"값이 resources.fileUp.path에 설정되어있는 값으로 치환 된다.
    	 * 예) [/fileUp/testDoc.xlsx] ==>> [D:/Temp/testDoc.xlsx]  
    	 * ResourceLocations 설정 프로토콜 - file:(파일시스템), classpath:(클래스패스)
    	 */
		registry.addResourceHandler("/fileUp/**").addResourceLocations("file:" + configProperty.getProperty("resources.fileUp.path") + "/"  );
		registry.addResourceHandler("/**").addResourceLocations("/");
    }

	/**
	 * encodingFilter 설정기능.
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Bean
	public FilterRegistrationBean encodingFilterBean() {
		FilterRegistrationBean registrationBean = new FilterRegistrationBean();
		CharacterEncodingFilter filter = new CharacterEncodingFilter();
		filter.setForceEncoding(true);
		filter.setEncoding("UTF-8");
		registrationBean.setFilter(filter);
		registrationBean.addUrlPatterns("*.do");
		return registrationBean;
	}

	/**
	 * 예외처리자(ExceptionResolver) 설정기능.
	 */
	@Override
	public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
		Properties prop = new Properties();
		prop.setProperty("net.dstone.common.exception.BizException", "common/error");
		prop.setProperty("net.dstone.common.exception.SecException", "common/error");
		prop.setProperty("java.lang.Exception", "common/error");
		prop.setProperty("java.lang.Throwable", "common/error");

		Properties statusCode = new Properties();
		statusCode.setProperty("common/error", "400");
		statusCode.setProperty("common/error", "500");

		DsExceptionResolver smer = new DsExceptionResolver();
		smer.setDefaultErrorView("common/error");
		smer.setExceptionMappings(prop);
		smer.setStatusCodes(statusCode);
		exceptionResolvers.add(smer);
	}

}
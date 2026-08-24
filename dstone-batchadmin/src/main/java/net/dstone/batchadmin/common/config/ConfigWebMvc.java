package net.dstone.batchadmin.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
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
public class ConfigWebMvc extends WebMvcConfigurationSupport {

	private static final LogUtil logger = new LogUtil(ConfigWebMvc.class);

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/").setViewName("forward:/index.html");
		super.addViewControllers(registry);
	}

	/**
	 * Ajax 방식일 때 사용 할 View를 생성.
	 */
	@Bean
	public MappingJackson2JsonView jsonView(ObjectMapper objectMapper) {
		MappingJackson2JsonView view = new MappingJackson2JsonView(objectMapper);
		view.setPrettyPrint(true);
		return view;
	}

	@Bean
	public BeanNameViewResolver beanNameViewResolver() {
		return new BeanNameViewResolver();
	}

	@Bean
	public UrlBasedViewResolver urlBasedViewResolver() {
		UrlBasedViewResolver urlBasedViewResolver = new UrlBasedViewResolver();
		urlBasedViewResolver.setViewClass(JstlView.class);
		urlBasedViewResolver.setPrefix("/WEB-INF/views/");
		urlBasedViewResolver.setSuffix(".jsp");
		return urlBasedViewResolver;
	}

	@Bean
	public ContentNegotiatingViewResolver contentNegotiatingViewResolver(MappingJackson2JsonView jsonView, UrlBasedViewResolver jspResolver) {
		ContentNegotiatingViewResolver resolver = new ContentNegotiatingViewResolver();
		resolver.setOrder(0);
		List<ViewResolver> viewResolvers = new ArrayList<ViewResolver>();
		viewResolvers.add(beanNameViewResolver());
		viewResolvers.add(urlBasedViewResolver());
		resolver.setViewResolvers(viewResolvers);

		List<View> defaultViews = new ArrayList<View>();
		defaultViews.add(jsonView);
		resolver.setDefaultViews(defaultViews);

		return resolver;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**").addResourceLocations("/");
	}

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

	@Override
	public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
		Properties prop = new Properties();
		prop.setProperty("net.dstone.common.exception.BizException", "common/error");
		prop.setProperty("net.dstone.common.exception.SecException", "common/error");
		prop.setProperty("java.lang.Exception", "common/error");
		prop.setProperty("java.lang.Throwable", "common/error");

		Properties statusCode = new Properties();
		statusCode.setProperty("common/error", "400");

		DsExceptionResolver resolver = new DsExceptionResolver();
		resolver.setDefaultErrorView("common/error");
		resolver.setExceptionMappings(prop);
		resolver.setStatusCodes(statusCode);
		exceptionResolvers.add(resolver);
	}

}

package net.dstone.common.utils;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringUtil implements ApplicationContextAware {

	private static ApplicationContext context;
	
    @Override
    public void setApplicationContext(ApplicationContext ac) {
        context = ac;
    }

    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    public static Object getBean(String clazzNm) {
        return context.getBean(clazzNm);
    }
}

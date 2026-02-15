package net.dstone.batch.common.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.task.configuration.DefaultTaskConfigurer;
import org.springframework.cloud.task.configuration.TaskConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.batch.common.core.BaseBatchObject;

@Configuration
public class ConfigCloudTask extends BaseBatchObject{

    /**
     * Spring Cloud Task를 사용할 경우 Task용 DataSource를 명시적으로 지정해 주어야 함.
     * @param dataSource
     * @return
     */
    @Bean
    public TaskConfigurer taskConfigurer(@Qualifier("dataSourceCommon") DataSource dataSource) {
        return new DefaultTaskConfigurer(dataSource);
    }
}

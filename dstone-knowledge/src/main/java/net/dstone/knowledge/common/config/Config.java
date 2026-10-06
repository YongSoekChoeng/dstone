package net.dstone.knowledge.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 이 모듈에 필요한 Configuration 클래스들을 한곳에 모아 @Import 해주는 클래스입니다.
 * dstone-boot, dstone-batch, dstone-batchadmin, dstone-ai-engine에서도 똑같은 방식을 씁니다.
 *
 * net.dstone.common 쪽의 공용 Config 빈(예: ConfigProperty)은
 * 이 모듈의 @ComponentScan(net.dstone.knowledge) 범위 밖에 있어서 자동으로는 스캔되지 않습니다.
 * 그래서 이 클래스에서 직접 @Import로 끌어와 등록해 줍니다.
 * </pre>
 */
@Configuration
@Import({
	ConfigDatasource.class
	, ConfigJob.class
	, ConfigMapper.class
	, ConfigProperty.class
	, ConfigTransaction.class
	, net.dstone.common.utils.SpringUtil.class
})
public class Config extends BaseObject {

}

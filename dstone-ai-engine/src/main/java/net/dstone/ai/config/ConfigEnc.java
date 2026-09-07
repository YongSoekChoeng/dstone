package net.dstone.ai.config;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;

import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.EncUtil;

@Configuration
@EnableEncryptableProperties
public class ConfigEnc extends BaseObject {

	// dstone-boot/dstone-batch와 동일한 EncUtil 기반 StringEncryptor를 재사용한다.
	@Bean("jasyptStringEncryptor")
	public StringEncryptor stringEncryptor() {
		return EncUtil.getEncryptor();
	}

}

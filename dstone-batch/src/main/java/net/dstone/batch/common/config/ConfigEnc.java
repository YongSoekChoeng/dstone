package net.dstone.batch.common.config;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.annotation.Bean;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;

import net.dstone.batch.common.core.BaseBatchObject;

@EnableEncryptableProperties
public class ConfigEnc extends BaseBatchObject{

	// 1-1. 암복호화 설정.
	@Bean("jasyptStringEncryptor") 
	public StringEncryptor stringEncryptor() { 
        return net.dstone.common.utils.EncUtil.getEncryptor();
	} 
}

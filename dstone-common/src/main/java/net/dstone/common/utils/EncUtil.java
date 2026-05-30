package net.dstone.common.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;

@Component
public class EncUtil {

	@Autowired 
	ConfigProperty configProperty;

	private static final String ENC_KEY = "jysn007db2admin";

	public static StringEncryptor getEncryptor() { 
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(ENC_KEY); // 암호화할 때 사용하는 키
        
//        config.setAlgorithm("PBEWithMD5AndDES"); // 암호화 알고리즘(DES 방식, 양방향)
//        config.setProviderName("SunJCE");        
        
//        config.setAlgorithm("PBEWITHHMACSHA256ANDAES_128"); // 암호화 알고리즘(SHA256, 단방향)
        
        config.setAlgorithm("PBEWithSHA256And128BitAES-CBC-BC"); // 암호화 알고리즘(SHA256, 양방향)
        config.setProvider(new BouncyCastleProvider());
        
        config.setKeyObtentionIterations("1000"); // 반복할 해싱 회수
        config.setPoolSize("2"); // 인스턴스 pool
        config.setStringOutputType("base64"); //인코딩 방식
        encryptor.setConfig(config);
        return encryptor;
	}
	
	public static String encrypt(String plainStr) {
		return EncUtil.getEncryptor().encrypt(plainStr);
	}

	public static String decrypt(String encStr) {
		return EncUtil.getEncryptor().decrypt(encStr);
	}

	/**
     * 간단 암호화 메소드.
     * @return
     */
	protected String getSimpleEnc(String input) {
		String output = "";
		try {
			javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
			javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(configProperty.getProperty("app.common.biz.simple-encrypt-key").getBytes(), "AES");
			cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey);
			output = java.util.Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes()));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return output;
	}
	
	/**
     * 간단 복호화 메소드.
     * @return
     */
	protected String getSimpleDec(String input) {
		String output = "";
		try {

			javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
			javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(configProperty.getProperty("app.common.biz.simple-encrypt-key").getBytes(), "AES");
			cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey);
			output = new String(cipher.doFinal(java.util.Base64.getDecoder().decode(input)), "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return output;
	}
	
}

package net.dstone.common.utils;

import java.security.Key;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JwtUtil {

	private static String SECRETE_KEY_FOR_HS256 = ""; 
	private static String SECRETE_KEY_FOR_HS384 = "";
	private static String SECRETE_KEY_FOR_HS512 = "";
	static {
		// 256비트 (32바이트 / ~32문자 이상)
		SECRETE_KEY_FOR_HS256 = "jysn007" + "226e6403effa0250609073202";
		// 384비트 (48바이트)
		SECRETE_KEY_FOR_HS384 = "jysn007" + "226e6403effa0250609073202" + "8287767126e6403e";
		// 512비트 (64바이트)
		SECRETE_KEY_FOR_HS512 = "jysn007" + "226e6403effa0250609073202" + "8287767126e6403e8842250609073302";
	}
	
	/**
	 * Jwt(Json Web Token)생성 메서드
	 * @param body
	 * @return
	 */
	public static String getJwt(Map<String, Object> body) {
		return getJwt(new HashMap<String, Object>(), body, SignatureAlgorithm.HS256, SECRETE_KEY_FOR_HS256);
	}
	
	/**
	 * Jwt(Json Web Token)생성 메서드
	 * @param body
	 * @param secretKey
	 * @return
	 */
	public static String getJwt(Map<String, Object> body, SignatureAlgorithm alg, String secretKey) {
		return getJwt(new HashMap<String, Object>(), body, alg, secretKey);
	}
	
	/**
	 * Jwt(Json Web Token)생성 메서드
	 * @param header
	 * @param body
	 * @param secretKey
	 * @return
	 */
	public static String getJwt(Map<String, Object> header, Map<String, Object> body, SignatureAlgorithm alg, String secretKey) {
		String jwt = getJwt(header, body, "", "", alg , secretKey);
		return jwt;
	}

	/**
	 * Jwt(Json Web Token)생성 메서드
	 * @param header
	 * @param body
	 * @param secretKey
	 * @return
	 */
	public static String getJwt(Map<String, Object> header, Map<String, Object> body, SignatureAlgorithm alg, Key secretKey) {
		String jwt = getJwt(header, body, "", "", alg, secretKey);
		return jwt;
	}
	
	/**
	 * Jwt(Json Web Token)생성 메서드
	 * @param header
	 * @param body
	 * @param issuer
	 * @param audience
	 * @param alg
	 * @param keyObj
	 * @return
	 */
	public static String getJwt(Map<String, Object> header, Map<String, Object> body, String issuer, String audience, SignatureAlgorithm alg, Object keyObj) {
		String jwt = "";
		io.jsonwebtoken.JwtBuilder builder = null;
        try {
        	builder = io.jsonwebtoken.Jwts.builder();
        	if( header != null && header.size() > 0 ) {
        		Iterator<String> iter = header.keySet().iterator();
        		while( iter.hasNext() ) {
        			String itemKey = iter.next();
        			builder.setHeaderParam(itemKey, header.get(itemKey));
        		}
        	}
        	if( body != null && body.size() > 0 ) {
        		Iterator<String> iter = body.keySet().iterator();
        		while( iter.hasNext() ) {
        			String itemKey = iter.next();
        			builder.claim(itemKey, body.get(itemKey));
        		}
        	}
        	if( !StringUtil.isEmpty(issuer) ) {
        		builder.setIssuer(issuer);
        	}
        	if( !StringUtil.isEmpty(audience) ) {
        		builder.setAudience(audience);
        	}
        	
            java.util.Date issDt = new java.util.Date();
            java.util.Date expDt = org.apache.commons.lang3.time.DateUtils.addHours(issDt, 1);
            
        	builder.setIssuedAt(issDt);
        	builder.setExpiration(expDt);
        	
        	// 대칭키 (HMAC)일 경우 - HS256, HS384, HS512 등... ==>> SecretKey 또는 byte[]
        	if( alg.getValue().startsWith("HS") ) {
        		if( keyObj instanceof String ) {
        			String keyStr = (String)keyObj;
        			SecretKeySpec key = new SecretKeySpec(keyStr.getBytes(), alg.getJcaName());
        			jwt = builder.signWith(key, alg).compact();
        		}else if( keyObj instanceof byte[] ) {
        			byte[] keyBytes = (byte[])keyObj;
        			SecretKeySpec key = new SecretKeySpec(keyBytes, alg.getJcaName());
        			jwt = builder.signWith(key, alg).compact();
        		}else {
        			throw new Exception("Algorithm과 키타입을 확인하세요. 대칭키 (HMAC)일 경우 - HS256, HS384, HS512 등... ==>> keyObj는 String 또는 byte[] 이어야 합니다.");
        		}
        	// 비대칭키일 경우 - RS256, RS384, RS512, ES256, ES384, ES512 등... ==>> PrivateKey
        	}else {
        		if( keyObj instanceof java.security.Key ) {
        			java.security.Key key = (java.security.Key)keyObj;
        			jwt = builder.signWith(key, alg).compact();
        		}else {
        			throw new Exception("Algorithm과 키타입을 확인하세요. 비대칭키일 경우 - RS256, RS384, RS512, ES256, ES384, ES512 등... ==>> keyObj는 PrivateKey 이어야 합니다.");
        		}
        	}
        	
        } catch (Exception e) {
            e.printStackTrace();
        }
		return jwt;
	}

	/**
	 * Jwt(Json Web Token)복호화 메서드
	 * @param jwt
	 * @param itemKey
	 * @return
	 */
	public static String getJwtDec(String jwt, String itemKey) throws Exception {
		return getJwtDec(jwt, itemKey, SignatureAlgorithm.HS256, SECRETE_KEY_FOR_HS256);
	}
	
	/**
	 * Jwt(Json Web Token)복호화 메서드
	 * @param jwt
	 * @param itemKey
	 * @return
	 */
	public static String getJwtDec(String jwt, SignatureAlgorithm alg, String itemKey) throws Exception {
		String secretKey = "";
		if( alg.getValue().startsWith("HS") ) {
    		if( "HS256".equals(alg.getValue()) ) {
    			secretKey = SECRETE_KEY_FOR_HS256;
    		}else if( "HS384".equals(alg.getValue()) ) {
    			secretKey = SECRETE_KEY_FOR_HS384;
    		}else if( "HS512".equals(alg.getValue()) ) {
    			secretKey = SECRETE_KEY_FOR_HS512;
    		}else {
    			throw new Exception("Algorithm과 키타입을 확인하세요.");
    		}
		}else {
			throw new Exception("Algorithm과 키타입을 확인하세요. 대칭키 (HMAC)알고리즘이 아닙니다.");
		}
		return getJwtDec(jwt, itemKey, alg, secretKey);
	}
	
	/**
	 * Jwt(Json Web Token)복호화 메서드
	 * @param jwt
	 * @param itemKey
	 * @param secretKey
	 * @return
	 */
	public static String getJwtDec(String jwt, String itemKey, SignatureAlgorithm alg, String secretKey) {
		return getJwtDec(jwt, itemKey, alg, new SecretKeySpec(secretKey.getBytes(), alg.getJcaName()) );
	}
	/**
	 * Jwt(Json Web Token)복호화 메서드
	 * @param jwt
	 * @param itemKey
	 * @param key
	 * @return
	 */
	public static String getJwtDec(String jwt, String itemKey, SignatureAlgorithm alg, Key key) {
		String jwtDec = "";
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();
        	jwtDec = claims.get(itemKey).toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
		return jwtDec;
	}

	/**
	 * PEM에서 PKCS#8 개인키 추출
	 * @param path
	 * @return
	 */
	public static String loadPrivateKeyPem(String path) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }

	/**
	 * PEM에서 PKCS#8 개인키 추출
	 * @param keyPem
	 * @return
	 */
	public static RSAPrivateKey loadPrivateKeyFromPem(String keyPem) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(keyPem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(spec);
    }
}

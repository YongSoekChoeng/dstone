package net.dstone.boot.sample.naver.service;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
//import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.BeanUtil;
import net.dstone.common.utils.StringUtil;


@Slf4j
@RequiredArgsConstructor
@Service
@ConfigurationProperties("interface.naver")
public class NaverService extends BaseService { 

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    public String getAccessTokenFromNaver(String code, String redirectUri) {

    	String accessToken 				= "";
    	String refreshToken 			= "";
    	
    	String accessTokenUrl 			= configProperty.getProperty("interface.naver.access-token-url");
    	String clientId 				= configProperty.getProperty("interface.naver.client-id");
    	
    	Map<String, String> reqMap		= new HashMap<String, String>();
		Map<String, String> header 		= new HashMap<String, String>();
		HttpEntity<String> request 		= null;
		ResponseEntity<String> response	= null;
		String jsonStr 					= "";
		
		try {
			
			accessTokenUrl 				= StringUtil.replace(accessTokenUrl, "@client_id@", clientId);
			accessTokenUrl 				= StringUtil.replace(accessTokenUrl, "@redirect_uri@", redirectUri);
			accessTokenUrl 				= StringUtil.replace(accessTokenUrl, "@code@", code);
			
			request 					= this.getEntity(MediaType.APPLICATION_JSON, header, BeanUtil.toJson(reqMap));
			response 					= this.getRestTemplate().exchange(accessTokenUrl, HttpMethod.GET, request, String.class);
			jsonStr						= response.getBody();
			
			if(response.getStatusCode().is2xxSuccessful()) {
				JSONParser parser 		= new JSONParser();
				JSONObject element     	= (JSONObject)parser.parse(jsonStr);
				accessToken 			= element.get("access_token").toString();
				refreshToken 			= element.get("refresh_token").toString();
				info( "accessToken["+accessToken+"]" + " refreshToken["+refreshToken+"]" );
			}else {
				throw new Exception( "Naver 인증토큰을 얻는데 실패하였습니다." +  "accessToken["+accessToken+"]" + " refreshToken["+refreshToken+"]"  );
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
		}
		
        return accessToken;
    }




    @SuppressWarnings("deprecation")
	public java.util.Map<String, String> getUserInfo(String accessToken) {
    	
    	java.util.Map<String, String> userInfo = new java.util.HashMap<String, String>();

		String userinfoUrl = configProperty.getProperty("interface.naver.userinfo-url");

		Map<String, String> reqMap				= new HashMap<String, String>();
		Map<String, String> header 				= new HashMap<String, String>();
		HttpEntity<String> request 				= null;
		ResponseEntity<String> response			= null;
		String jsonStr 							= "";
		
		try {
			
			header.put("Authorization", "Bearer " + accessToken);
			request 							= this.getEntity(MediaType.APPLICATION_JSON, header, BeanUtil.toJson(reqMap));
			response 							= this.getRestTemplate().postForEntity(userinfoUrl, request, String.class);
			jsonStr								= response.getBody();

			if(response.getStatusCode().is2xxSuccessful()) {
				JSONParser parser 				= new JSONParser();
				JSONObject element     			= (JSONObject)parser.parse(jsonStr);
				
//				{
//					"id": 3886924629,
//					"connected_at": "2025-01-21T00:01:09Z",
//					"kakao_account": {
//						"email_needs_agreement": false,
//						"profile_nickname_needs_agreement": false,
//						"profile": {
//							"is_default_nickname": false,
//							"nickname": "정용석"
//						},
//						"is_email_valid": true,
//						"is_email_verified": true,
//						"has_email": true,
//						"email": "jysn007@gmail.com"
//					},
//					"properties": {
//						"nickname": "정용석"
//					}
//				}
				
				JSONObject kakaoAccount 		= (JSONObject)element.get("naver_account");
				JSONObject properties 			= (JSONObject)element.get("properties");
				userInfo.put("id", element.get("id").toString());
				userInfo.put("email", kakaoAccount.get("email").toString());
				userInfo.put("nickname", properties.get("nickname").toString());

				info( "userInfo ===>>>" + userInfo );
			}else {
				throw new Exception( "Naver 개인정보를 얻는데 실패하였습니다."  );
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
		}
		
		return userInfo;

    }
}

package net.dstone.boot.sample.google.service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.FileUtil;

@Service
public class DriveService extends BaseService {

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    /** Global Drive API Scope. */
	private static List<String> SCOPES = Arrays.asList(
		DriveScopes.DRIVE
		, DriveScopes.DRIVE_FILE
		, DriveScopes.DRIVE_APPDATA
	);
    
    public Credential authorize(final NetHttpTransport HTTP_TRANSPORT) throws Exception {

		// 1. OAuth 클라이언트 키 파일이 있는지 체크 
    	if( !FileUtil.isFileExist(configProperty.getProperty("interface.google.credentials-filepath")) ) {
    		throw new Exception("OAuth 클라이언트 키파일["+configProperty.getProperty("interface.google.credentials-filepath")+"] 미존재.");
    	}
		InputStream in = new FileInputStream(configProperty.getProperty("interface.google.credentials-filepath"));
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), new InputStreamReader(in));

		// 2. 유저 인증 플로우 생성
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, GsonFactory.getDefaultInstance(),  clientSecrets, SCOPES)
			.setDataStoreFactory(new FileDataStoreFactory(new java.io.File(System.getProperty("user.home"), ".google/store/drive")))
			.setAccessType("offline")
			.build();

		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(9090).build();
		Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
		return credential;
    }
    
    public List<File> getFileList(String appName) {
    	List<File> list = new ArrayList<File>();
    	
    	try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Drive service = new Drive.Builder(HTTP_TRANSPORT, GsonFactory.getDefaultInstance(), authorize(HTTP_TRANSPORT))
            		.setApplicationName(appName)
                    .build();
            FileList result = service.files().list()
                    .setPageSize(10)
                    .setFields("nextPageToken, files(id, name)")
                    .execute();
            
            list = result.getFiles();
            if (list == null) {
            	list = new ArrayList<File>();
            }
            info("list:" + list);
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return list;
    }
    
    public void echo(String msg) {
    	System.out.println(msg);
    }
}

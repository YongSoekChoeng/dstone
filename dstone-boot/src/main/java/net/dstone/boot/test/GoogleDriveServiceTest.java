package net.dstone.boot.test;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;




public class GoogleDriveServiceTest {

	private static String CREDENTIALS_FILE_PATH = "D:/AppHome/framework/dstone/dstone-boot/src/main/resources/keys/google/client_secret_318736365485-m22c8qocfuodds1iut2ouunfv3u41tqo.apps.googleusercontent.com.json";
	private static String APPLICATION_NAME = "H-Energy";
	private static JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static List<String> SCOPES = Arrays.asList(
		DriveScopes.DRIVE
	);
	
    private static String DIRECTORY_ID = "10yrMF5dUx-8tQvDJOFY_RguB1vXN8PKN";

    /**
     * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
     * globally shared instance across your application.
     */
    private static FileDataStoreFactory dataStoreFactory;

    private static String UPLOAD_FILE_PATH = DIRECTORY_ID;
    private static String DIR_FOR_DOWNLOADS = "C:/Temp";
    private static java.io.File UPLOAD_FILE = new java.io.File(UPLOAD_FILE_PATH); 
	

    /** Global Drive API client. */
    private static Drive drive;
    


//    public static Credential authorize() throws IOException, GeneralSecurityException {
//    	GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH)).createScoped(SCOPES);
//        return credential;
//    }


    

    /** Authorizes the installed application to access user's protected data. */
    public static Credential authorize(final NetHttpTransport HTTP_TRANSPORT) throws Exception {

		// 1. OAuth 클라이언트 키 파일이 있는지 체크 
		InputStream in = new FileInputStream(CREDENTIALS_FILE_PATH);
		if (in == null) {
			throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
		}
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// 2. 유저 인증 플로우 생성 및 인증토큰 가져오기 
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
			.setDataStoreFactory(new FileDataStoreFactory(new java.io.File(System.getProperty("user.home"), ".google/store/drive")))
			.setAccessType("offline")
			.build();

		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(9090).build();

		Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

		return credential;
		
    }
    
    

    
    public static void main(String[] args) throws GeneralSecurityException, IOException, Exception {
    	GoogleDriveServiceTest test = new GoogleDriveServiceTest();
    	
    	test.test1();
    	//test.test2();
    	
    }
    
    public void test1() {

//      Preconditions.checkArgument(
//              !UPLOAD_FILE_PATH.startsWith("Enter ") && !DIR_FOR_DOWNLOADS.startsWith("Enter "),
//              "Please enter the upload file path and download directory in %s", GoogleDriveServiceTest.class);

      try {

			final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      	
          // authorization 
          Credential credential = authorize(HTTP_TRANSPORT);
          // set up the global Drive instance
          drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
              APPLICATION_NAME).build();
          
//          FileList list = drive.files().list()
//          		.setQ("name='공유스프레드시트1'")
//                  .setCorpora("allDrives")
//                  .setIncludeItemsFromAllDrives(true)
//                  .setSupportsAllDrives(true)
////                  .setIncludeTeamDriveItems(true)
////                  .setSupportsTeamDrives(true)
//                  .setPageSize(10)
//                  .setFields("nextPageToken, files(id, name, owners, parents, createdTime, description, mimeType, "
//                          + "webViewLink, webContentLink, fileExtension, iconLink, thumbnailLink, driveId, originalFilename)")
//                  .setPageToken(TOKENS_DIRECTORY_PATH).execute();
          
          FileList list = drive.files().list()
                  .setPageSize(10)
                  .setFields("nextPageToken, files(id, name)")
                  .execute();
//          pageToken = list.getNextPageToken();
         
          for(Object file : list.getFiles()) {
              System.out.print("file name: " + file);                    
          }
         
          System.out.print("===============>>> connected !!!");         
          
      } catch (Exception e) {
         	e.printStackTrace();
      }

    }
    
    public void test2() {
    	try {
    	    // Build a new authorized API client drive.
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, authorize(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            // Print the names and IDs for up to 10 files.
            FileList result = drive.files().list()
                    .setPageSize(10)
                    //.setFields("nextPageToken, files(id, name)")
                    .execute();


            List<com.google.api.services.drive.model.File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                System.out.println("No files found.");
            } else {
                System.out.println("Files:");
                for (com.google.api.services.drive.model.File file : files) {
                    System.out.println(file);
                }
            }
    //
System.out.println("업로드 시작..");

            com.google.api.services.drive.model.File fileMetaData = new com.google.api.services.drive.model.File();
            FileContent fileContent = null;
            
            /*
            fileMetaData.setName("c.txt"); // 업로드할 파일
            fileContent = new FileContent("text/plain", new java.io.File("D:/Temp/c.txt"));
            */

            fileMetaData.setName("loading.gif"); // 업로드할 파일
            fileContent = new FileContent("image/gif", new java.io.File("D:/Temp/loading.gif"));
            
            
System.out.println("업로드 line ===========>>> 192");


            drive.files().create(fileMetaData,fileContent)
            .setFields("id")
            .execute();
System.out.println("업로드 line ===========>>> 194 fileMetaData.getId():" + fileMetaData.getId());

            String downFileName = "1.zip";
            System.out.println("\n\n다운로드 시작..");
            try {
            	com.google.api.services.drive.model.File downloadFile = files.stream()
            			.filter(downFile->downFile.getName().equals(downFileName))
            			.findAny()
            			.orElseThrow(()->new FileNotFoundException(downFileName+"은 존재하지 않는 파일입니다"));
            	
                String downloadFileId = downloadFile.getId();
                OutputStream outputStream = new ByteArrayOutputStream();
                drive.files().get(downloadFileId)
                        .executeMediaAndDownloadTo(outputStream);
                ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream) outputStream;
                try(OutputStream writeStream = new FileOutputStream(downFileName)) {
                    byteArrayOutputStream.writeTo(writeStream);
                }
            } catch (GoogleJsonResponseException e) {
                // TODO(developer) - handle error appropriately
                System.err.println("Unable to move file: " + e.getDetails());
                throw e;
            }



		} catch (Exception e) {
			e.printStackTrace();
		}
    }
}

package net.dstone.boot.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;


public class GoogleSheetServiceTest {
	

	private static final String CREDENTIALS_FILE_PATH = "D:/AppHome/framework/dstone/dstone-boot/src/main/resources/keys/google/client_secret_318736365485-68nq1p97g9u3t9isu7a50km7upsiggc6.apps.googleusercontent.com.json";
	private static final String APPLICATION_NAME = "H-Energy";
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
	
    public static Credential authorize() throws IOException, GeneralSecurityException {
    	GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH)).createScoped(SCOPES);
        return credential;
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
    	
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        String SPREADSHEET_ID = "1K98BUHLYVGABWYpiRxtIo3KLe6rc2rBIfoaiNjNx6hU";
        String range = "시트1!A1";

        Sheets sheetsService = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, authorize())
                .setApplicationName(APPLICATION_NAME)
                .build();

        // 읽기
        
        List<String> ranges = Arrays.asList(range + ":Z99" );
        BatchGetValuesResponse readResult = sheetsService.spreadsheets().values()
          .batchGet(SPREADSHEET_ID)
          .setRanges(ranges)
          .execute();
        
        List<ValueRange> result = readResult.getValueRanges();
        if( result != null ) {
        	Iterator<ValueRange> iter = result.iterator();
        	while( iter.hasNext() ) {
        		ValueRange row = iter.next();
        		System.out.println(row);
        	}
        }
        
        
        // 쓰기
        
        List<List<Object>> values = Arrays.asList(
                Arrays.asList("Name", "Age", "Gender")
                ,Arrays.asList("Alice", 25, "Female3")
                ,Arrays.asList("Bob", 30, "Male")
                ,Arrays.asList("", "", "")
                ,Arrays.asList("choi", 32, "22222222")
                );
        ValueRange data = new ValueRange().setValues(values);


        sheetsService.spreadsheets().values().update(SPREADSHEET_ID, range, data)
                .setValueInputOption("USER_ENTERED")
                .execute();
        

        // 수정
        /*
		ValueRange appendBody = new ValueRange().setValues(Arrays.asList(Arrays.asList("Total", "=E1+E4")));
		AppendValuesResponse appendResult = sheetsService.spreadsheets().values().append(SPREADSHEET_ID, "A1", appendBody)
				.setValueInputOption("USER_ENTERED").setInsertDataOption("INSERT_ROWS").setIncludeValuesInResponse(true)
				.execute();
		ValueRange total = appendResult.getUpdates().getUpdatedData();
		*/

    }
}

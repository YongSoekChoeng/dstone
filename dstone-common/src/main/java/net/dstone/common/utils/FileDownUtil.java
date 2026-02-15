package net.dstone.common.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.common.config.ConfigProperty;

/**
 * @author db2admin ORIGINAL_FILE_NAME, SAVED_FILE_NAME 을 파라메터로 호출한다.
 */

@Controller
public class FileDownUtil extends jakarta.servlet.http.HttpServlet {

	@Autowired 
	ConfigProperty configProperty;

	/**
	 * <code>doGet</code> Process incoming HTTP doGet requests
	 * 
	 * @param request
	 * @param response
	 */
	public void doGet(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws jakarta.servlet.ServletException, java.io.IOException {

		performTask(request, response);

	}

	/**
	 * <code>doPost</code> Process incoming HTTP doGet requests
	 * 
	 * @param request
	 * @param response
	 */
	public void doPost(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws jakarta.servlet.ServletException, java.io.IOException {

		performTask(request, response);

	}

	/**
	 * <code>performTask</code> Process incoming HTTP doGet requests
	 * 
	 * @param request
	 * @param response
	 */
	public void performTask(HttpServletRequest req, HttpServletResponse res) {
		
		if( req.getParameterMap().containsKey("ORI_FILE_NAME") && req.getParameterValues("ORI_FILE_NAME").length > 1 ) {
			String saveFileName = req.getParameter("SAVE_FILE_NAME");
			String[] oriFileNameArray = req.getParameterValues("ORI_FILE_NAME");
			List<String> oriFileNameList = new ArrayList<String>();
			for(String ORI_FILE_NAME : oriFileNameArray) {
				oriFileNameList.add(ORI_FILE_NAME);
			}
			multiFileDown(saveFileName, oriFileNameList, req, res);
		}else {
			String saveFileName = req.getParameter("SAVE_FILE_NAME");
			String oriFileName = req.getParameter("ORI_FILE_NAME");
			singleFileDown(oriFileName, saveFileName, req, res);
		}
		
	}
	
	private void singleFileDown(String oriFileName, String saveFileName, HttpServletRequest req, HttpServletResponse res) {

		String FILEUP_WEB_DIR = configProperty.getProperty("resources.fileUp.path");
		
		String path = "";

		try {

			oriFileName = new String(oriFileName.getBytes(), "UTF-8");
			
			net.dstone.common.utils.LogUtil.sysout( "oriFileName["+oriFileName+"] saveFileName["+saveFileName+"] FILEUP_WEB_DIR["+FILEUP_WEB_DIR+"]" );

			path = FILEUP_WEB_DIR;
			
			java.io.File f = new java.io.File(path + "/" + saveFileName);

			byte b[] = new byte[1024];

			BufferedInputStream fin = new BufferedInputStream(new java.io.FileInputStream(f));
			BufferedOutputStream fout = new BufferedOutputStream(res.getOutputStream());

			String strClient = req.getHeader("User-Agent");

			if (strClient.indexOf("MSIE 5.5") != -1) {
				res.setHeader("Content-Type", "doesn/matter; charset=euc-kr");
				res.setHeader("Content-Disposition", "filename=" + oriFileName + ";");
			} else {
				res.setHeader("Content-Type", "application/octet-stream; charset=euc-kr");
				res.setHeader("Content-Disposition", "attachment;filename=" + oriFileName + ";");
			}
			;
			res.setHeader("Content-Transfer-Encoding", "binary;");
			res.setHeader("Pragma", "no-cache;");
			res.setHeader("Expires", "-1;");
			for (int i; (i = fin.read(b)) != -1;) {
				fout.write(b, 0, i);
				fout.flush();
			}
			fin.close();
			fout.flush();
			fout.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	private void multiFileDown(String saveFileName, List<String> oriFileNameList, HttpServletRequest req, HttpServletResponse res) {

		if( oriFileNameList != null && oriFileNameList.size() > 0 && !StringUtil.isEmpty(saveFileName)) {

			ZipOutputStream zos = null;
			
	        // 본격적인 zip 파일을 만들어 내기 위한 로직
	        try{

				if( !saveFileName.endsWith(".zip") ) {
					saveFileName = saveFileName + ".zip";
				}
				saveFileName = URLEncoder.encode(saveFileName,"UTF-8").replaceAll("\\+", "%20");
				
		        // ======================== 파일 다운로드 위한 response 세팅 ========================
				res.setContentType("application/zip");
				res.setHeader("Content-Disposition", "attachment; filename=" + new String( saveFileName.getBytes(StandardCharsets.UTF_8)) + ".zip;");
				res.setStatus(HttpServletResponse.SC_OK);
		        // =============================================================================
				
	        	zos = new ZipOutputStream(res.getOutputStream());
	        	
				for(String oriFileName : oriFileNameList) {
	                Path path = Paths.get(oriFileName);
	                try (FileInputStream fis = new FileInputStream(path.toFile())) {
	                    // 압축될 파일명을 ZipEntry에 담아준다
	                    ZipEntry zipEntry = new ZipEntry(path.getFileName().toString());
	                    // 압축될 파일명을 ZipOutputStream 에 담아준다
	                    zos.putNextEntry(zipEntry);
	                    byte[] buffer = new byte[1024];
	                    int length;
	                    while ((length = fis.read(buffer)) >= 0) {
	                        zos.write(buffer, 0, length);
	                    }
	                } catch (FileNotFoundException e) {
	                    res.setStatus(HttpServletResponse.SC_NOT_FOUND);
	                    throw new IllegalArgumentException("파일 변환 작업중, [ " + oriFileName + " ] 파일을 찾을 수 없습니다.");
	                } catch (IOException e) {
	                    res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	                    throw new IllegalArgumentException("파일 변환 작업중, [ " + oriFileName + " ] 파일을 다운로드 할 수 없습니다.");
	                } finally {
	                    // ZipOutputStream 에 담아둔 압축될 파일명을 flush 시켜준다
	                    zos.flush();
	                    // ZipOutputStream 에 담아둔 압축될 파일명을 close 시켜준다
	                    zos.closeEntry();
	                }
				}

	        } catch (Exception e) {
	            e.printStackTrace();
	        } finally {
	        	if( zos != null ) {
	        		try {
	        			zos.close();
					} catch (Exception e2) {
						// TODO: handle exception
					}
	        	}
	        }
		}

	}
}
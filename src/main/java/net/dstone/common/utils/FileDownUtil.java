package net.dstone.common.utils;

import java.io.*;
import javax.servlet.http.*;

/**
 * @author db2admin ORIGINAL_FILE_NAME, SAVED_FILE_NAME 을 파라메터로 호출한다.
 */
public class FileDownUtil extends javax.servlet.http.HttpServlet {
	/**
	 * <code>doGet</code> Process incoming HTTP doGet requests
	 * 
	 * @param request
	 *            FWObject that encapsulates the request to the servlet
	 * @param response
	 *            FWObject that encapsulates the response from the servlet
	 */
	public void doGet(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {

		performTask(request, response);

	}

	/**
	 * <code>doPost</code> Process incoming HTTP doGet requests
	 * 
	 * @param request
	 *            FWObject that encapsulates the request to the servlet
	 * @param response
	 *            FWObject that encapsulates the response from the servlet
	 */
	public void doPost(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {

		performTask(request, response);

	}

	/**
	 * <code>performTask</code> Process incoming HTTP doGet requests
	 * 
	 * @param request
	 *            FWObject that encapsulates the request to the servlet
	 * @param response
	 *            FWObject that encapsulates the response from the servlet
	 */
	public void performTask(HttpServletRequest req, HttpServletResponse res) {

		String ORI_FILE_NAME = req.getParameter("ORI_FILE_NAME");
		String SAVE_FILE_NAME = req.getParameter("SAVE_FILE_NAME");
		String FILEUP_WEB_DIR = net.dstone.common.utils.PropUtil.getInstance().getProp("app", "FILEUP_WEB_DIR");
		
		String path = "";

		try {

			ORI_FILE_NAME = new String(ORI_FILE_NAME.getBytes(), "UTF-8");
			
			System.out.println( "ORI_FILE_NAME["+ORI_FILE_NAME+"] SAVE_FILE_NAME["+SAVE_FILE_NAME+"] FILEUP_WEB_DIR["+FILEUP_WEB_DIR+"]" );

			path = FILEUP_WEB_DIR;
			
			java.io.File f = new java.io.File(path + "/" + SAVE_FILE_NAME);

			byte b[] = new byte[1024];

			BufferedInputStream fin = new BufferedInputStream(new java.io.FileInputStream(f));
			BufferedOutputStream fout = new BufferedOutputStream(res.getOutputStream());

			int count = 0;
			String strClient = req.getHeader("User-Agent");

			if (strClient.indexOf("MSIE 5.5") != -1) {
				res.setHeader("Content-Type", "doesn/matter; charset=euc-kr");
				res.setHeader("Content-Disposition", "filename=" + ORI_FILE_NAME + ";");
			} else {
				res.setHeader("Content-Type", "application/octet-stream; charset=euc-kr");
				res.setHeader("Content-Disposition", "attachment;filename=" + ORI_FILE_NAME + ";");
			}
			;
			res.setHeader("Content-Transfer-Encoding", "binary;");
			res.setHeader("Pragma", "no-cache;");
			res.setHeader("Expires", "-1;");
			for (int i; (i = fin.read(b)) != -1;) {
				count++;
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
}

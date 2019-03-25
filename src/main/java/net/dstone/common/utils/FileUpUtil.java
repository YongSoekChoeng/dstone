package net.dstone.common.utils;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

import com.oreilly.servlet.*;
import com.oreilly.servlet.multipart.DefaultFileRenamePolicy;

public class FileUpUtil {

	class FileRenamePolicy implements com.oreilly.servlet.multipart.FileRenamePolicy {
		public java.io.File rename(java.io.File f) {
			String path = "";
			String fileName = "";
			String fileNewName = "";
			String fileExt = "";
			java.io.File reFile = null;
			try {
				path = f.getParent();
				fileName = f.getName();
				fileExt = FileUtil.getFileExt(fileName);
				fileNewName = (new net.dstone.common.utils.GuidUtil()).getNewGuid() + "." + fileExt;
				reFile = new java.io.File(path + "/" + fileNewName);
			} catch (Exception e) {
				// TODO: handle exception
			}
			return reFile;
		}
	}

	private MultipartRequest multiRequest;
	private java.util.Properties uploadInfo = new java.util.Properties();
	private boolean isMultiPart = false;
	private HttpServletRequest request;
	private String encoding = "utf-8"; // utf-8 euc-kr 환경에 맞게 수정.

	public FileUpUtil(HttpServletRequest request, HttpServletResponse response) throws Exception {
		java.util.ArrayList<java.util.Properties> uploadList = new java.util.ArrayList<java.util.Properties>();
		String FILEUP_WEB_DIR = net.dstone.common.utils.PropUtil.getInstance().getProp("app", "FILEUP_WEB_DIR");
		try {

			if ((request.getHeader("Content-Type") != null) && (request.getHeader("Content-Type").toUpperCase().indexOf("MULTIPART") != -1)) {
				isMultiPart = true;
			}
			int maxPostSize = 15 * 1024 * 1024; // 15MB
			String saveDirectory = FILEUP_WEB_DIR;
			net.dstone.common.utils.FileUtil.makeDir(saveDirectory);
			uploadInfo.put("SAVE_DIRECTORY", saveDirectory);
			uploadInfo.put("MAX_POST_SIZE", String.valueOf(maxPostSize));
			uploadInfo.put("UPLOAD_LIST", uploadList);

			request.setAttribute("uploadInfo", uploadInfo);

			if (isMultiPart) {

				multiRequest = new MultipartRequest(request, saveDirectory, maxPostSize, encoding, new net.dstone.common.utils.FileUpUtil.FileRenamePolicy());
				Enumeration formNames = multiRequest.getFileNames(); // 폼의 이름 반환
				String fileInput = "";
				String fileName = "";
				String type = "";
				File fileObj = null;
				String originFileName = "";
				String fileExtend = "";
				String fileSize = "";
				while (formNames.hasMoreElements()) {
					java.util.Properties uploadFileRow = new java.util.Properties();
					fileInput = (String) formNames.nextElement(); // 파일인풋 이름
					fileName = multiRequest.getFilesystemName(fileInput); // 파일명
					if (fileName == null) {
						continue;
					}
					if (fileName != null) {
						type = multiRequest.getContentType(fileInput); // 콘텐트타입
						fileObj = multiRequest.getFile(fileInput); // 파일객체
						originFileName = multiRequest.getOriginalFileName(fileInput); // 초기파일명
						fileExtend = fileName.substring(fileName.lastIndexOf(".") + 1); // 파일 확장자
						fileSize = String.valueOf(fileObj.length()); // 파일크기
					}
					uploadFileRow.setProperty("SAVED_FILE_NAME", fileName);
					uploadFileRow.setProperty("CONTENTS_TYPE", type);
					uploadFileRow.setProperty("ORIGINAL_FILE_NAME", originFileName);
					uploadFileRow.setProperty("FILE_EXTEND", fileExtend);
					uploadFileRow.setProperty("FILE_SIZE", fileSize);
					uploadList.add(uploadFileRow);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getParameter(String key) {
		String val = null;
		try {
			if (isMultiPart) {
				val = getMultiRequest().getParameter(key);
			} else {
				val = request.getParameter(key);
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
		return val;
	}

	public String[] getParameterValues(String key) {
		String[] val = null;
		try {
			if (isMultiPart) {
				val = getMultiRequest().getParameterValues(key);
			} else {
				val = request.getParameterValues(key);
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
		return val;
	}

	public java.util.Enumeration getParameterNames() {
		java.util.Enumeration val = null;
		try {
			if (isMultiPart) {
				val = getMultiRequest().getParameterNames();
			} else {
				val = request.getParameterNames();
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
		return val;
	}

	public java.util.Properties getUploadInfo() {
		return uploadInfo;
	}

	public void setUploadInfo(java.util.Properties uploadInfo) {
		this.uploadInfo = uploadInfo;
	}

	public MultipartRequest getMultiRequest() {
		return multiRequest;
	}

	public void setMultiRequest(MultipartRequest multiRequest) {
		this.multiRequest = multiRequest;
	}

	public void deleteFile(String filePath) {
		String result = "";

		try {
			java.io.File f = new java.io.File(filePath);
			if (f.exists()) {
				f.delete();
			}
		} catch (Exception e) {

		}
	}

}

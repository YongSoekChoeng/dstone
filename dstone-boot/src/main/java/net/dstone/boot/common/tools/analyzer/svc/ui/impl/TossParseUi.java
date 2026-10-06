package net.dstone.boot.common.tools.analyzer.svc.ui.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.svc.ui.ParseUi;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

public class TossParseUi implements ParseUi {
	
	/**
	 * UI파일로부터 UI아이디 추출
	 * @param uiFile 
	 * @return
	 */
	public String getUiId(String uiFile) throws Exception {
		uiFile = StringUtil.replace(uiFile, "\\", "/");
		
		String fileName = FileUtil.getFileName(uiFile, false);
		String subPath = FileUtil.getFilePath(uiFile);
		subPath = StringUtil.replace(subPath, AppAnalyzer.WEB_ROOT_PATH, "");
		
		if(subPath.startsWith("/")) {
			subPath = subPath.substring(subPath.indexOf("/")+1);
		}
		if(subPath.endsWith("/")) {
			subPath = subPath.substring(0, subPath.lastIndexOf("/"));
		}
		subPath = StringUtil.replace(subPath, "/", ".");
		if( !StringUtil.isEmpty(subPath)) {
			fileName = subPath + "." + fileName;
		}

		String uiId = fileName;
		return uiId;
	}

	/**
	 * UI파일로부터 UI명 추출
	 * @param uiFile
	 * @return
	 */
	public String getUiName(String uiFile) throws Exception {
		String uiName = "";
		String[] lines = FileUtil.readFileByLines(uiFile);
		List<String> keywordList = new ArrayList<String>();
		keywordList.add("화면 설명 :");
		
		if(lines!=null) {
			for(String line : lines) {
				line = line.trim();
				for(String keyword : keywordList) {
					if(line.indexOf(keyword) != -1) {
						uiName = line.substring(line.indexOf(keyword) + keyword.length());
						break;
					}
				}
			}
		}
		
		if(!StringUtil.isEmpty(uiName)) {
			uiName = StringUtil.replace(uiName, ":", "");
			uiName = StringUtil.replace(uiName, ";", "");
			uiName = StringUtil.replace(uiName, " ", "");
		}
		return uiName;
	}
	
	/**
	 * UI파일로부터 링크목록 추출
	 * @param uiFile
	 * @return
	 */
	@Override
	public List<String> getUiLinkList(String uiFile) throws Exception {
		List<String> uiLinkList = new ArrayList<String>();
		
		List<String> keywordList = new ArrayList<String>();
		keywordList.add(".do");
		keywordList.add(".go");
		keywordList.add(".pop");
		keywordList.add(".ajax");
		
		try {
			if(FileUtil.isFileExist(uiFile)) {
				String link = "";
				String[] lines = FileUtil.readFileByLines(uiFile);
				boolean isUsedInTheLine = false;
				if(lines!=null) {
					String[] div = {" ", "\"", "'", ")"};
					for(String line : lines) {
						line = line.trim();
						line = StringUtil.replace(line, "?", " ?");
						link = "";
						isUsedInTheLine = false;
						for(String keyword : keywordList) {
							if( line.indexOf(keyword)>-1 ) {
								link = StringUtil.beforeWord(line, keyword, div);
								if( !StringUtil.isEmpty(link) && link.startsWith("/") ) {
									link = link + keyword;
									isUsedInTheLine = true;
									break;
								}
							}
						}
						if(isUsedInTheLine) {
							uiLinkList.add(link);
							continue;
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return uiLinkList.stream().distinct().collect(Collectors.toList());
	}

}

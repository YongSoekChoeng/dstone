package net.dstone.common.tools;

public class CallHierarchyAnalyzer {
	
	private static CallHierarchyAnalyzer call = null;
	
	public java.util.Properties javaList = null;
	public java.util.Properties jspList = null;
	public java.util.Properties fileList = null;
	
	private String rootPath = "";
	
	private CallHierarchyAnalyzer(){
	}
	
	private void debug(Object msg){
		System.out.println(msg);
	}
	
	public static CallHierarchyAnalyzer getInstance(String rootPath){
		if(call == null){
			call = new CallHierarchyAnalyzer();
		}
		call.rootPath = rootPath;
		return call;
	}
	
	public String report(Report report, String classFullNm, String methodNm){
		StringBuffer buff = new StringBuffer();
		buff.append("\n");
		buff.append("||********************* 클래스["+classFullNm+"] 메소드["+methodNm+"] 호출구조 검색 시작 *********************||").append("\n");
		buff.append(report.toString());
		buff.append("||********************* 클래스["+classFullNm+"] 메소드["+methodNm+"] 호출구조 검색 종료 *********************||").append("\n");
		return buff.toString();
	}
	
	public Report searchJava(int level, String classNm, String methodNm) throws Exception{
		JavaFinder java = new JavaFinder(level, classNm, methodNm);
		return java.find();
	}
	
	public class Report{
		int level;
		String containerNm;
		String itemNm;
		java.util.LinkedList childrun = new java.util.LinkedList();
		
		Report(int level, String containerNm, String itemNm){
			this.level = level;
			this.containerNm = containerNm;
			this.itemNm = itemNm;
		}
		
		void addChild(Report child){
			this.childrun.add(child);
		}
		
		public String toString(){
			StringBuffer buff = new StringBuffer();
			String space = "";
			for(int i=0; i<this.level; i++){
				space = space + "\t";
			}
			buff.append(space);
			if(this.level > 0){
				buff.append("->");
			}
			buff.append(containerNm);
			if(itemNm != null){
				buff.append(".").append(itemNm);
			}
			buff.append("\n");
			for(int i=0; i<childrun.size(); i++){
				buff.append(childrun.get(i));
			}
			return buff.toString();
		}
	}

	private class JavaFinder{
		
		int level;
		String classFullNm;
		String methodNm;
		
		JavaFinder(int level, String classFullNm, String methodNm) throws Exception{
			if(classFullNm == null){
				throw new Exception("클래스명을 입력해 주세요.");
			}
			if(methodNm == null){
				throw new Exception("메소드명을 입력해 주세요.");
			}
			if(classFullNm.indexOf(".") == -1){
				throw new Exception("클래스명은 풀패키지명으로 입력해 주세요.");
			}
			this.level = level;
			this.classFullNm = classFullNm;
			this.methodNm = methodNm;
			fillJavaList();
		}

		private void fillJavaList(){
			if(javaList == null){
				javaList = new java.util.Properties();
				String[] fileList = net.dstone.common.utils.FileUtil.readFileListAll(rootPath);
				String packageNm = "";
				String clzzNm = "";
				String fileConts = "";
				if(fileList != null){
					for(int i=0 ; i<fileList.length; i++){
						if(fileList[i].toLowerCase().endsWith(".java")){
							fileConts = net.dstone.common.utils.FileUtil.readFile(fileList[i]);
							fileConts = net.dstone.common.utils.StringUtil.replace(fileConts, "\r\n", "\n");
							fileConts = net.dstone.common.utils.StringUtil.replace(fileConts, "\r\n", "\n");
							fileConts = net.dstone.common.utils.StringUtil.replace(fileConts, "\n\n", "\n");
							fileConts = net.dstone.common.utils.StringUtil.replace(fileConts, "\n\n", "\n");
							fileConts = net.dstone.common.utils.StringUtil.replace(fileConts, "\t\t", "\t");
							fileConts = net.dstone.common.utils.StringUtil.replace(fileConts, "\t\t", "\t");
							
							fileConts = net.dstone.common.utils.StringUtil.replace(fileConts, "\t", " ");
							fileConts = net.dstone.common.utils.StringUtil.replace(fileConts, "  ", " ");
							
							String sfileConts = fileConts.substring(fileConts.indexOf("package"));
							packageNm = sfileConts.substring(sfileConts.indexOf("package ")+8, sfileConts.indexOf(";"));
							clzzNm = net.dstone.common.utils.FileUtil.getFileName(fileList[i], false);
							javaList.setProperty(packageNm + "." + clzzNm, fileConts);
						}
					}
				}
			}
		}
		
		
		protected Report find(){
			String filePath = rootPath;
			String[] keyword = {this.classFullNm, this.methodNm};
			String[] extFilter = {"java"};
			boolean searchSaperatedOnly = false;

			Report report = null;
			
			try{
				if(this.classFullNm != null && this.methodNm == null){
					keyword = new String[1];
					keyword[0] = this.classFullNm;
				}else if(this.classFullNm != null && this.methodNm != null){
					keyword = new String[2];
					keyword[0] = this.classFullNm;
					keyword[1] = this.methodNm;
				}else{
					throw new Exception("키워드가 없습니다.");
				}
				report = new Report(this.level, this.classFullNm, this.methodNm);
				report = findReculsivly(report);

			}catch(Exception e){
				debug(e);
			}
			return report;
		}
		
		private Report findReculsivly(Report report){
			
			boolean isUsed = false;
			boolean isClassImported = false;
			boolean isSamePackage = false;
			
			String classFullNm = report.containerNm;
			String methodNm = report.itemNm;
			String fileConts = null;
			String[] fileLines = null;
			String fileLine = "";
			String packageNm = "";
			String[] packages = null;
			String packagePath = "";
			String classNm = "";

			String parentClassNm = "";
			String parentMethodNm = "";
			
			java.util.ArrayList classAlias = null;
			String alias = "";
			String keyword = "";
			
			String[] aliasDiv = {" ", "\t", "\n", "\r", "(", ")", "{", "}", "[", "]", ".", ",", ";"};
			String[] methodDiv = {" ", "\t", "\n", "\r", "(", ")"};
			packageNm = classFullNm.substring(0, classFullNm.lastIndexOf("."));
			packages = net.dstone.common.utils.StringUtil.toStrArray(packageNm, ".");
			classNm = classFullNm.substring(classFullNm.lastIndexOf(".")+1);
			
			java.util.Enumeration keys = javaList.keys();
			String key = "";
			
			/* 00. 파일 루프 시작 */
			while( keys.hasMoreElements() ){
				key = (String)keys.nextElement();

				parentClassNm = key;
				parentMethodNm = "";
				
				fileConts = javaList.getProperty(key);
				fileLines = net.dstone.common.utils.StringUtil.toStrArray(fileConts, "\n");

				isUsed = false;
				isClassImported = false;
				isSamePackage = false;
				
				classAlias = new java.util.ArrayList();
				alias = "";
				keyword = "";
				
				for(int i=0; i<fileLines.length; i++){
					fileLine = fileLines[i].trim();

					/* 01. 동일패키지 여부 체크 시작 */
					if(fileLine.indexOf("package ") != -1){
						fileLine = net.dstone.common.utils.StringUtil.replace(fileLine, " ", "");
						if(packageNm.equals(fileLine.substring(fileLine.indexOf("package")+7, fileLine.indexOf(";")))){
							isSamePackage = true;
						}
					}
					/* 01. 동일패키지 여부 체크 끝 */
					
					/* 02. Import 되었는지 여부 체크 시작 */
					if(fileLine.indexOf("import ") != -1){
						fileLine = net.dstone.common.utils.StringUtil.replace(fileLine, " ", "");
						if(!isClassImported){
							// (패키지단위)
							packagePath = "";
							for(int k=0; k<packages.length; k++){
								packagePath = packagePath + packages[k];
								if(k>0){
									packagePath = packagePath + ".";
								}
								if(fileLine.indexOf("import" + packagePath + "*;") != -1){
									isClassImported = true;
								}
							}
							// (클래스단위)
							if(fileLine.indexOf("import" + classFullNm +";") != -1){
								isClassImported = true;
							}
						}
					}
					/* 02. Import 되었는지 여부 체크 끝 */

					/* 03. 메소드명 검색 시작 */
					if( fileLine.indexOf("(") != -1 ){
						if(net.dstone.common.utils.StringUtil.isIncluded(fileLine, "public", true) || net.dstone.common.utils.StringUtil.isIncluded(fileLine, "protected", true) || net.dstone.common.utils.StringUtil.isIncluded(fileLine, "private", true)){
							parentMethodNm = net.dstone.common.utils.StringUtil.beforeWord(fileLine, "(", methodDiv); 
						}
					}
					/* 03. 메소드명 검색 끝 */

					/* 04. 클래스의 ALIAS 검색  시작 */
					if( net.dstone.common.utils.StringUtil.isIncluded(fileLine, classFullNm, true) ){
						alias = net.dstone.common.utils.StringUtil.nextWord(fileLine, classFullNm, aliasDiv); 
						classAlias.add(alias);
					}
					if(isSamePackage || isClassImported){
						if( net.dstone.common.utils.StringUtil.isIncluded(fileLine, classNm, true) ){
							alias = net.dstone.common.utils.StringUtil.nextWord(fileLine, classNm, aliasDiv); 
							classAlias.add(alias);
						}
					}
					/* 04. 클래스의 ALIAS 검색  끝 */
					
					/* 05. 단어검색  시작 */
					for(int k=0; k<classAlias.size(); k++){
						alias = (String)classAlias.get(k);
						keyword = alias + "." + methodNm;
						isUsed = net.dstone.common.utils.StringUtil.isIncluded(fileLine, keyword, true);
						if(isUsed){
							break;
						}
					}
					
					if( isUsed ){
						Report parentReport = new Report(report.level+1, parentClassNm, parentMethodNm);
						if(findReculsivly(parentReport) != null){
							report.addChild(parentReport);
						}
						break;
					}
					/* 05. 단어검색  끝 */
				}
			}
			/* 00. 파일 루프 끝 */
			return report;
		}
	}
	
	private class JspFinder{
		
		String[] webRoot;
		int level;
		String classFullNm;
		String methodNm;
		
		JspFinder(String[] webRoot, int level, String classFullNm, String methodNm) throws Exception{
			if(webRoot == null){
				throw new Exception("웹루트를 입력해 주세요.");
			}
			if(classFullNm == null){
				throw new Exception("클래스명을 입력해 주세요.");
			}
			if(methodNm == null){
				throw new Exception("메소드명을 입력해 주세요.");
			}
			if(classFullNm.indexOf(".") == -1){
				throw new Exception("클래스명은 풀패키지명으로 입력해 주세요.");
			}
			this.webRoot = webRoot;
			this.level = level;
			this.classFullNm = classFullNm;
			this.methodNm = methodNm;
			fillJspList();
		}
		
		private void fillJspList(){
			if(jspList == null){
				jspList = new java.util.Properties();
				String[] fileList = net.dstone.common.utils.FileUtil.readFileListAll(rootPath);
				String packageNm = "";
				String jspNm = "";
				String fileConts = "";
				if(fileList != null){
					for(int i=0 ; i<fileList.length; i++){
						if(fileList[i].toLowerCase().endsWith(".jsp")){
							fileConts = net.dstone.common.utils.FileUtil.readFile(fileList[i]);
							
							packageNm = "";
							jspNm = net.dstone.common.utils.FileUtil.getFileName(fileList[i], false);
							javaList.setProperty(packageNm + "." + jspNm, fileConts);
						}
					}
				}
			}
		}
		
		protected Report find(){
			String filePath = rootPath;
			String[] keyword = {this.classFullNm, this.methodNm};
			String[] extFilter = {"jsp"};
			boolean searchSaperatedOnly = false;

			Report report = null;
			
			try{
				if(this.classFullNm != null && this.methodNm == null){
					keyword = new String[1];
					keyword[0] = this.classFullNm;
				}else if(this.classFullNm != null && this.methodNm != null){
					keyword = new String[2];
					keyword[0] = this.classFullNm;
					keyword[1] = this.methodNm;
				}else{
					throw new Exception("키워드가 없습니다.");
				}
				report = new Report(this.level, this.classFullNm, this.methodNm);
				report = findReculsivly(report);

			}catch(Exception e){
				debug(e);
			}
			return report;
		}
		
		private Report findReculsivly(Report report){
			
			boolean isUsed = false;
			boolean isClassImported = false;
			boolean isSamePackage = false;
			
			String classFullNm = report.containerNm;
			String methodNm = report.itemNm;
			String fileConts = null;
			String[] fileLines = null;
			String fileLine = "";
			String packageNm = "";
			String[] packages = null;
			String packagePath = "";
			String classNm = "";

			String parentClassNm = "";
			String parentMethodNm = "";
			
			java.util.ArrayList classAlias = null;
			String alias = "";
			String keyword = "";
			
			String[] aliasDiv = {" ", "\t", "\n", "\r", "(", ")", "{", "}", "[", "]", ".", ",", ";"};
			String[] methodDiv = {" ", "\t", "\n", "\r", "(", ")"};
			packageNm = classFullNm.substring(0, classFullNm.lastIndexOf("."));
			packages = net.dstone.common.utils.StringUtil.toStrArray(packageNm, ".");
			classNm = classFullNm.substring(classFullNm.lastIndexOf(".")+1);
			
			java.util.Enumeration keys = jspList.keys();
			String key = "";
			
			/* 00. 파일 루프 시작 */
			while( keys.hasMoreElements() ){
				key = (String)keys.nextElement();

				parentClassNm = key;
				parentMethodNm = "";
				
				fileConts = jspList.getProperty(key);
				fileLines = net.dstone.common.utils.StringUtil.toStrArray(fileConts, "\n");

				isUsed = false;
				isClassImported = false;
				isSamePackage = false;
				
				classAlias = new java.util.ArrayList();
				alias = "";
				keyword = "";
				
				for(int i=0; i<fileLines.length; i++){
					fileLine = fileLines[i].trim();

					/* 01. 동일패키지 여부 체크 시작 */
					if(fileLine.indexOf("package ") != -1){
						fileLine = net.dstone.common.utils.StringUtil.replace(fileLine, " ", "");
						if(packageNm.equals(fileLine.substring(fileLine.indexOf("package")+7, fileLine.indexOf(";")))){
							isSamePackage = true;
						}
					}
					/* 01. 동일패키지 여부 체크 끝 */
					
					/* 02. Import 되었는지 여부 체크 시작 */
					if(fileLine.indexOf("import ") != -1){
						fileLine = net.dstone.common.utils.StringUtil.replace(fileLine, " ", "");
						if(!isClassImported){
							// (패키지단위)
							packagePath = "";
							for(int k=0; k<packages.length; k++){
								packagePath = packagePath + packages[k];
								if(k>0){
									packagePath = packagePath + ".";
								}
								if(fileLine.indexOf("import" + packagePath + "*;") != -1){
									isClassImported = true;
								}
							}
							// (클래스단위)
							if(fileLine.indexOf("import" + classFullNm +";") != -1){
								isClassImported = true;
							}
						}
					}
					/* 02. Import 되었는지 여부 체크 끝 */

					/* 03. 메소드명 검색 시작 */
					if( fileLine.indexOf("(") != -1 ){
						if(net.dstone.common.utils.StringUtil.isIncluded(fileLine, "public", true) || net.dstone.common.utils.StringUtil.isIncluded(fileLine, "protected", true) || net.dstone.common.utils.StringUtil.isIncluded(fileLine, "private", true)){
							parentMethodNm = net.dstone.common.utils.StringUtil.beforeWord(fileLine, "(", methodDiv); 
						}
					}
					/* 03. 메소드명 검색 끝 */

					/* 04. 클래스의 ALIAS 검색  시작 */
					if( net.dstone.common.utils.StringUtil.isIncluded(fileLine, classFullNm, true) ){
						alias = net.dstone.common.utils.StringUtil.nextWord(fileLine, classFullNm, aliasDiv); 
						classAlias.add(alias);
					}
					if(isSamePackage || isClassImported){
						if( net.dstone.common.utils.StringUtil.isIncluded(fileLine, classNm, true) ){
							alias = net.dstone.common.utils.StringUtil.nextWord(fileLine, classNm, aliasDiv); 
							classAlias.add(alias);
						}
					}
					/* 04. 클래스의 ALIAS 검색  끝 */
					
					/* 05. 단어검색  시작 */
					for(int k=0; k<classAlias.size(); k++){
						alias = (String)classAlias.get(k);
						keyword = alias + "." + methodNm;
						isUsed = net.dstone.common.utils.StringUtil.isIncluded(fileLine, keyword, true);
						if(isUsed){
							break;
						}
					}
					
					if( isUsed ){
						Report parentReport = new Report(report.level+1, parentClassNm, parentMethodNm);
						if(findReculsivly(parentReport) != null){
							report.addChild(parentReport);
						}
						break;
					}
					/* 05. 단어검색  끝 */
				}
			}
			/* 00. 파일 루프 끝 */
			return report;
		}
		
	}

}

package net.dstone.boot.common.tools.analyzer.svc.clzz.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dstone.common.core.BaseObject;
import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.consts.ClzzKind;
import net.dstone.boot.common.tools.analyzer.svc.SvcAnalyzer;
import net.dstone.boot.common.tools.analyzer.svc.clzz.ParseClzz;
import net.dstone.boot.common.tools.analyzer.util.ParseUtil;
import net.dstone.boot.common.tools.analyzer.vo.ClzzVo;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

public class TextParseClzz extends BaseObject implements ParseClzz {

	/**
	 * 패키지ID 추출
	 * @param classFile 클래스파일
	 * @return
	 */
	@Override
	public String getPackageId(String classFile) throws Exception {
		String pkg = "";
		String[] lines = FileUtil.readFileByLines(classFile);
		if(lines != null) {
			String keyword = "package";
			for(String line : lines) {
				line = line.trim();
				if(line.startsWith(keyword)) {
					pkg = line.substring(keyword.length()+1);
					pkg = StringUtil.replace(pkg, ";", "");
					pkg = StringUtil.replace(pkg, " ", "");
					break;
				}
			}
		}
		return pkg;
	}

	/**
	 * 클래스ID 추출
	 * @param classFile
	 * @return
	 */
	@Override
	public String getClassId(String classFile) throws Exception  {
		String classId = "";
		classId = this.getPackageId(classFile) + "." + FileUtil.getFileName(classFile, false);
		return classId;
	}

	/**
	 * 클래스명 추출
	 * @param classFile
	 * @return
	 */
	@Override
	public String getClassName(String classFile) throws Exception  {
		String className = "";
		String fileExt = FileUtil.getFileExt(classFile);
		String fileConts = FileUtil.readFile(classFile);
		
		if("java".equals(fileExt)) {
			if(fileConts.indexOf("public class") != -1) {
				fileConts = fileConts.substring(0, fileConts.indexOf("public class"));
				className = ParseUtil.getFnNameFromComment(fileConts);
			}
		}
		return className;
	}

	/**
	 * 기능종류(UI:화면/JS:자바스크립트/CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) 추출
	 * @param classFile
	 * @return
	 */
	@Override
	public ClzzKind getClassKind(String classFile) throws Exception  {
		ClzzKind classKind = ClzzKind.OT;
		String fileConts = FileUtil.readFile(classFile);
		fileConts = StringUtil.trimTextForParse(fileConts);
		if(fileConts.indexOf("@Controller") != -1 || fileConts.indexOf("@RestController") != -1) {
			classKind = ClzzKind.CT;
		}else if(fileConts.indexOf("@Service") != -1) {
			classKind = ClzzKind.SV;
		}else if(fileConts.indexOf("@Repository") != -1) {
			classKind = ClzzKind.DA;
		}
		return classKind;
	}
	
	/**
	 * 어노테이션으로 표현된 리소스ID 추출
	 * @param classFile
	 * @return
	 */
	@Override
	public String getResourceId(String classFile) throws Exception {
		String resourceId = "";
		String[] lines = FileUtil.readFileByLines(classFile);
		if(lines != null) {
			String searchLine = "";
			for(String line : lines) {
				line = line.trim();
				line = StringUtil.trimTextForParse(line);
				if(line.indexOf("@Service") != -1 || line.indexOf("@Repository") != -1 || line.indexOf("@Resource") != -1 || line.indexOf("@Component") != -1) {
					searchLine = line;
				}
				if(!StringUtil.isEmpty(searchLine)) {
					resourceId = ParseUtil.getValueFromAnnotationLine(searchLine);
					break;
				}
				if(line.indexOf(" class ") != -1 || line.indexOf(" interface ") != -1) {
					break;
				}
			}
		}
		return resourceId;
	}
	
	/**
	 * 클래스or인터페이스(C:클래스/I:인터페이스) 추출
	 * @param classFile
	 * @return
	 */
	public String getClassOrInterface(String classFile) throws Exception{
		String classOrInterface = "";
		String fileConts = FileUtil.readFile(classFile);
		fileConts = StringUtil.trimTextForParse(fileConts);
		if(fileConts.indexOf(" interface ")>-1) {
			classOrInterface = "I";
		}else {
			classOrInterface = "C";
		}
		return classOrInterface;
	}
	
	/**
	 * 상위인터페이스 클래스ID 추출.(인터페이스를 구현한 클래스의 경우에만 존재)
	 * @param classFile
	 * @return
	 */
	public List<String> getInterfaceIdList(String classFile) throws Exception{
		List<String> interfaceIdList = new ArrayList<String>();
		String interfaceId = "";
		String interfaceClass = "";
		String[] lines = FileUtil.readFileByLines(classFile);
		ArrayList<String> importList = new ArrayList<String>();
		if(lines != null) {
			String importLine = "";
			String searchLine = "";
			for(String line : lines) {
				line = line.trim();
				line = StringUtil.trimTextForParse(line);
				if(line.indexOf("import ") != -1) {
					importLine = line;
					importLine = StringUtil.replace(importLine, "import ", "");
					importLine = StringUtil.replace(importLine, ";", "");
					importList.add(importLine);
				}
				if(line.indexOf(" implements ") != -1) {	
					searchLine = line;
				}
				if(!StringUtil.isEmpty(searchLine)) {
					String[] div = {"{"};
					interfaceClass = StringUtil.nextWord(searchLine, " implements ", div);
//System.out.println( "classFile:" + classFile + ", searchLine:" + searchLine  + ", interfaceClass:" + interfaceClass );	
					break;
				}
			}
		}
		if(!StringUtil.isEmpty(interfaceClass)) {
			String[] interArr = StringUtil.toStrArray(interfaceClass.trim(), ",");
			for(String inter : interArr) {
				interfaceClass = inter.trim();
				if(interfaceClass.indexOf(".")>-1) {
					interfaceId = interfaceClass;
					interfaceIdList.add(interfaceId);
				}else {
					for(String importLine : importList) {
						if(importLine.endsWith("." + interfaceClass)) {
							interfaceId = importLine;
							interfaceIdList.add(interfaceId);
							break;
						}
					}
				}
			}
		}
		return interfaceIdList;
	}
	/**
	 * 상위(부모)클래스ID 추출.
	 * @param classFile
	 */
	@Override
	public String getParentClassId(String classFile) throws Exception {
		String returnParentClassId = "";
		List<String> importList = new ArrayList<String>();
		String[] lines = FileUtil.readFileByLines(classFile);
		boolean isFound = false;
		if(lines != null) {
			String importClass = "";
			for(String line : lines) {
				importClass = "";
				line = line.trim();
				line = StringUtil.trimTextForParse(line);
				if(line.startsWith("import") && line.endsWith(";")) {
					importClass = line;
					importClass = StringUtil.replace(importClass, "import", "");
					importClass = StringUtil.replace(importClass, ";", "");
					importClass = StringUtil.replace(importClass, " ", "");
					importList.add(importClass);
				}
				if(line.indexOf(" extends ")>-1) {
					String[] div = {" ", "{"};
					String parentClassId = StringUtil.nextWord(line, " extends ", div);
					for(String importStr : importList) {
						if( parentClassId.indexOf(".")>-1 ) {
							if(importStr.equals(parentClassId)) {
								returnParentClassId = importStr;
								isFound = true;
								break;
							}
						}else {
							if(importStr.endsWith("."+parentClassId)) {
								returnParentClassId = importStr;
								isFound = true;
								break;
							}
						}
					}
					if( !StringUtil.isEmpty(returnParentClassId) && returnParentClassId.indexOf(".") == -1 ) {
						returnParentClassId = this.getPackageId(classFile) + "." + returnParentClassId;
						isFound = true;
					}
				}
				if(isFound) {
					break;
				}
			}
		}
		return returnParentClassId;
	}

	/**
	 * 인터페이스구현하위클래스ID목록 추출.(인터페이스인 경우에만 존재)
	 * @param selfClzzVo
	 * @param analyzedClassFileList
	 * @return
	 */
	public List<String> getImplClassIdList(ClzzVo selfClzzVo, String[] analyzedClassFileList) throws Exception{	
		List<String> implClassIdList = new ArrayList<String>();
		if( "I".equals(selfClzzVo.getClassOrInterface()) ) {
			ClzzVo otherClzzVo = null;
			for(String packageClassId : analyzedClassFileList) {
				otherClzzVo = ParseUtil.readClassVo(packageClassId, AppAnalyzer.WRITE_PATH + "/class");		
				for(String interfaceId : otherClzzVo.getInterfaceIdList()) {
					if( selfClzzVo.getClassId().equals(interfaceId) ) {
						implClassIdList.add(otherClzzVo.getClassId());
						break;
					}
				}
			}
		}
		return implClassIdList;
	}
	
	/**
	 * 호출알리아스 추출. 리스트<맵>을 반환. 맵항목- Full클래스,알리아스 .(예: FULL_CLASS:aaa.bbb.Clzz2, ALIAS:clzz2)
	 * @param selfClzzVo
	 * @param analyzedClassFileList
	 * @return
	 */
	private List<Map<String, String>> getAllClassAlias(ClzzVo selfClzzVo, String[] analyzedClassFileList) throws Exception  {
		List<Map<String, String>> callClassAliasList = new ArrayList<Map<String, String>>();
		Map<String, String> callClassAlias = new HashMap<String, String>();
		if( !StringUtil.isEmpty(selfClzzVo.getFileName()) && FileUtil.isFileExist(selfClzzVo.getFileName()) ) {
			String fileConts = FileUtil.readFile(selfClzzVo.getFileName());
			fileConts = StringUtil.trimTextForParse(fileConts);
			String[] lines = StringUtil.toStrArray(fileConts, "\n", false, true);

			boolean isAliasExists = false;
			String alias = "";
			String[] div = {" ", ";", "=", " ="};
			String selfPkg = selfClzzVo.getPackageId();
			String selfClassId = selfClzzVo.getClassId();
			selfClassId = selfClassId.substring(selfClassId.lastIndexOf(".")+1);
			
			for(String packageClassId : analyzedClassFileList) {
				
				isAliasExists = false;
				String pkg = packageClassId.substring(0, packageClassId.lastIndexOf("."));
				String classIdWithoutPkg = packageClassId.substring(packageClassId.lastIndexOf(".")+1);
				String beforeLine = "";
				String resourceId = "";

				if( pkg.equals(selfPkg) && classIdWithoutPkg.equals(selfClassId)) {
					continue;
				}
				// [Full클래스 알리아스] 형식으로 선언되어있을 경우
				if( !( fileConts.indexOf( "import " + pkg + ".*;")>-1 || fileConts.indexOf( "import " + packageClassId + ";")>-1 || pkg.equals(selfPkg)  ) && fileConts.indexOf(packageClassId + " ")>-1 ) {
					//getLogger().sysout("[Full클래스 알리아스] 형식으로 선언되어있음 : classFile[" + classFile + "] packageClassId[" + packageClassId + "]");
					for(String line : lines) {
						if (!ParseUtil.isValidAliasLine(line)) { 
							continue;
						}
						if( line.indexOf(packageClassId + " ")>-1 ) {
							alias = StringUtil.nextWord(line, packageClassId, div);
							if(!StringUtil.isEmpty(alias)) {
								isAliasExists = true;
								break;
							}
						}
						beforeLine = line;
					}
					if (isAliasExists) {
						if ( fileConts.indexOf(alias + ".")>-1) {
							resourceId = ParseUtil.getValueFromAnnotationLine(beforeLine);
							callClassAlias = new HashMap<String, String>();

							// packageClassId 가 인터페이스 일 경우 구현클래스ID를 구한다.
							List<String> implClzzNmList = ParseUtil.getImplClassList(packageClassId, resourceId, AppAnalyzer.CLASS_ROOT_PATH);
							if(implClzzNmList != null && implClzzNmList.size() > 0) {
								String classType = implClzzNmList.get(0);
								callClassAlias.put("FULL_CLASS"	, classType);
								callClassAlias.put("ALIAS"		, alias);
								if( !callClassAliasList.contains(callClassAlias) ) {
									callClassAliasList.add(callClassAlias);
								}
							}
						}
					}
				// import 패키지 이후 [클래스ID 알리아스] 형식으로 선언되어있을 경우
				}else if( fileConts.indexOf( "import " + pkg + ".*;")>-1 || fileConts.indexOf( "import " + packageClassId + ";")>-1 || pkg.equals(selfPkg)  ) {
					//getLogger().sysout("import 패키지 이후 [클래스ID 알리아스] 형식으로 선언되어있음 : classFile[" + classFile + "] packageClassId[" + packageClassId + "]");
					for(String line : lines) {
						if (!ParseUtil.isValidAliasLine(line)) { 
							continue;
						}
						if( line.indexOf(packageClassId + " ")>-1 || line.indexOf(classIdWithoutPkg + " ")>-1 ) {
							alias = StringUtil.nextWord(line, classIdWithoutPkg, div);
							if(!StringUtil.isEmpty(alias)) {
								isAliasExists = true;
								break;
							}
						}
						beforeLine = line;
					}
					if (isAliasExists) {
						if (fileConts.indexOf(alias + ".")>-1) {
							resourceId = ParseUtil.getValueFromAnnotationLine(beforeLine);
							callClassAlias = new HashMap<String, String>();
							// packageClassId 가 인터페이스 일 경우 구현클래스ID를 구한다.
							List<String> implClzzNmList = ParseUtil.getImplClassList(packageClassId, resourceId, AppAnalyzer.CLASS_ROOT_PATH);
							if(implClzzNmList != null && implClzzNmList.size() > 0) {
								String classType = implClzzNmList.get(0);
								callClassAlias.put("FULL_CLASS"	, classType);
								callClassAlias.put("ALIAS"		, alias);
								if( !callClassAliasList.contains(callClassAlias) ) {
									callClassAliasList.add(callClassAlias);
								}
							}
						}
					}
				}
			}
			// 부모클래스가 존재할 경우 부모클래스의 호출알리아스도 가져와서 합쳐준다.
	        if(!StringUtil.isEmpty(selfClzzVo.getParentClassId())) {
	        	ClzzVo parentClzzVo = ParseUtil.readClassVo(selfClzzVo.getParentClassId(), AppAnalyzer.WRITE_PATH + "/class");		
	        	callClassAliasList.addAll(this.getAllClassAlias(parentClzzVo, analyzedClassFileList));
	        }
		}
		
		return callClassAliasList;
	}
	
	/**
	 * 호출알리아스 추출. 리스트<맵>을 반환. 맵항목- Full클래스,알리아스 .(예: FULL_CLASS:aaa.bbb.Clzz2, ALIAS:clzz2)
	 * @param selfClzzVo
	 * @param analyzedClassFileList
	 * @return
	 */
	@Override
	public List<Map<String, String>> getCallClassAlias(ClzzVo selfClzzVo, String[] analyzedClassFileList) throws Exception  {
		List<Map<String, String>> callClassAliasList = new ArrayList<Map<String, String>>();
		if( !StringUtil.isEmpty(selfClzzVo.getFileName()) && FileUtil.isFileExist(selfClzzVo.getFileName()) ) {
			List<Map<String, String>> allClassAliasList = this.getAllClassAlias(selfClzzVo, analyzedClassFileList);
			//getLogger().sysout(selfClzzVo.getClassId() + ":: allClassAliasList ===>>> " + allClassAliasList);			
			if( allClassAliasList != null ) {
				String fileConts = FileUtil.readFile(selfClzzVo.getFileName());
				String type = "";
				String alias = "";
				boolean isUsed = false;
				for(Map<String, String> callClassAlias : allClassAliasList) {
					isUsed = false;
					type = callClassAlias.get("FULL_CLASS");
					alias = callClassAlias.get("ALIAS");
	            	if(!StringUtil.isEmpty(alias)) {
	            		if( !SvcAnalyzer.isValidSvcPackage(type) ) {
	            			continue;
	            		}
	            		// 변수명.메소드  or 변수Getter.메소드 의 형태가 존재할 경우 사용된걸로 간주한다.
						if (fileConts.indexOf(alias + ".")>-1 || fileConts.indexOf(ParseUtil.getGetterNmFromField(alias) + ".")>-1) {
							isUsed = true;
						}
						if(isUsed) {
							if( !callClassAliasList.contains(callClassAlias) ) {
								callClassAliasList.add(callClassAlias);
							}
						}
	            	}
				}
			}
		}
		return callClassAliasList;
	}





}

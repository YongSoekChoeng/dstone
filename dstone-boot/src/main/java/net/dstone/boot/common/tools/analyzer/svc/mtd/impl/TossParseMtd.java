package net.dstone.boot.common.tools.analyzer.svc.mtd.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.svc.mtd.ParseMtd;
import net.dstone.boot.common.tools.analyzer.util.ParseUtil;
import net.dstone.boot.common.tools.analyzer.vo.MtdVo;
import net.dstone.boot.common.tools.analyzer.vo.QueryVo;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

public class TossParseMtd extends TextParseMtd implements ParseMtd {

	/**
	 * 파일로부터 메소드ID/메소드명/메소드URL/메소드내용 이 담긴 메소드정보목록 추출
	 * LIST[ MAP<메서드ID(METHOD_ID), 메서드명(METHOD_NAME), 메서드URL(METHOD_URL), 메서드바디(METHOD_BODY)> ]
	 * @param classFile 
	 * @return
	 */
	@Override
	public List<Map<String, String>> getMtdInfoList(String classFile) throws Exception {
		
		List<Map<String, String>> mList = new ArrayList<Map<String, String>>();
		
		CompilationUnit cu = StaticJavaParser.parse(new File(classFile));

    	// classUrl
        String classUrl = "";
        if(cu.findFirst(ClassOrInterfaceDeclaration.class).isPresent()) {
            ClassOrInterfaceDeclaration classOrInterfaceDeclaration = cu.findFirst(ClassOrInterfaceDeclaration.class).get();
            List<AnnotationExpr> annotationList = classOrInterfaceDeclaration.getAnnotations();
            for(AnnotationExpr an : annotationList) {
            	if(an.getNameAsString().endsWith("Mapping")) {
            		/***************************************************
            		<SingleMemberAnnotation/NormalAnnotation 의 차이>
            		- @RequestMapping("/sample/admin.do")       ===>> SingleMemberAnnotation
            		- @RequestMapping(value="/sample/admin.do") ===>> NormalAnnotation
            		***************************************************/
            		if( an.isSingleMemberAnnotationExpr() ) {
            			classUrl = an.asSingleMemberAnnotationExpr().getMemberValue().asStringLiteralExpr().asString();
            		}else if( an.isNormalAnnotationExpr() ) {
            			classUrl = an.asNormalAnnotationExpr().getPairs().get(0).getValue().asStringLiteralExpr().asString();
            		}
            	}
            }
            if(!StringUtil.isEmpty(classUrl)) {
            	if(classUrl.endsWith("/*")) {
            		classUrl = classUrl.substring(0, classUrl.length()-2);
            	}else if(classUrl.endsWith("/")) {
            		classUrl = classUrl.substring(0, classUrl.length()-1);
            	}
            }

            // javaparser로 Comment가 얻어지지 않는 버그가 있음. 따라서  METHOD_NAME 은 Text베이스로 직접 파싱한 것을 사용할 필요가 있음.
        	List<Map<String, String>> methodInfoFromTextList = ParseUtil.getMtdListFromJava(FileUtil.readFile(classFile)); 
        	
            List<MethodDeclaration> methodDeclarationList = cu.findAll(MethodDeclaration.class);
            for(MethodDeclaration methodDec : methodDeclarationList) {
            	Map<String, String> item = new HashMap<String, String>();

            	// CLASS_ID
                String CLASS_ID = classOrInterfaceDeclaration.resolve().getQualifiedName();
            	item.put("CLASS_ID", CLASS_ID);
            	// METHOD_ID
                String METHOD_ID = methodDec.getNameAsString();
            	item.put("METHOD_ID", METHOD_ID);
            	// METHOD_NAME
                String METHOD_NAME = "";
            	if(methodDec.getJavadocComment().isPresent()) {
            		METHOD_NAME = ParseUtil.getFnNameFromComment(methodDec.getJavadocComment().get().asString());
            	}
            	if( StringUtil.isEmpty(METHOD_NAME)) {
            		for(Map<String, String> methodInfoFromText : methodInfoFromTextList) {
            			if( methodInfoFromText.containsKey("METHOD_ID") && METHOD_ID.equals(methodInfoFromText.get("METHOD_ID")) ) {
            				METHOD_NAME = methodInfoFromText.get("METHOD_NAME");
            				break;
            			}
            		}
            	}    
            	item.put("METHOD_NAME", METHOD_NAME);
            	// METHOD_URL
                String METHOD_URL = "";
                List<AnnotationExpr> methodAnnotationList =  methodDec.getAnnotations();
                for(AnnotationExpr an : methodAnnotationList) {
                	if(an.getNameAsString().endsWith("Mapping")) {
                		if( an.isSingleMemberAnnotationExpr() ) {
                			METHOD_URL = an.asSingleMemberAnnotationExpr().getMemberValue().asStringLiteralExpr().asString();
                		}else if( an.isNormalAnnotationExpr() ) {         
                			if (an.asNormalAnnotationExpr().getPairs().get(0).getValue().isStringLiteralExpr()) {
                				METHOD_URL = an.asNormalAnnotationExpr().getPairs().get(0).getValue().asStringLiteralExpr().asString();
                			}else if (an.asNormalAnnotationExpr().getPairs().get(0).getValue().isArrayInitializerExpr()) {
                				List<Node> nodes = an.asNormalAnnotationExpr().getPairs().get(0).getValue().asArrayInitializerExpr().getChildNodes();
                				if(nodes != null) {
                		            for (Node node : nodes) {
                		                if (node instanceof StringLiteralExpr) {
                		                	METHOD_URL = ((StringLiteralExpr) node).getValue();
                		                	break;
                		                }
                		            }
                				}
                			}
                		}
                	}
                }
                if(!StringUtil.isEmpty(METHOD_URL)) {
                	if(!METHOD_URL.startsWith("/")) {
                		METHOD_URL = "/" + METHOD_URL;
                	}
                	METHOD_URL = classUrl + METHOD_URL;
                }
            	item.put("METHOD_URL", METHOD_URL);
            	// METHOD_BODY
            	String METHOD_BODY = "";
            	if( methodDec.getBody() != null && methodDec.getBody().isPresent() ) {
            		METHOD_BODY = StringUtil.trimTextForParse(methodDec.getBody().get().toString());
            	}
        		String[] lines = StringUtil.toStrArray(METHOD_BODY, "\n");
        		METHOD_BODY = "";
        		for(String line : lines) {
        			METHOD_BODY = METHOD_BODY + line.trim() + "\n";
        		}
            	item.put("METHOD_BODY", METHOD_BODY);
            	
            	mList.add(item);
            }
        }
        
		return mList;
	}

	/**
	 * 호출메소드 목록 추출
	 * @param clzzVo
	 * @param analyzedMethodFile
	 * @return
	 */
	@Override
	public List<String> getCallMtdList(String analyzedMethodFile) throws Exception {
		return super.getCallMtdList(analyzedMethodFile);
	}

	/**
	 * 호출테이블 목록 추출
	 * 파일에서 매칭되는 queryKey를 찾아내어 해당 [쿼리정보.테이블목록]을 읽어온 후 [메소드정보.호출테이블]에 저장.
	 * @param analyzedMethodFile
	 * @return
	 */
	@Override
	public List<String> getCallTblList(String analyzedMethodFile) throws Exception {
		List<String> callTblList = new ArrayList<String>();
		// 메소드VO 정보 획득
		String functionId = FileUtil.getFileName(analyzedMethodFile, false);
		MtdVo mtdVo = ParseUtil.readMethodVo(functionId, AppAnalyzer.WRITE_PATH + "/method");

		// 쿼리목록 정보 획득
		String[] queryFileArr = FileUtil.readFileList(AppAnalyzer.WRITE_PATH + "/query", false);
		List<String> queryKeyList = new ArrayList<String>();
		if(queryFileArr != null) {
			for(String item : queryFileArr) {
				queryKeyList.add(item);
			}
		}

		QueryVo queryVo = null;
		String mtdBody = "";
		String keyword = "";
		
		String queryKeyNameSpace = "";
		String queryKeyQueryId = "";
		String getterName = "";
		boolean isUsed = false;
		
		if( !StringUtil.isEmpty(mtdVo.getMethodBody()) ) {
			mtdBody = mtdVo.getMethodBody();
			String[] lines = StringUtil.toStrArray(mtdBody, "\n");
			for(String line : lines) {
				keyword = "";
				for(String queryKey : queryKeyList) {
					queryKeyNameSpace = "";
					queryKeyQueryId = "";
					getterName = "";
					isUsed = false;
					/********************************************
					아래와 같이 queryKey => keyword 로 치환하는 작업.
					queryKey 	: Board_deleteBoard
					keyword 	: "Board.deleteBoard" 혹은 getBoardmapper() + "deleteBoard"
					********************************************/
					// CASE-1. 정석적인 경우. 예)getSqlSession().insert("Board.deleteBoard", boardVO).
					keyword = queryKey;
					if( keyword.indexOf("_")>-1 ) {
						keyword = keyword.substring(0, keyword.lastIndexOf("_")) + "." + keyword.substring(keyword.lastIndexOf("_")+1);
					}
					keyword = "\"" + keyword + "\"";
					if( line.indexOf(keyword) > -1 ) {
						isUsed = true;
					}
					// CASE-2. 메소드로 감싼 경우. 예)getSqlSession().insert(getBoardmapper() + "deleteBoard", boardVO). getBoardmapper()는 "Board."를 반환.
					if( !isUsed && queryKey.indexOf("_")>-1  && ( line.indexOf("getSqlSession().")>-1 || line.indexOf("sqlSession.")>-1 ) ) {
						keyword = queryKey;
						queryKeyNameSpace = StringUtil.toStrArray(keyword, "_")[0]; // Board
						getterName = this.getGetterMethodNameByNameSpace(queryKeyNameSpace)+"()"; // getBoardmapper()
						queryKeyQueryId = StringUtil.toStrArray(keyword, "_")[1]; // deleteBoard
						queryKeyQueryId = "\"" + queryKeyQueryId + "\"";	// "deleteBoard"
						
						keyword = getterName + "+" + queryKeyQueryId; // getBoardmapper()+"deleteBoard"
						keyword = keyword.toUpperCase(); // GETBOARDMAPPER()+"DELETEBOARD"
						keyword = StringUtil.replace(StringUtil.trimTextForParse(keyword), " ", "");
						if( StringUtil.replace(StringUtil.trimTextForParse(line), " ", "").toUpperCase().indexOf(keyword) > -1 ) {
							isUsed = true;
						}
					}
					if(isUsed) {
						queryVo = ParseUtil.readQueryVo(queryKey, AppAnalyzer.WRITE_PATH + "/query");
						if(queryVo != null && queryVo.getCallTblList() != null && queryVo.getCallTblList().size() > 0) {
							String tblKey = "";
							for(String callTbl : queryVo.getCallTblList()) {
								/********************************************
								메소드VO.호출테이블 항목을 테이블명 + "!" + 쿼리종류 로 저장.
								예)SAMPLE_MEMBER!UPDATE
								********************************************/
								tblKey = callTbl + "!" + queryVo.getQueryKind();
								if( !callTblList.contains(tblKey ) ) {
									callTblList.add(tblKey);
								}
							}
						}
						break;
					}
				}
			}
		}

		return callTblList;
	}
	
	private static Map<String, String> METHOD_NAME_VALUE_MAP = new HashMap<String, String>();
	private static Map<String, String> METHOD_VALUE_NAME_MAP = new HashMap<String, String>();
	static {
		try {
			if( (METHOD_NAME_VALUE_MAP.size() == 0 || METHOD_VALUE_NAME_MAP.size() == 0) && FileUtil.isFileExist( AppAnalyzer.CLASS_ROOT_PATH + "/kr/co/gnx/base/BaseDAO.java" ) ) {
				Map<String, String> MEMBER_VALUE_MAP = new HashMap<String, String>();
				com.github.javaparser.ast.CompilationUnit cu = com.github.javaparser.StaticJavaParser.parse(new java.io.File( AppAnalyzer.CLASS_ROOT_PATH + "/kr/co/gnx/base/BaseDAO.java" ));
				java.util.List<com.github.javaparser.ast.body.FieldDeclaration> fieldDeclarationList = cu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class);
				String fieldName = "";
				String fieldValue = "";
				for(com.github.javaparser.ast.body.FieldDeclaration fieldDec : fieldDeclarationList) {
					fieldName = "";
					fieldValue = "";
					com.github.javaparser.ast.body.VariableDeclarator varDec = fieldDec.getVariables().get(0);
					fieldName = varDec.getNameAsString();
					if( fieldDec.isPrivate() && fieldDec.isStatic() && fieldDec.isFinal() && fieldName.toLowerCase().endsWith("mapper") ){
						java.util.List<com.github.javaparser.ast.Node> nodeList = varDec.getChildNodes();
						if(nodeList.size()>2) {
							fieldValue = nodeList.get(2).toString();
							fieldValue = net.dstone.common.utils.StringUtil.replace(fieldValue, "\"", "");
							fieldValue = net.dstone.common.utils.StringUtil.replace(fieldValue, ".", "");
							MEMBER_VALUE_MAP.put(fieldName, fieldValue);
						}
					}
				}
				
				java.util.List<com.github.javaparser.ast.body.MethodDeclaration> methodDeclarationList = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class);
				String methodName = "";
				String methodReturnValue = "";
				String methodBody = "";
				String[] div = {";"};
				for(com.github.javaparser.ast.body.MethodDeclaration methodDec : methodDeclarationList) {
					methodName = methodDec.getNameAsString();
					methodReturnValue = "";
					methodBody = methodDec.getBody().get().toString();
					if( methodDec.isStatic() && methodName.toLowerCase().startsWith("get") && methodName.toLowerCase().endsWith("mapper") && "String".equals(methodDec.getTypeAsString()) ){
						methodReturnValue = net.dstone.common.utils.StringUtil.nextWord(methodBody, "return ", div);
						METHOD_NAME_VALUE_MAP.put(methodName, MEMBER_VALUE_MAP.get(methodReturnValue));
						METHOD_VALUE_NAME_MAP.put(MEMBER_VALUE_MAP.get(methodReturnValue), methodName);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String getGetterMethodNameByNameSpace(String nameSpace) throws Exception {
		String getterMethodName = "";
		if( nameSpace.endsWith("_") ) {
			getterMethodName = nameSpace;
			getterMethodName = net.dstone.common.utils.StringUtil.replace(getterMethodName, "_", "");
			getterMethodName = net.dstone.common.utils.StringUtil.replace(getterMethodName, ".", "");
		}
		if(METHOD_VALUE_NAME_MAP.containsKey(nameSpace)) {
			getterMethodName = METHOD_VALUE_NAME_MAP.get(nameSpace);
		}
		return getterMethodName;
	}

}

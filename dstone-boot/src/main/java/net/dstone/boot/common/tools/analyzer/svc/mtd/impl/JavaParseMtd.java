package net.dstone.boot.common.tools.analyzer.svc.mtd.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.svc.SvcAnalyzer;
import net.dstone.boot.common.tools.analyzer.svc.mtd.ParseMtd;
import net.dstone.boot.common.tools.analyzer.util.ParseUtil;
import net.dstone.boot.common.tools.analyzer.vo.ClzzVo;
import net.dstone.boot.common.tools.analyzer.vo.MtdVo;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

public class JavaParseMtd extends TextParseMtd implements ParseMtd {

	/**
	 * 파일로부터 메소드ID/메소드명/메소드URL/메소드내용 이 담긴 메소드정보목록 추출
	 * LIST[ MAP<메서드ID(METHOD_ID), 메서드명(METHOD_NAME), 메서드URL(METHOD_URL), 메서드바디(METHOD_BODY)> ]
	 * @param classFile
	 * @return
	 */ 
	@Override
	public List<Map<String, String>> getMtdInfoList(String classFile) throws Exception {
		
		List<Map<String, String>> mList = new ArrayList<Map<String, String>>();
		
		CompilationUnit cu = ParseUtil.getCompilationUnit(classFile);
		
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
            	
            	// FUNCTION_ID
                String FUNCTION_ID = ParseUtil.getReMethodDec(methodDec).getQualifiedSignature();
            	item.put("FUNCTION_ID", FUNCTION_ID);
            	
            	// CLASS_ID
                String CLASS_ID = ParseUtil.getReClassDec(classOrInterfaceDeclaration).getQualifiedName();
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
                		/***************************************************
                		<SingleMemberAnnotation/NormalAnnotation 의 차이>
                		- @RequestMapping("/sample/admin.do")       ===>> SingleMemberAnnotation
                		- @RequestMapping(value="/sample/admin.do") ===>> NormalAnnotation
                		***************************************************/
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
        		StringBuffer METHOD_BODY_BUFF = new StringBuffer();
        		for(String line : lines) {
        			METHOD_BODY_BUFF.append(" ").append(line.trim()).append("\n");
        		}
            	item.put("METHOD_BODY", METHOD_BODY_BUFF.toString());
            	
            	mList.add(item);
            }
        }
        
		return mList;
	}

	/**
	 * 메소드내의 호출메소드 목록 추출
	 * @param clzzVo
	 * @param analyzedMethodFile
	 * @return
	 */
	@Override
	public List<String> getCallMtdList(String analyzedMethodFile) throws Exception {
		
		String debugStr = "kr.co.gnx.contract.contract.ContractService.deleteContract";
		
		ArrayList<String> mtdCallList = new ArrayList<String>(); 
		
		/*** 메소드VO 정보 획득  ***/
		String functionId = FileUtil.getFileName(analyzedMethodFile, false);
		MtdVo mtdVo = ParseUtil.readMethodVo(functionId, AppAnalyzer.WRITE_PATH + "/method");
		
		if( !StringUtil.isEmpty(debugStr) && analyzedMethodFile.indexOf(debugStr) > -1 ) {
			debug("내부호출 분석대상:"+analyzedMethodFile + ", functionId:" + functionId );
		}


		/*** 클래스VO 정보 획득  ***/
		ClzzVo clzzVo = ParseUtil.readClassVo(mtdVo.getClassId(), AppAnalyzer.WRITE_PATH + "/class");
		List<Map<String, String>> callClassAliasList = clzzVo.getCallClassAlias();

		/*** 클래스멤버 목록조회 ***/
		HashMap<String, ArrayList<String>> clzzMemberMap = new HashMap<String, ArrayList<String>>(); 
		if( callClassAliasList != null ) {
			for(Map<String, String> classAliasMap : callClassAliasList) {
				Iterator<String> classIter = classAliasMap.keySet().iterator();
				while(classIter.hasNext()) {
					String classIterKey = classIter.next();
					String aliasVal = classAliasMap.get(classIterKey);
					if( !clzzMemberMap.containsKey(aliasVal) ) {
						clzzMemberMap.put(aliasVal, new ArrayList<String>());
					}
					clzzMemberMap.get(aliasVal).add(classIterKey);
				}
			}
		}
		
		/*** 메소드AST 정보 획득  ***/
		MethodDeclaration mtdDec = ParseUtil.getMethodDec(AppAnalyzer.CLASS_ROOT_PATH, mtdVo.getFunctionId());

		if(mtdDec != null) {

			/*** 메서드내의 호출메서드 목록조회 ***/
			List<MethodCallExpr> meCallList = mtdDec.findAll(MethodCallExpr.class);
			for (MethodCallExpr meCall : meCallList) {
				
				ResolvedMethodDeclaration mtdResolved = ParseUtil.getReMethodDec(meCall); 
				ClassOrInterfaceDeclaration valClzz = null; 
				
				// 호출메서드의 부모(클래스/인터페이스)객체 조회
				String methodQualifiedSignature = mtdResolved.getQualifiedSignature(); 
				String clzzQualifiedName = "";
				String methodSignature = "";
				
				clzzQualifiedName = methodQualifiedSignature.substring(0, methodQualifiedSignature.indexOf("("));
				clzzQualifiedName = clzzQualifiedName.substring(0, clzzQualifiedName.lastIndexOf(".")); 
				methodSignature = StringUtil.replace(methodQualifiedSignature, clzzQualifiedName+".", "");
				
				valClzz = ParseUtil.getClassDec(AppAnalyzer.CLASS_ROOT_PATH, clzzQualifiedName);
				
				if( !StringUtil.isEmpty(debugStr) && analyzedMethodFile.indexOf(debugStr) > -1 ) {
					debug("\t\t" + "호출메서드:" + methodQualifiedSignature );
				}
				
				if( valClzz != null) {
					String callMethodQualifiedSignature = "";
					
					/*** 호출메서드의 부모(클래스/인터페이스)객체가 클래스 일 경우 (MethodCallExpr 자체적으로 구현 클래스.메서드 등 찾을 수 있음) ***/
					if( !valClzz.isInterface()) {

						/*** 메서드가 일반메서드 일 경우 ***/
						if( !mtdResolved.isAbstract() ) {
							callMethodQualifiedSignature = valClzz.getFullyQualifiedName().get() + "." + methodSignature;
							if( SvcAnalyzer.isValidSvcPackage(callMethodQualifiedSignature) ) {
								if( !mtdCallList.contains(callMethodQualifiedSignature)) {
									//debug("\t" + "Class-Type 메서드호출:"+ callMethodQualifiedSignature );
									mtdCallList.add(callMethodQualifiedSignature); 
								}
							}
						/*** 메서드가 추상메서드 일 경우 ***/
						}else {
							/*************************************************************************
							 * 메서드 내에서 해당 추상메서드클래스의  (자식클래스)객체생성을 확인하여 확인된 자식클래스의 메서드를 추가.
							 * 예)
							 * AbstractClass abstractClass = null;
							 * if(param==1){
							 * 		abstractClass = new ChildClass1();
							 * }else{
							 * 		abstractClass = new ChildClass2();
							 * }
							 * abstractClass.abstractMethod();
							*************************************************************************/
							List<ObjectCreationExpr> ocList = mtdDec.findAll(ObjectCreationExpr.class);
							for (ObjectCreationExpr oc : ocList) {
								ClassOrInterfaceDeclaration ocClzz = ParseUtil.getClassDec(AppAnalyzer.CLASS_ROOT_PATH, oc.calculateResolvedType().describe());
								if(ocClzz.getExtendedTypes().size() > 0) {
									if( valClzz.resolve().getQualifiedName().equals(ocClzz.getExtendedTypes(0).resolve().describe()) ) {
										callMethodQualifiedSignature = ParseUtil.getReClassDec(ocClzz).getQualifiedName() + "." + methodSignature;
										if( !SvcAnalyzer.isValidSvcPackage(callMethodQualifiedSignature) ) {
											continue;
										}
										if( !mtdCallList.contains(callMethodQualifiedSignature)) {
											//debug("\t\t" + "Class-Type 추상메서드호출:"+ callMethodQualifiedSignature );
											mtdCallList.add(callMethodQualifiedSignature); 
										}
									}
								}
							}
						}
					/*** 호출메서드의 부모(클래스/인터페이스)객체가 인터페이스 일 경우 ***/
					}else {
						List<String> implClassList = ParseUtil.getImplClassList(clzzQualifiedName, "", AppAnalyzer.INCLUDE_PACKAGE_ROOT);
						for (String implClass : implClassList) {
							callMethodQualifiedSignature = implClass + "." + methodSignature;
							if( !SvcAnalyzer.isValidSvcPackage(callMethodQualifiedSignature) ) {
								continue;
							}
							if( !mtdCallList.contains(callMethodQualifiedSignature)) {
								//debug("\t\t" + "Interface-Type 메서드호출:"+ callMethodQualifiedSignature );
								mtdCallList.add(callMethodQualifiedSignature); 
							}
						}
					}
				}
			}
		}
		return mtdCallList;
	}

	/**
	 * 호출테이블 목록 추출
	 * 파일에서 매칭되는 queryKey를 찾아내어 해당 [쿼리정보.테이블목록]을 읽어온 후 [메소드정보.호출테이블]에 저장.
	 * @param analyzedMethodFile
	 * @return
	 */
	@Override
	public List<String> getCallTblList(String analyzedMethodFile) throws Exception {
		return super.getCallTblList(analyzedMethodFile);
	}

}

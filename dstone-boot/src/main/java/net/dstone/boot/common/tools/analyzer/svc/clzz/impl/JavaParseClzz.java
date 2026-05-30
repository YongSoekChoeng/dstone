package net.dstone.boot.common.tools.analyzer.svc.clzz.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;

import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.consts.ClzzKind;
import net.dstone.boot.common.tools.analyzer.svc.SvcAnalyzer;
import net.dstone.boot.common.tools.analyzer.svc.clzz.ParseClzz;
import net.dstone.boot.common.tools.analyzer.util.ParseUtil;
import net.dstone.boot.common.tools.analyzer.vo.ClzzVo;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

public class JavaParseClzz extends TextParseClzz implements ParseClzz {
	
	/**
	 * 패키지ID 추출
	 * @param classFile 클래스파일 
	 * @return
	 */
	@Override
	public String getPackageId(String classFile) throws Exception {
		String pkg = "";
		CompilationUnit clzzCU = ParseUtil.getCompilationUnit(classFile);
        // packageId
		pkg = clzzCU.getPackageDeclaration().get().getNameAsString();
		return pkg;
	}

	/**
	 * 클래스ID 추출
	 * @param classFile
	 * @return
	 */
	@Override
	public String getClassId(String classFile) throws Exception {
		String classId = "";
		CompilationUnit clzzCU = ParseUtil.getCompilationUnit(classFile);
		classId = clzzCU.getPackageDeclaration().get().getNameAsString() + "." + clzzCU.getType(0).getNameAsString();
		return classId;
	}

	/**
	 * 클래스명 추출
	 * @param classFile
	 * @return
	 */
	@Override
	public String getClassName(String classFile) throws Exception {
		String className = "";
		CompilationUnit clzzCU = ParseUtil.getCompilationUnit(classFile);
        if(clzzCU.getType(0).getJavadocComment().isPresent()) {
        	className = ParseUtil.getFnNameFromComment(clzzCU.getType(0).getJavadocComment().get().asString());
        }
        if(StringUtil.isEmpty(className)) {
        	className = super.getClassName(classFile);
        }
		return className;
	}

	/**
	 * 기능종류(UI:화면/JS:자바스크립트/CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) 추출
	 * @param classFile
	 * @return
	 */
	@Override
	public ClzzKind getClassKind(String classFile) throws Exception {
		ClzzKind classKind = ClzzKind.OT;
		CompilationUnit clzzCU = ParseUtil.getCompilationUnit(classFile);
        String fileExt = FileUtil.getFileExt(classFile);
        String annotation = "";
        
		if("java".equals(fileExt)) {
			List<AnnotationExpr> annotationDeclarationList = clzzCU.getType(0).getAnnotations();
            if(!annotationDeclarationList.isEmpty()) {
            	for(AnnotationExpr item : annotationDeclarationList) {
            		annotation = item.getNameAsString();
            		if("Controller".equals(annotation) || "RestController".equals(annotation)) {
            			classKind = ClzzKind.CT;
            		}else if("Service".equals(annotation)) {
            			classKind = ClzzKind.SV;
            		}else if("Repository".equals(annotation)) {
            			classKind = ClzzKind.DA;
            		}
            		if(!classKind.equals(ClzzKind.OT)) {
            			break;
            		}
            	}
            }
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
		String annotation = "";
        String resourceId = "";
		CompilationUnit clzzCU = ParseUtil.getCompilationUnit(classFile);
        String fileExt = FileUtil.getFileExt(classFile);
		if("java".equals(fileExt)) {
			List<AnnotationExpr> annotationDeclarationList = clzzCU.getType(0).getAnnotations();
            if(!annotationDeclarationList.isEmpty()) {
            	for(AnnotationExpr item : annotationDeclarationList) {
            		annotation = item.getNameAsString();
            		if("Controller".equals(annotation) || "RestController".equals(annotation) || "Service".equals(annotation) || "Repository".equals(annotation)) {
            			if( item.findFirst(LiteralStringValueExpr.class).isPresent() ) {
            				resourceId = item.findFirst(LiteralStringValueExpr.class).get().getValue();
            			}
            		}
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
	@Override
	public String getClassOrInterface(String classFile) throws Exception{
		String classOrInterface = "";
		CompilationUnit clzzCU = ParseUtil.getCompilationUnit(classFile);
		try {
			if( clzzCU.findFirst(ClassOrInterfaceDeclaration.class).isPresent() ) {
				if (clzzCU.findFirst(ClassOrInterfaceDeclaration.class).get().isInterface()) {
					classOrInterface = "I";
				} else {
					classOrInterface = "C";
				}
			}
		} catch (Exception e) {
			throw e;
		}
		return classOrInterface;
	}

	/**
	 * 상위인터페이스 클래스ID 추출.(인터페이스를 구현한 클래스의 경우에만 존재)
	 * @param classFile
	 * @return
	 */
	@Override
	public List<String> getInterfaceIdList(String classFile) throws Exception{
		List<String> interfaceIdList = new ArrayList<String>();
		CompilationUnit clzzCU = ParseUtil.getCompilationUnit(classFile);
		if( clzzCU.findFirst(ClassOrInterfaceDeclaration.class).isPresent() ) {
			ClassOrInterfaceDeclaration clzzDec = clzzCU.findFirst(ClassOrInterfaceDeclaration.class).get();
			NodeList<ClassOrInterfaceType> interfaceList = clzzDec.getImplementedTypes();
			if( interfaceList != null && interfaceList.isNonEmpty() ) {
				for(ClassOrInterfaceType inter : interfaceList) {
	        		if( !SvcAnalyzer.isValidSvcPackage(inter.resolve().describe()) ) {
	        			continue;
	        		}
	        		interfaceIdList.add(inter.resolve().describe());
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
		String parentClassId = "";
		CompilationUnit clzzCU = ParseUtil.getCompilationUnit(classFile);
		if( clzzCU.findFirst(ClassOrInterfaceDeclaration.class).isPresent() ) {
			ClassOrInterfaceDeclaration clzzDec = clzzCU.findFirst(ClassOrInterfaceDeclaration.class).get();
			NodeList<ClassOrInterfaceType> parentList = clzzDec.getExtendedTypes();
			if( parentList != null && parentList.isNonEmpty() ) {
				ClassOrInterfaceType parent = parentList.get(0);
				parentClassId = parent.resolve().describe();
	    		if( !SvcAnalyzer.isValidSvcPackage(parentClassId) ) {
	    			parentClassId = "";
	    		}
			}
		}

		return parentClassId;
	}

	/**
	 * 인터페이스구현하위클래스ID목록 추출.(인터페이스인 경우에만 존재)
	 * @param selfClzzVo
	 * @param analyzedClassFileList (JavaParseClzz 에서는 사용하지 않음.)
	 * @return
	 */
	public List<String> getImplClassIdList(ClzzVo selfClzzVo, String[] analyzedClassFileList) throws Exception{
		List<String> implClassIdList = new ArrayList<String>();
		if( "I".equals(selfClzzVo.getClassOrInterface()) ) {
			implClassIdList = ParseUtil.getImplClassList(selfClzzVo.getClassId(), "", AppAnalyzer.INCLUDE_PACKAGE_ROOT);
		}
		return implClassIdList;
	}
	
	/**
	 * 호출알리아스 추출. 리스트<맵>을 반환. 맵항목- Full클래스,알리아스 . 
	 * 만일 Full클래스 가 인터페이스 일 경우 해당 인터페이스를 구현한 클래스 목록으로 반환.
	 * 반환리스트 형태
	 * {
	 *    [<FULL_CLASS:aaa.bbb.Clzz1, 		ALIAS:alias1>]
	 *  , [<FULL_CLASS:aaa.bbb.Clzz2, 		ALIAS:alias2>]
	 *  , [<FULL_CLASS:aaa.bbb.Clzz3, 		ALIAS:alias3>]
	 *  , [<FULL_CLASS:aaa.bbb.Clzz4Impl1, 	ALIAS:alias4>]
	 *  , [<FULL_CLASS:aaa.bbb.Clzz4Impl2, 	ALIAS:alias4>]
	 * }
	 * @param selfClzzVo
	 * @param analyzedClassFileList-JavaParseClzz에서는 사용하지 않는 파라메터.
	 * @return
	 */
	@Override
	public List<Map<String, String>> getCallClassAlias(ClzzVo selfClzzVo, String[] analyzedClassFileList) throws Exception {
		
		boolean checkDetailYn = false;
		
		List<Map<String, String>> callClassAliasList = new ArrayList<Map<String, String>>();
		Map<String, String> callClassAliasMap = new HashMap<String, String>();
		if( !StringUtil.isEmpty(selfClzzVo.getFileName()) && FileUtil.isFileExist(selfClzzVo.getFileName()) ) {
			CompilationUnit clzzCU = ParseUtil.getCompilationUnit(selfClzzVo.getFileName());
			if( clzzCU.findFirst(ClassOrInterfaceDeclaration.class).isPresent() ) {

				ClassOrInterfaceDeclaration clzzDec = clzzCU.findFirst(ClassOrInterfaceDeclaration.class).get();
				
				List<ResolvedFieldDeclaration> clzzFieldList = clzzDec.resolve().getAllFields();
				String classAlias = "";
				String classType = "";
				
				for (ResolvedFieldDeclaration clzzField: clzzFieldList) {
					ClassOrInterfaceDeclaration clzzFieldClzz = ParseUtil.getClassDec(AppAnalyzer.CLASS_ROOT_PATH, clzzField.getType().describe());
					if( clzzFieldClzz != null) {

						classType = clzzFieldClzz.resolve().getQualifiedName();	// Full클래스
						classAlias = clzzField.getName();						// 알리아스
						
	            		if( !SvcAnalyzer.isValidSvcPackage(classType) ) {
	            			continue;
	            		}
	            		
						// 멤버가 클래스 일 경우
						if( !clzzFieldClzz.isInterface() ) {
							callClassAliasMap = new HashMap<String, String>();
							callClassAliasMap.put("FULL_CLASS"	, classType);
							callClassAliasMap.put("ALIAS"		, classAlias);
							if( !callClassAliasList.contains(callClassAliasMap) ) {
								if(checkDetailYn) {
									debug("\t" + "1. 멤버가 클래스 일 경우 ==>>" + callClassAliasMap);
								}
								callClassAliasList.add(callClassAliasMap);
							}
						// 멤버가 인터페이스 일 경우	
						}else {
							String annoVal = "";
							// Annotation-Autowired (구현체는 클래스 내에서  하나만 존재.)
							if( clzzField.toAst(FieldDeclaration.class).get().getAnnotationByName("Autowired").isPresent() ) {
								// 인터페이스를 구현한 클래스목록 추출
								List<String> implClzzNmList = ParseUtil.getImplClassList(classType, "", AppAnalyzer.INCLUDE_PACKAGE_ROOT);
								if(implClzzNmList != null && implClzzNmList.size() > 0) {
									classType = implClzzNmList.get(0);
									callClassAliasMap = new HashMap<String, String>();
									callClassAliasMap.put("FULL_CLASS"	, classType);
									callClassAliasMap.put("ALIAS"		, classAlias);
									if( !callClassAliasList.contains(callClassAliasMap) ) {
										if(checkDetailYn) {
											debug("\t" + "2-1. 멤버가 인터페이스 일 경우(Autowired) ==>>" + callClassAliasMap);
										}
										callClassAliasList.add(callClassAliasMap);
									}
								}
							// Annotation-Qualifier (구현체는 클래스 내에서  여러개 존재 가능. @Qualifier("TestDao21") 혹은  @Qualifier(value ="TestDao22") 로 표현)
							}else if( clzzField.toAst(FieldDeclaration.class).get().getAnnotationByName("Qualifier").isPresent() ) {
								AnnotationExpr annoExpr = clzzField.toAst(FieldDeclaration.class).get().getAnnotationByName("Qualifier").get();
								if(  annoExpr.findFirst(LiteralStringValueExpr.class).isPresent() ) {
									LiteralStringValueExpr annoStrVal = annoExpr.findFirst(LiteralStringValueExpr.class).get();
									annoVal = annoStrVal.getValue();
									callClassAliasMap = new HashMap<String, String>();
									for(String cType : ParseUtil.findImplClassList(classType, annoVal)) {
										classType = cType;
										callClassAliasMap.put("FULL_CLASS"	, classType);
										callClassAliasMap.put("ALIAS"		, classAlias);
										if( !callClassAliasList.contains(callClassAliasMap) ) {
											if(checkDetailYn) {
												debug("\t" + "2-2. 멤버가 인터페이스 일 경우(Qualifier) ==>>" + callClassAliasMap);
											}
											callClassAliasList.add(callClassAliasMap);
										}
									}

								}
							// Annotation-Resource (구현체는 클래스 내에서  여러개 존재 가능. @Qualifier("Resource") 혹은  @Qualifier(name ="Resource") 로 표현)
							}else if( clzzField.toAst(FieldDeclaration.class).get().getAnnotationByName("Resource").isPresent() ) {
								AnnotationExpr annoExpr = clzzField.toAst(FieldDeclaration.class).get().getAnnotationByName("Resource").get();
								if(  annoExpr.findFirst(LiteralStringValueExpr.class).isPresent() ) {
									LiteralStringValueExpr annoStrVal = annoExpr.findFirst(LiteralStringValueExpr.class).get();
									annoVal = annoStrVal.getValue();
									callClassAliasMap = new HashMap<String, String>();
									for(String cType : ParseUtil.findImplClassList(classType, annoVal)) {
										classType = cType;
										callClassAliasMap.put("FULL_CLASS"	, classType);
										callClassAliasMap.put("ALIAS"		, classAlias);
										if( !callClassAliasList.contains(callClassAliasMap) ) {
											if(checkDetailYn) {
												debug("\t" + "2-3. 멤버가 인터페이스 일 경우(Resource) ==>>" + callClassAliasMap);
											}
											callClassAliasList.add(callClassAliasMap);
										}
									}
								}
							// 그외 나머지일 경우(Annotation이 아니고 생성자를 직접 사용할 경우)
							}else {
								List<String> implClzzNmList = ParseUtil.getImplClassList(classType, "", AppAnalyzer.INCLUDE_PACKAGE_ROOT);
								if( implClzzNmList != null && !implClzzNmList.isEmpty() ) {
									for (String implClzzNm: implClzzNmList) {
										classType = implClzzNm;
										callClassAliasMap = new HashMap<String, String>();
										callClassAliasMap.put("FULL_CLASS"	, classType);
										callClassAliasMap.put("ALIAS"		, classAlias);
										if( !callClassAliasList.contains(callClassAliasMap) ) {
											if(checkDetailYn) {
												debug("\t" + "3. 인터페이스를 구현한 클래스목록 일 경우 ==>>" + callClassAliasMap);
											}
											callClassAliasList.add(callClassAliasMap);
										}								
									}
								}
							}
						}
					}
				}
			}
		}
		return callClassAliasList;
	}
    
}

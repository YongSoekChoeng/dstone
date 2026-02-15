package net.dstone.boot.common.tools.analyzer.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.consts.ClzzKind;
import net.dstone.boot.common.tools.analyzer.vo.ClzzVo;
import net.dstone.boot.common.tools.analyzer.vo.MtdVo; 
import net.dstone.boot.common.tools.analyzer.vo.QueryVo;
import net.dstone.boot.common.tools.analyzer.vo.UiVo;
import net.dstone.common.utils.DataSet;
import net.dstone.common.utils.DbUtil;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.SqlUtil;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.XmlUtil;

public class ParseUtil {

	static LogUtil logger = new LogUtil();
	
	static List<String> MANNUAL_TABLE_LIST = new ArrayList<String>();
	static List<Map<String, String>> MANNUAL_TABLE_MAP_LIST = new ArrayList<Map<String, String>>();
	static Map<String, Map<String, String>> MANNUAL_TABLE_LIST_MAP = new HashMap<String, Map<String, String>>();
	
	static Map<String, ClassOrInterfaceDeclaration> PARSE_CLASS_DECL_MAP = new HashMap<String, ClassOrInterfaceDeclaration>();
	static Map<ClassOrInterfaceDeclaration, ResolvedReferenceTypeDeclaration> RE_PARSE_CLASS_DECL_MAP = new HashMap<ClassOrInterfaceDeclaration, ResolvedReferenceTypeDeclaration>();
	 
	static Map<String, MethodDeclaration> PARSE_METHOD_DECL_MAP = new HashMap<String, MethodDeclaration>();
	static Map<Object, ResolvedMethodDeclaration> RE_PARSE_METHOD_DECL_MAP = new HashMap<Object, ResolvedMethodDeclaration>();
	
	public static JavaSymbolSolver getJavaSymbolSolver(){
		//logger.debug("net.dstone.boot.common.tools.analyzer.util.ParseUtil.getJavaSymbolSolver() has been called !!!");
		JavaSymbolSolver javaSymbolSolver = null;
		boolean showClassPathYn = false;
    	try {
    		CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
    		
    		// 1. JDK
    		String jdkHome = "";
    		if( AppAnalyzer.CONF != null &&  AppAnalyzer.CONF.getNode("APP_JDK_HOME") != null && !StringUtil.isEmpty(AppAnalyzer.CONF.getNode("APP_JDK_HOME").getTextContent()) ) {
    			jdkHome = AppAnalyzer.CONF.getNode("APP_JDK_HOME").getTextContent();
    		}else {
    			// [ java.home ]:C:\DEV\JDK\1.8\jre
    			jdkHome = StringUtil.replace(System.getProperty("java.home"), "\\jre", "");
    		}
    		if(!StringUtil.isEmpty(jdkHome)) {
    			jdkHome = StringUtil.replace(jdkHome, "\\", "/");
    			combinedTypeSolver.add(new JarTypeSolver(new File( jdkHome + "/jre/lib/rt.jar")));
    			if(showClassPathYn) {
    				logger.debug("combinedTypeSolver.addJarTypeSolver("+ jdkHome + "/jre/lib/rt.jar" +")");
    			}
    		}
    		
    		// 2. APP CLASS PATH
    		String classPathStr = "";
    		if( AppAnalyzer.CONF != null &&  AppAnalyzer.CONF.getNode("APP_CLASSPATH") != null && !StringUtil.isEmpty(AppAnalyzer.CONF.getNode("APP_CLASSPATH").getTextContent()) ) {
    			classPathStr = AppAnalyzer.CONF.getNode("APP_CLASSPATH").getTextContent();
    		}
    		if(!StringUtil.isEmpty(classPathStr)) {
    			classPathStr = StringUtil.replace(classPathStr, "\n", "");
    			classPathStr = StringUtil.replace(classPathStr, "\t", "");
    			classPathStr = StringUtil.replace(classPathStr, " ", "");
    			String[] classPathArr = StringUtil.toStrArray(classPathStr, ";");
    			for(String classPath : classPathArr) {
    				combinedTypeSolver.add(new JarTypeSolver(new File(classPath)));
    				if(showClassPathYn) {
    					logger.debug("combinedTypeSolver.addJarTypeSolver("+ classPath +")");
    				}
    			}
    		}
    		
    		// 3. APP CLASS ROOT
    		if( AppAnalyzer.CLASS_ROOT_PATH != null ) {
    			combinedTypeSolver.add(new JavaParserTypeSolver(new File(AppAnalyzer.CLASS_ROOT_PATH)));
    			if(showClassPathYn) {
    				logger.debug("combinedTypeSolver.addJavaParserTypeSolver("+ AppAnalyzer.CLASS_ROOT_PATH +")");
    			}
    		}

    		// 4. Reflection
    		combinedTypeSolver.add(new ReflectionTypeSolver());
    		if(showClassPathYn) {
    			logger.debug("combinedTypeSolver.addReflectionTypeSolver()");
    		}
    		
    		javaSymbolSolver = new JavaSymbolSolver(combinedTypeSolver);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return javaSymbolSolver;
	}

	static private HashMap<Long, JavaParser> javaParserMap = new HashMap<Long, JavaParser>();
	
	public static JavaParser getJavaParser() throws Exception{
		//logger.debug("net.dstone.boot.common.tools.analyzer.util.ParseUtil.getJavaParser() has been called !!!");
		JavaParser parser = null;
    	try {
    		Long threadId = Thread.currentThread().getId();
    		if( !javaParserMap.containsKey(threadId) ) {
	    		parser = new JavaParser();
	    		ParserConfiguration config = parser.getParserConfiguration();
	    		
	    		config.setStoreTokens(false);
	    		config.setLexicalPreservationEnabled(false);
	    		config.setSymbolResolver(getJavaSymbolSolver());
	    		
	    		javaParserMap.put(threadId, parser);
    		}else {
    			parser = javaParserMap.get(threadId);
    		}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
    	return parser;
    }
	
	static HashMap<String, CompilationUnit> compilationUnitMap = new HashMap<String, CompilationUnit>();
	public static CompilationUnit getCompilationUnit(String fileName){
		CompilationUnit cu = null;
		if( compilationUnitMap.containsKey(fileName) ) {
			cu = compilationUnitMap.get(fileName);
		}else {
			try {
				if( FileUtil.isFileExist(fileName) ) {
					ParseResult<CompilationUnit> result = getJavaParser().parse(new File(fileName));
					if( result.isSuccessful() ) {
						cu = result.getResult().get();
					}else {
						cu = StaticJavaParser.parse(new File(fileName));
					}
					if( cu != null ) {
						compilationUnitMap.put(fileName, cu);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return cu;
	}

	/**
	 * 테이블명을 파싱하기 위해 SQL을 간소화 하는 메소드(주석제거, XML의 CDATA 태그제거, MYBATIS의 파라메터 세팅부분 변환)
	 * @param sqlBody
	 * @param sqlKind
	 * @return
	 */
	public static String removeBasicTagFromSql(String inputSql, String sqlKind) {
		String sqlBody = inputSql;
		sqlBody = sqlBody.trim();
	
		// 주석제거 및 쿼리정리
		sqlBody = XmlUtil.removeCommentsFromXml(sqlBody);
		sqlBody = SqlUtil.removeCommentsFromSql(sqlBody);
		sqlBody = StringUtil.trimTextForParse(sqlBody);
		
		// XML의 CDATA 태그제거
		sqlBody = StringUtil.replace(sqlBody, "<![CDATA[", "");
		sqlBody = StringUtil.replace(sqlBody, "]]>", "");
		
		// MYBATIS의 파라메터 태그제거하고 스몰쿼테이션추가
		sqlBody = StringUtil.replace(sqlBody, "#{", "'");
		sqlBody = StringUtil.replace(sqlBody, "#", "'");
		sqlBody = StringUtil.replace(sqlBody, "${", "'");
		sqlBody = StringUtil.replace(sqlBody, "$", "'");
		sqlBody = StringUtil.replace(sqlBody, "}", "'");
		sqlBody = StringUtil.replace(sqlBody, ", ,", ", 'AAA',");
		
		return sqlBody.trim();
	}
	
	/**
	 * 주석에서 기능명을 추출하는 메소드.
	 * @param comment
	 * @return
	 */
	public static String getFnNameFromComment(String comment) {
		String fnName = "";
		if(!StringUtil.isEmpty(comment)) {

			/*** 메서드명 세팅 ***/
			String[] mlines = StringUtil.toStrArray(comment, "\n") ;
			String keyword = "";
			for(String mline : mlines) {
				mline = mline.toUpperCase();
				if(StringUtil.isEmpty(fnName)) {
					keyword = "@DESCRIPTION";
					if(mline.indexOf(keyword) > -1) {
						fnName = mline;
						fnName = StringUtil.replace(fnName, keyword, "");
						fnName = StringUtil.replace(fnName, "/*", "");
						fnName = StringUtil.replace(fnName, "*", "");
						fnName = StringUtil.replace(fnName, " ", "");
						break;
					}
				}
				if(StringUtil.isEmpty(fnName)) {
					keyword = "@DESC";
					if(mline.indexOf(keyword) > -1) {
						fnName = mline;
						fnName = StringUtil.replace(fnName, keyword, "");
						fnName = StringUtil.replace(fnName, "/*", "");
						fnName = StringUtil.replace(fnName, "*", "");
						fnName = StringUtil.replace(fnName, " ", "");
						break;
					}
				}
				if(StringUtil.isEmpty(fnName)) {
					keyword = "@NAME";
					if(mline.indexOf(keyword) > -1) {
						fnName = mline;
						fnName = StringUtil.replace(fnName, keyword, "");
						fnName = StringUtil.replace(fnName, "/*", "");
						fnName = StringUtil.replace(fnName, "*", "");
						fnName = StringUtil.replace(fnName, " ", "");
						break;
					}
				}
			}
			if(StringUtil.isEmpty(fnName)) {
				for(String mline : mlines) {
					// 주석 첫줄이 아니고, 비어있는 주석라인이 아니고, @문자가들어가 있지 않다면 
					if( ( !mline.trim().startsWith("/*") ) && ( !mline.trim().equals("*") ) && ( mline.trim().startsWith("*") ) && (mline.indexOf("@") == -1) ) {
						fnName = mline;
						fnName = StringUtil.replace(fnName, "/*", "");
						fnName = StringUtil.replace(fnName, "*", "");
						fnName = StringUtil.replace(fnName, " ", "");
						break;
					}
				}
			}
			if(!StringUtil.isEmpty(fnName)) {
				if( fnName.indexOf("<") > -1 ) {
					fnName = fnName.substring(0, fnName.indexOf("<"));
				}
				fnName = StringUtil.replace(fnName, ":", "");
				fnName = StringUtil.replace(fnName, ";", "");
				fnName = StringUtil.replace(fnName, " ", "");
			}
		}
		return fnName;
	}

	/**
	 *  파일내용으로부터 클래스ID/메소드ID/메소드명/메소드URL/메소드내용 이 담긴 메소드정보목록 추출
	 *  LIST[ MAP<클래스ID(CLASS_ID), 메서드ID(METHOD_ID), 메서드명(METHOD_NAME), 메서드URL(METHOD_URL), 메서드바디(METHOD_BODY)> ]
	 * @param fileConts
	 * @return
	 */
	public static List<Map<String, String>> getMtdListFromJava(String fileConts){
		
		List<Map<String, String>> mList = new ArrayList<Map<String, String>>();
		
		boolean isCommentStart = false;
		boolean isCommentEnd = false;
		StringBuffer comment = new StringBuffer();

		boolean isSignitureStart = false;
		boolean isSignitureEnd = false;
		StringBuffer signiture = new StringBuffer();

		boolean isBodyStart = false;
		boolean isBodyEnd = false;
		StringBuffer body = new StringBuffer();
		
		int level = -1;
		int tgtLevel = 1;
		String startDiv = "{";
		String endDiv = "}";

		StringBuffer classId = new StringBuffer("");
		String classMappingUrl = "";
		String methodMappingUrl = "";
		
		String line = "";
		
		try {
			fileConts = StringUtil.replace(fileConts, "\t", " ");
			String[] lines = StringUtil.toStrArray(fileConts, "\n", true);

			for(int i=0; i<lines.length; i++) {
				line = lines[i];
				if(line.trim().startsWith("//")) {continue;}
				
				/*** A.클래스ID 수집 시작 ***/
				if(line.indexOf("package ") > -1) {
					String strForClassId = StringUtil.replace(line, "package ", "");
					strForClassId = StringUtil.replace(strForClassId, ";", "").trim();
					classId.append(strForClassId);
				}
				if(line.indexOf("class ") > -1) {
					String[] div = {" ", "{"};
					String strForClassId = StringUtil.nextWord(line, "class ", div).trim();
					classId.append(strForClassId);
				}
				/*** A.클래스ID 수집 끝 ***/

				/*** B.레벨조정 시작 ***/
				// 구분자 레벨 UP
				if(line.indexOf(startDiv) > -1) {
					level = level + StringUtil.countString(line, startDiv);
				}
				// 구분자 레벨 DOWN
				if(line.indexOf(endDiv) > -1) {
					level = level - StringUtil.countString(line, endDiv);
				}
				//System.out.println("level["+level+"] line["+line+"]");
				/*** B.레벨조정 끝 ***/
				
				/*** C.주석관련 수집 시작 ***/
				if(level == (tgtLevel-1)){ // tgtLevel 바로위의 레벨에 있는 내용을 수집.
					if(line.startsWith("/*")) {
						isCommentStart = true;
						isCommentEnd = false;
						comment.setLength(0);
					}
				}
				if(isCommentStart && !isCommentEnd) {
					comment.append(line).append("\n");
				}
				if(level == (tgtLevel-1)){ // tgtLevel 바로위의 레벨에 있는 내용을 수집.
					if(isCommentStart && line.endsWith("*/")) {
						isCommentEnd = true;
					}
				}
				/*** C.주석관련 수집 끝 ***/

				/*** D.맵핑URL 수집 시작 ***/
				if( line.indexOf("@")>-1 && line.indexOf("Mapping")>-1 ) {
					if(level == -1) {
						String signLine = line;					
						signLine = StringUtil.replace(signLine, " ", "");
						if( signLine.indexOf("value=\"")>-1  ) {
							String[] div = {"\""};
							classMappingUrl = StringUtil.nextWord(signLine, "value=\"", div);
						}else {
							String[] div = {"\""};
							classMappingUrl = StringUtil.nextWord(signLine, "(\"", div);
						}
						if(classMappingUrl.endsWith("*")) {
							classMappingUrl = classMappingUrl.substring(0, classMappingUrl.lastIndexOf("*"));
						}
						if(classMappingUrl.endsWith("/")) {
							classMappingUrl = classMappingUrl.substring(0, classMappingUrl.lastIndexOf("/"));
						}
					}
					if(level == 0) {
						methodMappingUrl = "";
						String signLine = line;					
						signLine = StringUtil.replace(signLine, " ", "");
						if( signLine.indexOf("value=\"")>-1  ) {
							String[] div = {"\""};
							methodMappingUrl = StringUtil.nextWord(signLine, "value=\"", div);
						}else {
							String[] div = {"\""};
							methodMappingUrl = StringUtil.nextWord(signLine, "(\"", div);
						}
						if(!StringUtil.isEmpty(classMappingUrl)) {
							methodMappingUrl = classMappingUrl + methodMappingUrl;
						}
					}
				}

				/*** D.맵핑URL 수집 끝 ***/
				
				/*** E.시그니쳐 수집 시작 ***/
				// 메서드ID 는 startDiv과 동일한 라인에 존재할 수도 있고 다음라인에 존재할 수도 있기 때문에 level로 검색할 수 없음.
				if(line.indexOf("(")>-1 && (line.startsWith("public ") || line.startsWith("protected ") || line.startsWith("private "))) {
					isSignitureStart = true;
					isSignitureEnd = false;
					signiture.setLength(0);
				}
				if(isSignitureStart && !isSignitureEnd) {
					String signLine = line;
					signLine = StringUtil.replace(signLine, "public", "");
					signLine = StringUtil.replace(signLine, "protected", "");
					signLine = StringUtil.replace(signLine, "private", "");
					
					String[] div = {" "};
					String methodName = StringUtil.beforeWord(signLine, "(", div);
					signiture.append(methodName);
				}
				if(isSignitureStart && line.indexOf(")") > -1) {
					isSignitureEnd = true;
				}
				/*** E.시그니쳐 수집 끝 ***/

				/*** F.바디 수집 시작 ***/
				if(level == tgtLevel){ // tgtLevel 의 레벨에 있는 내용을 수집.
					if(line.indexOf(startDiv) > -1) {
						isBodyStart = true;
						isBodyEnd = false;
						body.setLength(0);
					}
				}
				if(isBodyStart && !isBodyEnd) {
					String bodyLine = line;
					if( bodyLine.indexOf(startDiv) >-1 && !bodyLine.startsWith(startDiv) ) {
						bodyLine = bodyLine.substring(bodyLine.indexOf(startDiv));
					}
					body.append(bodyLine).append("\n");
				}
				if(level == (tgtLevel-1)){ // B.구분자 레벨 DOWN을 통해서 level이 1->0이 되어있고 Body구문이 시작된 상태이며 endDiv가 발생했을 때 Body구문은 끝남.
					if(isBodyStart && line.indexOf(endDiv) > -1) {
						isBodyEnd = true;
					}
				}
				/*** F.바디 수집 끝 ***/
				
				if( (isSignitureEnd && signiture.length() > 0) && (isBodyEnd && body.length() > 0) ) {

					Map<String, String> item = new HashMap<String, String>();
					mList.add(item);

					/*** 클래스ID 세팅 ***/
					item.put("CLASS_ID", classId.toString());
					/*** 메서드ID 세팅 ***/
					item.put("METHOD_ID", signiture.toString());
					signiture = new StringBuffer();
					/*** 메서드명 세팅 ***/
					item.put("METHOD_NAME", ParseUtil.getFnNameFromComment(comment.toString()));
					comment = new StringBuffer();
					/*** 메서드URL 세팅 ***/
					item.put("METHOD_URL", methodMappingUrl);
					/*** 메서드바디 세팅 ***/
					item.put("METHOD_BODY", body.toString());
					body = new StringBuffer();
					
				}

			}
		} catch (Exception e) {
			System.out.println("level["+level+"] line["+line+"] 수행중 예외발생.");
			e.printStackTrace();
		}		
		return mList;
	}
	/**
	 * 클래스ID의 알리아스를 추출하기 위해 해당 소스 라인이 적합한지 조사하는 메소드.(클래스 시작부분, 주석부분은 적합하지 않으므로 false반환)
	 * @param line
	 * @return
	 */
	public static boolean isValidAliasLine(String line) {
		boolean isValid = true;
		ArrayList<String> inValidCaseList = new ArrayList<String>();
		inValidCaseList.add("class ");
		inValidCaseList.add("implements ");
		inValidCaseList.add("extends ");
		inValidCaseList.add("throws ");
		for(String word : inValidCaseList) {
			if(line.indexOf(word)>-1) {
				isValid = false;
				break;
			}
		}
		if(isValid) {
			if(line.trim().startsWith("//") || line.trim().startsWith("/*") || line.trim().startsWith("*")) {
				isValid = false;
			}
		}
		return isValid;
	}
	
	/**
	 * 웹페이지의 A태그로부터 링크를 추출하는 메소드
	 * @param webPageFile
	 * @return
	 */
	public static List<String> extrackLinksFromAtag(String webPageFile){
		List<String> linkList = new ArrayList<String>();
		try {
			if(FileUtil.isFileExist(webPageFile)) {
				String keyword = "";
				String[] div = {"'"};
				String nextWord = "";
				
				String conts = FileUtil.readFile(webPageFile);
				conts = StringUtil.replace(conts, "\r\n", "");
				conts = StringUtil.replace(conts, "\n", "");
				conts = StringUtil.trimTextForParse(conts);
				conts = StringUtil.replace(conts, "\"", "'");
				
				String contsForAtag = new String(conts);
				
				// A태그 Link
				contsForAtag = StringUtil.replace(contsForAtag, "href =", "href=");
				contsForAtag = StringUtil.replace(contsForAtag, "href= '", "href='");
				contsForAtag = StringUtil.replace(contsForAtag, "href=''", "");
				keyword = "href='";
				nextWord = "";
				while(contsForAtag.indexOf(keyword) > -1) {
					if(contsForAtag.indexOf(keyword) > -1) {
						nextWord = StringUtil.nextWord(contsForAtag, keyword, div);
						if(nextWord.indexOf("?")>-1) {
							nextWord = nextWord.substring(0, nextWord.indexOf("?"));
						}
						linkList.add(nextWord);
						contsForAtag = contsForAtag.substring( contsForAtag.indexOf(nextWord) + (nextWord).length() );
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return linkList;
	}
	
	/**
	 * 웹페이지의 A태그로부터 링크를 파싱(추출)하는 메소드
	 * @param webPageFile
	 * @return
	 */
	public static List<String> parseLinksFromAtag(String webPageFile){
		List<String> linkList = new ArrayList<String>();
		try {

			if(FileUtil.isFileExist(webPageFile)) {

				org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse( net.dstone.common.utils.FileUtil.readFile(webPageFile) );
				String elVal = "";
				org.jsoup.select.Elements links = doc.select("a[href]");
			    for (org.jsoup.nodes.Element link : links) {
			    	elVal = link.attr("abs:href");
			    	if(!StringUtil.isEmpty(elVal)) {
			    		if(!linkList.contains(elVal)) {
			    			linkList.add(elVal);
			    		}
			    	}
			    }

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return linkList;
	}

	
	/**
	 * 웹페이지의 폼액션으로부터 링크를 추출하는 메소드
	 * @param webPageFile
	 * @return
	 */
	public static List<String> extrackLinksFromAction(String webPageFile){
		List<String> linkList = new ArrayList<String>();
		try {
			if(FileUtil.isFileExist(webPageFile)) {
				String keyword = "";
				String[] div = {"'"};
				String nextWord = "";
				
				String conts = FileUtil.readFile(webPageFile);
				conts = StringUtil.trimTextForParse(conts);
				conts = StringUtil.replace(conts, "\r\n", "");
				conts = StringUtil.replace(conts, "\n", "");
				conts = StringUtil.replace(conts, "\"", "'");
				
				String contsForAction = new String(conts);
				
				// Form Action
				contsForAction = StringUtil.replace(contsForAction, ".action =", ".action=");
				contsForAction = StringUtil.replace(contsForAction, ".action= '", ".action='");
				contsForAction = StringUtil.replace(contsForAction, "action =", "action=");
				contsForAction = StringUtil.replace(contsForAction, "action= '", "action='");
				contsForAction = StringUtil.replace(contsForAction, "action=''", "");
				keyword = "action='";
				nextWord = "";
				while(contsForAction.indexOf(keyword) > -1) {
					if(contsForAction.indexOf(keyword) > -1) {
						nextWord = StringUtil.nextWord(contsForAction, keyword, div);
						if(nextWord.indexOf("?")>-1) {
							nextWord = nextWord.substring(0, nextWord.indexOf("?"));
						}
						linkList.add(nextWord);
						contsForAction = contsForAction.substring( contsForAction.indexOf(nextWord) + (nextWord).length() );
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return linkList;
	}
	
	/**
	 * 웹페이지의 폼액션으로부터 링크를 파싱(추출)하는 메소드
	 * @param webPageFile
	 * @return
	 */
	public static List<String> parseLinksFromAction(String webPageFile){
		List<String> linkList = new ArrayList<String>();
		try {
			if(FileUtil.isFileExist(webPageFile)) {

				org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse( net.dstone.common.utils.FileUtil.readFile(webPageFile) );
				String elVal = "";
				org.jsoup.select.Elements links = doc.getElementsByTag("form");
			    for (org.jsoup.nodes.Element el : links) {
			    	elVal = el.attr("action");
			    	if(!StringUtil.isEmpty(elVal)) {
			    		if(!linkList.contains(elVal)) {
			    			linkList.add(elVal);
			    		}
			    	}
			    }
			    
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return linkList;
	}
	
	/**
	 * 어노테이션이 있는 라인으로부터 어노테이션값을 추출하는 메소드.
	 * 예)@Resource(name = "customUserService") ==>> customUserService
	 * 예)@Service("customUserService") ==>> customUserService
	 * @param annotationLine
	 * @return
	 */
	public static String getValueFromAnnotationLine(String annotationLine) {
		String annotationValue = "";
		
		if(!StringUtil.isEmpty(annotationLine) && annotationLine.indexOf("@")>-1) {
			if(annotationLine.indexOf("(")>-1) {
				annotationLine = annotationLine.trim();
				annotationLine = StringUtil.trimTextForParse(annotationLine);
				annotationLine = annotationLine.substring(annotationLine.indexOf("("));
				annotationLine = StringUtil.replace(annotationLine, "(", "");
				annotationLine = StringUtil.replace(annotationLine, "value", "");
				annotationLine = StringUtil.replace(annotationLine, "name", "");
				annotationLine = StringUtil.replace(annotationLine, "=", "");
				annotationLine = StringUtil.replace(annotationLine, ")", "");
				annotationLine = StringUtil.replace(annotationLine, "\"", "");
				annotationLine = StringUtil.replace(annotationLine, " ", "");
				annotationValue = annotationLine;
			}
		}
		
		return annotationValue;
	}
	
	/**
	 * 인터페이스인 클래스ID로 분석클래스파일목록(analyzedClassFileList)에서 구현클래스들의 클래스ID목록을 추출하는 메소드.
	 * resourceId 가 일치하는 구현클래스를 우선적으로 찾는다.
	 * @param interfaceId
	 * @param resourceId
	 * @return
	 */
	public static List<String> findImplClassList(String interfaceId, String resourceId) {
		List<String> implClassList = new ArrayList<String>();
		String implClassId = "";
		ClzzVo interfaceVo = ParseUtil.readClassVo(interfaceId, AppAnalyzer.WRITE_PATH + "/class");
		ClzzVo implClzzVo = null;
		if( "I".equals(interfaceVo.getClassOrInterface()) ) {
			List<String> implClassIdList = interfaceVo.getImplClassIdList();
			if(implClassIdList != null) {
				// 해당인터페이스 구현클래스 목록을 LOOP 돌리면서 인터페이스의 클래스ID 가 구현클래스의 인터페이스ID와 일치하는 구현클래스의 resourceId를 찾아서 비교한다.
				for(String packageClassId : implClassIdList) {
					implClzzVo = ParseUtil.readClassVo(packageClassId, AppAnalyzer.WRITE_PATH + "/class");
					implClassId = "";
					for(String inter : implClzzVo.getInterfaceIdList()) {
						// resourceId 로 찾고자 할 때
						if( !StringUtil.isEmpty(resourceId) ) {
							if( resourceId.equals(implClzzVo.getResourceId())) {
								implClassId = implClzzVo.getClassId();
								break;
							}
						// resourceId 로 찾지 않을 때	
						}else {
							implClassId = implClzzVo.getClassId();
							break;
						}
					}
					if( !StringUtil.isEmpty(implClassId) ) {
						implClassList.add(implClassId);
					}
				}
			}
		}
		return implClassList;
	}
	
	/**
	 * 특정한 패키지 내에서 인터페이스클래스명(interfaceId)으로 구현클래스목록(List<String>)을 추출하는 메소드.
	 * resourceId 가 일치하는 구현클래스를 우선적으로 찾는다.
	 * @param interfaceId
	 * @param resourceId
	 * @param packageRoot
	 * @return
	 */
	public static List<String> getImplClassList(String interfaceId, String resourceId, String... packageRoot) {
		List<String> implClassList = new ArrayList<String>();
		ScanResult scanResult = null;
		try {
			scanResult = new ClassGraph().enableAllInfo().acceptPackages(packageRoot).scan();

			for (ClassInfo ci : scanResult.getClassesImplementing(interfaceId)) {
				if( !StringUtil.isEmpty(resourceId) ) {
					boolean isExactResourceYn = false;
					
					ClassOrInterfaceDeclaration valClzz = null; 
					valClzz = ParseUtil.getClassDec(AppAnalyzer.CLASS_ROOT_PATH, ci.getName());
					for( AnnotationExpr anExpr :  valClzz.findAll(com.github.javaparser.ast.expr.AnnotationExpr.class) ) {
						if( 
							"Controller".equals(anExpr.getNameAsString()) 
							||  "RestController".equals(anExpr.getNameAsString())  
							||  "Service".equals(anExpr.getNameAsString())  
							||  "Repository".equals(anExpr.getNameAsString()) 
							||  "Component".equals(anExpr.getNameAsString()) 
						) {
							for( StringLiteralExpr strExpr :  anExpr.findAll(com.github.javaparser.ast.expr.StringLiteralExpr.class) ) {
								if(resourceId.equals(strExpr.getValue())) {
									isExactResourceYn = true;
									break;
								}
							}
						}
						if(isExactResourceYn) {
							break;
						}
					}
					if( isExactResourceYn && !implClassList.contains(ci.getName())) {
						implClassList.add(ci.getName());
					}
				}else {
					if(!implClassList.contains(ci.getName())) {
						implClassList.add(ci.getName());
					}
				}
		    }
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(scanResult!=null) {scanResult.close();}
		}
		return implClassList;
	}
	
	
	/**
	 * 구현클래스로 인터페이스목록을 추출하는 메소드.
	 * 인터페이스를 찾지 못하면 구현클래스ID를 반환한다.
	 * @param classId
	 * @return
	 */
	public static List<String> getInterfaceIdList(String classId, String... packageRoot) {
		List<String> interfaceIdList = new ArrayList<String>();
		ScanResult scanResult = null;
		try {
			scanResult = new ClassGraph().enableAllInfo().acceptPackages(packageRoot).scan();
			for (ClassInfo ci : scanResult.getInterfaces(classId) ) {
				if(!interfaceIdList.contains(ci.getName())) {
					interfaceIdList.add(ci.getName());
				}
		    }
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(scanResult!=null) {scanResult.close();}
		}
		return interfaceIdList;
	}
	
	/**
	 * 필드 명으로 Getter 명을 추출하여 반환하는 메소드. Getter 는 카멜표기법에 기반하는걸로 간주.
	 * @param fieldName
	 * @return
	 */
	public static String getGetterNmFromField(String fieldName) {
		String getterNm = "";
		if(!StringUtil.isEmpty(fieldName)) {
			getterNm = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1)  + "()";
		}
		return getterNm;
	}

	/**
	 * Getter 명으로  필드 명을 추출하여 반환하는 메소드. Getter 는 카멜표기법에 기반하는걸로 간주.
	 * @param getterNm
	 * @return
	 */
	public static String getFieldFromGetterNm(String getterNm) {
		String fieldName = "";
		if(!StringUtil.isEmpty(getterNm)) {
			if(getterNm.startsWith("get")) {
				fieldName = fieldName.substring(3);
			}
			if(getterNm.endsWith("()")) {
				fieldName = StringUtil.replace(fieldName, "()", "");
			}
		}
		return fieldName;
	}
	

	/**
	 * 클래스명(xx.xxx.TestBean)으로 ClassOrInterfaceDeclaration 찾아서 반환.
	 * @param srcRoot
	 * @param clzzQualifiedName
	 * @return
	 */
	public static ClassOrInterfaceDeclaration getClassDec(String srcRoot, String clzzQualifiedName) throws Exception { 
		ClassOrInterfaceDeclaration classDec = null; 
		String filePath = "";
		try {
			filePath = srcRoot+"/"+ StringUtil.replace(clzzQualifiedName,".", "/")+".java" ;
			if( FileUtil.isFileExist(filePath)) {
				
				if(PARSE_CLASS_DECL_MAP.containsKey(filePath)) {
					classDec = PARSE_CLASS_DECL_MAP.get(filePath);
				}else {
					ParseResult<CompilationUnit> result = getJavaParser().parse(new File(filePath));
					if( result.isSuccessful() && result.getResult().isPresent() ) {
						CompilationUnit clzzCU = result.getResult().get(); 
						if( clzzCU.findFirst(ClassOrInterfaceDeclaration.class).isPresent()) { 
							classDec = clzzCU.findFirst(ClassOrInterfaceDeclaration.class).get();
						}
					}
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		return classDec;
	}
	
	/**
	 * ClassOrInterfaceDeclaration으로 ResolvedReferenceTypeDeclaration을 찾아서 반환.
	 * @param classDec
	 * @return
	 */
	public static ResolvedReferenceTypeDeclaration getReClassDec(ClassOrInterfaceDeclaration classDec) throws Exception { 
		ResolvedReferenceTypeDeclaration reClassDec = null; 
		try {
			if( classDec != null) {
				if(RE_PARSE_CLASS_DECL_MAP.containsKey(classDec)) {
					reClassDec = RE_PARSE_CLASS_DECL_MAP.get(classDec);
				}else {
					reClassDec = classDec.resolve();
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		return reClassDec;
	}
	
	/**
	 * 메서드기능ID(xx.xxx.TestBean.testMethed(java.lang.String))으로 MethodDeclaration 찾아서 반환.
	 * @param srcRoot
	 * @param methodQualifiedSignature
	 * @return
	 */
	public static MethodDeclaration getMethodDec(String srcRoot, String methodQualifiedSignature) throws Exception { 
		MethodDeclaration mtdDec = null;
		try {
			String clzzQualifiedName = "";
			clzzQualifiedName = methodQualifiedSignature.substring(0, methodQualifiedSignature.indexOf("("));
			clzzQualifiedName = clzzQualifiedName.substring(0, clzzQualifiedName.lastIndexOf(".")); 
			
			ClassOrInterfaceDeclaration classDec =  ParseUtil.getClassDec(srcRoot, clzzQualifiedName); 
			if( classDec != null ) {
				for (MethodDeclaration mDec : classDec.getMethods()) {
					if( ParseUtil.getReMethodDec(mDec).getQualifiedSignature().equals(methodQualifiedSignature) ) {
						mtdDec = mDec;
						break;
					}
				}
			}
		} catch (Exception e) {
			logger.info("net.dstone.boot.common.tools.analyzer.util.ParseUtil.getMethodDec("+srcRoot+", "+methodQualifiedSignature+") 수행중 예외발생.");
			e.printStackTrace();
			throw e;
		}

		return mtdDec;
	}
	
	/**
	 * MethodDeclaration으로 ResolvedMethodDeclaration을 찾아서 반환.
	 * @param mtdDec
	 * @return
	 */
	public static ResolvedMethodDeclaration getReMethodDec(Object mtdDec) throws Exception { 
		ResolvedMethodDeclaration reMtdDec = null;
		try {
			if( mtdDec != null ) {
				if( RE_PARSE_METHOD_DECL_MAP.containsKey(mtdDec) ) {
					reMtdDec = RE_PARSE_METHOD_DECL_MAP.get(mtdDec);
				}else {
					if( mtdDec instanceof MethodDeclaration ) {
						reMtdDec = ((MethodDeclaration)mtdDec).resolve();
					}else if( mtdDec instanceof MethodCallExpr ) {
						reMtdDec = ((MethodCallExpr)mtdDec).resolve();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		return reMtdDec;
	}
	
	public static String convFunctionIdToFileName(String functionId) {
		String functionIdToFileName = functionId;
		functionIdToFileName = StringUtil.replace(functionIdToFileName, ">", "]");
		functionIdToFileName = StringUtil.replace(functionIdToFileName, "<", "[");
		functionIdToFileName = StringUtil.replace(functionIdToFileName, "?", "#");
		return functionIdToFileName;
	}
	
	public static String convFunctionIdFromFileName(String fileName) {
		String functionIdFromFileName = fileName;
		functionIdFromFileName = StringUtil.replace(functionIdFromFileName, "]", ">");
		functionIdFromFileName = StringUtil.replace(functionIdFromFileName, "[", "<");
		functionIdFromFileName = StringUtil.replace(functionIdFromFileName, "#", "?");
		return functionIdFromFileName;
	}
	
	/**
	 * 수동 테이블목록파일을 생성한다. 기존에 존재하는 테이블목록은 유지한다.
	 * @param DBID
	 * @param tableNameLikeStr
	 */
	public static void makeMannalTableListFileFromDb( String DBID, String tableNameLikeStr) {
		
		HashMap<String, DataSet> tblMap = new HashMap<String, DataSet>();
		DataSet dsTblRow = null;
		DataSet dsTblListFromFile = null;
		DataSet dsTblListFromDb = null;
		String tableName = "";
		String tableComment = "";
		
		String tblListFilePath = AppAnalyzer.WRITE_PATH + "/" +  AppAnalyzer.TABLE_LIST_FILE_NAME;
		StringBuffer fileConts = new StringBuffer();
		
		// 파일에 존재하는 테이블목록
		dsTblListFromFile = new DataSet();
		if( FileUtil.isFileExist(tblListFilePath) ) {
			String[] lines = FileUtil.readFileByLines(tblListFilePath);
			for(String line : lines) {
				tableName = "";
				tableComment = "";
				if(line.trim().equals("")) {
					continue;
				}
				dsTblRow = dsTblListFromFile.addDataSet("TBL_LIST");
				String[] words = StringUtil.toStrArray(line, "\t", true);
				if(words != null && words.length > 0 && !StringUtil.isEmpty(words[0])) {
					tableName = words[0].toUpperCase();
					dsTblRow.setDatum("TABLE_NAME", tableName);
				}
				if(words != null && words.length > 1 && !StringUtil.isEmpty(words[1])) {
					tableComment = words[1].toUpperCase();
					dsTblRow.setDatum("TABLE_COMMENT", tableComment);
				}
			}
		}
		
		// DB에서 읽어온 테이블목록
		dsTblListFromDb = DbUtil.getTabs(DBID, tableNameLikeStr);
		
		// 파일에 존재하는 테이블목록 맵에 추가
		if(dsTblListFromFile.isChildExists("TBL_LIST")) {
			for(int i=0; i<dsTblListFromFile.getDataSetRowCount("TBL_LIST"); i++) {
				dsTblRow = dsTblListFromFile.getDataSet("TBL_LIST", i);
				tableName = dsTblRow.getDatum("TABLE_NAME").toUpperCase();
				dsTblRow.setDatum("TABLE_NAME", tableName);
				if(!tblMap.containsKey(tableName)) {
					tblMap.put(tableName, dsTblRow);
				}
			}
		}

		// DB에서 읽어온 테이블목록 맵에 추가
		if(dsTblListFromDb.isChildExists("TBL_LIST")) {
			for(int i=0; i<dsTblListFromDb.getDataSetRowCount("TBL_LIST"); i++) {
				dsTblRow = dsTblListFromDb.getDataSet("TBL_LIST", i);
				tableName = dsTblRow.getDatum("TABLE_NAME").toUpperCase();
				dsTblRow.setDatum("TABLE_NAME", tableName);
				if(!tblMap.containsKey(tableName)) {
					tblMap.put(tableName, dsTblRow);
				}
			}
		}
		
		// 맵에 담긴 테이블정보 파일로 저장.
        List<String> keySet = new ArrayList<>(tblMap.keySet());
        Collections.sort(keySet);
        for (String key : keySet) {
        	dsTblRow = tblMap.get(key);
        	if( fileConts.length() > 0 ) {
        		fileConts.append("\n");
        	}
        	tableName = dsTblRow.getDatum("TABLE_NAME");
        	tableComment = dsTblRow.getDatum("TABLE_COMMENT");
        	fileConts.append(tableName);
        	fileConts.append("\t");
        	fileConts.append(tableComment);
        }
        FileUtil.writeFile(AppAnalyzer.WRITE_PATH, AppAnalyzer.TABLE_LIST_FILE_NAME, fileConts.toString());
	}

	/**
	 * 수동으로 모아 놓은 테이블목록을 반환한다.
	 * @return
	 */
	public static List<String> getMannalTableList() {
		initMannalTableInfo();
		return MANNUAL_TABLE_LIST;
	}

	/**
	 * 수동으로 모아 놓은 테이블정보맵목록을 반환한다.
	 * @return
	 */
	public static List<Map<String, String>> getMannalTableMapList() {
		initMannalTableInfo();
		return MANNUAL_TABLE_MAP_LIST;
	}

	/**
	 * 수동으로 모아 놓은 테이블정보맵을 반환한다.
	 * @return
	 */
	public static Map<String, String> getMannalTableMap(String tableName) {
		initMannalTableInfo();
		return MANNUAL_TABLE_LIST_MAP.get(tableName);
	}
	
	private static void initMannalTableInfo() {
		if( MANNUAL_TABLE_LIST.isEmpty() ) {
			String tblListFilePath = AppAnalyzer.WRITE_PATH + "/" +  AppAnalyzer.TABLE_LIST_FILE_NAME;
			if( FileUtil.isFileExist(tblListFilePath) ) {
				String[] lines = FileUtil.readFileByLines(tblListFilePath);
				String tableName = "";
				String tableComment = "";
				for(String line : lines) {
					tableName = "";
					tableComment = "";
					if(line.trim().equals("")) {
						continue;
					}
					String[] words = StringUtil.toStrArray(line, "\t", true);
					if(words != null && words.length > 0 && !StringUtil.isEmpty(words[0])) {
						tableName = words[0].toUpperCase();
						if( MANNUAL_TABLE_LIST.contains(tableName) ) {
							continue;
						}
						MANNUAL_TABLE_LIST.add(tableName);
						
						Map<String, String> row = new HashMap<String, String>();
						row.put("TABLE_NAME", tableName);
						if(words.length > 1 && !StringUtil.isEmpty(words[1])) {
							tableComment = words[1].toUpperCase();
							row.put("TABLE_COMMENT", tableComment.toUpperCase());
						}
						MANNUAL_TABLE_MAP_LIST.add(row);
						MANNUAL_TABLE_LIST_MAP.put(tableName, row);
					}
				}
			}
		}
	}
	/**
	 * 클래스VO를 특정디렉토리에 파일로 저장하는 메소드
	 * @param vo
	 * @param writeFilePath
	 */
	public static void writeClassVo(ClzzVo vo, String writeFilePath) {
		String fileName = "";
		StringBuffer fileConts = new StringBuffer();
		StringBuffer interfaceIdConts = new StringBuffer();
		StringBuffer implClassIdConts = new StringBuffer();
		StringBuffer callClassAliasConts = new StringBuffer();
		String div = "|";
		try {
			fileName = vo.getClassId()+ ".txt";
			fileConts.append("패키지" + div + StringUtil.nullCheck(vo.getPackageId(), "")).append("\n");
			fileConts.append("클래스ID" + div + StringUtil.nullCheck(vo.getClassId(), "")).append("\n");
			fileConts.append("클래스명" + div + StringUtil.nullCheck(vo.getClassName(), "")).append("\n");
			fileConts.append("기능종류" + div + StringUtil.nullCheck(vo.getClassKind(), "")).append("\n");
			fileConts.append("리소스ID" + div + StringUtil.nullCheck(vo.getResourceId(), "")).append("\n");
			fileConts.append("클래스or인터페이스" + div + StringUtil.nullCheck(vo.getClassOrInterface(), "")).append("\n");
			List<String> interfaceIdList = vo.getInterfaceIdList();
			if(interfaceIdList != null) {
				for(String item : interfaceIdList) {
					if(interfaceIdConts.length() > 0) {
						interfaceIdConts.append(AppAnalyzer.DIV);
					}
					interfaceIdConts.append(item);
				}
			}
			fileConts.append("상위인터페이스ID목록" + div  + interfaceIdConts.toString() ).append("\n");
			
			fileConts.append("상위클래스ID" + div + StringUtil.nullCheck(vo.getParentClassId(), "")).append("\n");
			List<String> implClassIdList = vo.getImplClassIdList();
			if(implClassIdList != null) {
				for(String item : implClassIdList) {
					if(implClassIdConts.length() > 0) {
						implClassIdConts.append(AppAnalyzer.DIV);
					}
					implClassIdConts.append(item);
				}
			}
			fileConts.append("인터페이스구현하위클래스ID목록" + div  + implClassIdConts.toString() ).append("\n");
			
			List<Map<String, String>> callClassAlias = vo.getCallClassAlias();
			if(callClassAlias != null) {
				for(Map<String, String> item : callClassAlias) {
					if(callClassAliasConts.length() > 0) {
						callClassAliasConts.append(AppAnalyzer.DIV);
					}
					callClassAliasConts.append(item.get("FULL_CLASS")+"-"+item.get("ALIAS"));
				}
			}
			fileConts.append("호출알리아스" + div  + callClassAliasConts.toString() ).append("\n");
			
			fileConts.append("파일명" + div + StringUtil.nullCheck(vo.getFileName(), "")).append("\n");
			FileUtil.writeFile(writeFilePath, fileName, fileConts.toString()); 
		} catch (Exception e) {
			System.out.println("fileName["+fileName+"] 수행중 예외발생.");	
			e.printStackTrace();
		}
	}
	
	/**
	 * 페키지ID+클래스ID로 특정디렉토리에서 클래스VO를 복원하는 메소드
	 * @param packageClassId
	 * @param readFilePath
	 * @return
	 */
	public static ClzzVo readClassVo(String packageClassId, String readFilePath) {
		ClzzVo vo = new ClzzVo();
		String fileName = "";
		String div = "|";
		try {
			fileName = readFilePath + "/" + packageClassId+ ".txt";
			if(FileUtil.isFileExist(fileName)) {
				String[] lines = FileUtil.readFileByLines(fileName);
				for(String line : lines) {
					if(StringUtil.isEmpty(line.trim())) {continue;}
					if(line.startsWith("패키지" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setPackageId(words[1]);
						}
					}
					if(line.startsWith("클래스ID" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setClassId(words[1]);
						}
					}
					if(line.startsWith("클래스명" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setClassName(words[1]);
						}
					}
					if(line.startsWith("기능종류" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setClassKind(ClzzKind.getClzzKindCd(words[1]));
						}
					}
					
					if(line.startsWith("리소스ID" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setResourceId(words[1]);
						}
					}

					if(line.startsWith("클래스or인터페이스" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setClassOrInterface(words[1]);
						}
					}
					if(line.startsWith("상위인터페이스ID목록" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							List<String> interfaceIdList = new ArrayList<String>();
							String[] interfaceIdStrList = StringUtil.toStrArray(words[1], AppAnalyzer.DIV);
							for(String interfaceIdStr : interfaceIdStrList) {
								interfaceIdList.add(interfaceIdStr);
							}
							vo.setInterfaceIdList(interfaceIdList);
						}
					}
					if(line.startsWith("상위클래스ID" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setParentClassId(words[1]);
						}
					}
					if(line.startsWith("인터페이스구현하위클래스ID목록" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							List<String> implClassIdList = new ArrayList<String>();
							String[] implClassIdStrList = StringUtil.toStrArray(words[1], AppAnalyzer.DIV);
							for(String implClassIdStr : implClassIdStrList) {
								implClassIdList.add(implClassIdStr);
							}
							vo.setImplClassIdList(implClassIdList);
						}
					}
					if(line.startsWith("호출알리아스" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							List<Map<String, String>> callClassAlias = new ArrayList<Map<String, String>>();
							String[] classAliasStrList = StringUtil.toStrArray(words[1], AppAnalyzer.DIV);
							for(String classAliasStr : classAliasStrList) {
								String[] classAliasStrPair = StringUtil.toStrArray(classAliasStr, "-");
								if(classAliasStrPair.length > 1) {
									Map<String, String> classAlias = new HashMap<String, String>();
									classAlias.put("FULL_CLASS", classAliasStrPair[0]);
									classAlias.put("ALIAS", classAliasStrPair[1]);
									callClassAlias.add(classAlias);
								}
							}
							vo.setCallClassAlias(callClassAlias);
						}
					}

					if(line.startsWith("파일명" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setFileName(words[1]);
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return vo;
	}
	

	
	/**
	 * 메소드VO를 특정디렉토리에 파일로 저장하는 메소드
	 * @param vo
	 * @param writeFilePath
	 */
	public static void writeMethodVo(MtdVo vo, String writeFilePath) {
		String fileName = "";
		StringBuffer fileConts = new StringBuffer();
		String div = "|";
		StringBuffer callTblVoListConts = new StringBuffer();
		StringBuffer callMtdVoListConts = new StringBuffer();
		try {
			
			fileConts.append("기능ID" + div + StringUtil.nullCheck(vo.getFunctionId(), "")).append("\n");
			fileConts.append("클래스ID" + div + StringUtil.nullCheck(vo.getClassId(), "")).append("\n");
			fileConts.append("메서드ID" + div + StringUtil.nullCheck(vo.getMethodId(), "")).append("\n");
			fileConts.append("메서드명" + div + StringUtil.nullCheck(vo.getMethodName(), "")).append("\n");
			fileConts.append("메서드URL" + div + StringUtil.nullCheck(vo.getMethodUrl(), "")).append("\n");
			fileConts.append("파일명" + div + StringUtil.nullCheck(vo.getFileName(), "")).append("\n");
			
			List<String> callMtdVoList = vo.getCallMtdVoList();
			if(callMtdVoList != null) {
				for(String item : callMtdVoList) {
					if(callMtdVoListConts.length() > 0) {
						callMtdVoListConts.append(AppAnalyzer.DIV);
					}
					callMtdVoListConts.append(item);
				}
			}
			fileConts.append("호출메서드" + div + StringUtil.nullCheck(callMtdVoListConts, "")).append("\n");
			
			List<String> callTblVoList = vo.getCallTblVoList();
			if(callTblVoList != null) {
				for(String item : callTblVoList) {
					if(callTblVoListConts.length() > 0) {
						callTblVoListConts.append(AppAnalyzer.DIV);
					}
					callTblVoListConts.append(item);
				}
			}
			fileConts.append("호출테이블" + div + StringUtil.nullCheck(callTblVoListConts, "")).append("\n");
			
			fileConts.append("메서드내용" + div + StringUtil.nullCheck(vo.getMethodBody(), "")).append("\n");
			
			fileName = ParseUtil.convFunctionIdToFileName(vo.getFunctionId()) + ".txt";
			FileUtil.writeFile(writeFilePath, fileName, fileConts.toString()); 
			
		} catch (Exception e) {
			System.out.println("fileName["+fileName+"] 수행중 예외발생.");	
			e.printStackTrace();
		}
	}
	
	/**
	 * 기능ID(페키지ID+클래스ID+메소드ID) 로 특정디렉토리에서 메소드VO를 복원하는 메소드
	 * @param functionId
	 * @param readFilePath
	 * @return
	 */
	public static MtdVo readMethodVo(String functionId, String readFilePath) {
		MtdVo vo = new MtdVo();
		String fileName = "";
		String div = "|";
		try {

			fileName = readFilePath + "/" + ParseUtil.convFunctionIdToFileName(functionId) + ".txt";
			if(FileUtil.isFileExist(fileName)) {
				String[] lines = FileUtil.readFileByLines(fileName);
				for(String line : lines) {
					if(StringUtil.isEmpty(line.trim())) {continue;}
					if(line.startsWith("기능ID" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setFunctionId(words[1]);
						}
					}
					if(line.startsWith("클래스ID" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setClassId(words[1]);
						}
					}
					if(line.startsWith("메서드ID" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setMethodId(words[1]);
						}
					}
					if(line.startsWith("메서드명" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setMethodName(words[1]);
						}
					}
					if(line.startsWith("메서드URL" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setMethodUrl(words[1]);
						}
					}
					if(line.startsWith("파일명" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setFileName(words[1]);
						}
					}
					if(line.startsWith("호출메서드" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							List<String> callMtdVoList = new ArrayList<String>();
							String[] callMtdVoStrList = StringUtil.toStrArray(words[1], AppAnalyzer.DIV);
							for(String callMtdVoStr : callMtdVoStrList) {
								callMtdVoList.add(callMtdVoStr);
							}
							vo.setCallMtdVoList(callMtdVoList);
						}
					}
					if(line.startsWith("호출테이블" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							List<String> callTblVoList = new ArrayList<String>();
							String[] callTblVoStrList = StringUtil.toStrArray(words[1], AppAnalyzer.DIV);
							for(String callTblVoStr : callTblVoStrList) {
								callTblVoList.add(callTblVoStr);
							}
							vo.setCallTblVoList(callTblVoList);
						}
					}
					if(line.startsWith("메서드내용" + div)) {
						String methodBody = FileUtil.readFile(fileName);
						String keyword = "메서드내용" + div;
						methodBody = methodBody.substring(methodBody.indexOf(keyword)+keyword.length());
						vo.setMethodBody(methodBody);
					}
				}
			}else {
				logger.debug("net.dstone.boot.common.tools.analyzer.util.ParseUtil.readMethodVo("+functionId+", "+readFilePath+") ::: fileName["+fileName+"]이 존재하지 않음.");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return vo;
	}
	

	
	
	/**
	 * 쿼리VO를 특정디렉토리에 파일로 저장하는 메소드
	 * @param vo
	 * @param writeFilePath
	 */
	public static void writeQueryVo(QueryVo vo, String writeFilePath) {
		String fileName = "";
		StringBuffer queryInfoConts = new StringBuffer();
		String div = "|";
		StringBuffer tblListConts = new StringBuffer();
		try {
			fileName = StringUtil.nullCheck(vo.getKey(), "") + ".txt";
			queryInfoConts.append("KEY" + div + StringUtil.nullCheck(vo.getKey(), "")).append("\n");
			queryInfoConts.append("네임스페이스" + div + StringUtil.nullCheck(vo.getNamespace(), "")).append("\n");
			queryInfoConts.append("쿼리ID" + div + StringUtil.nullCheck(vo.getQueryId(), "")).append("\n");
			queryInfoConts.append("쿼리종류" + div + StringUtil.nullCheck(vo.getQueryKind(), "")).append("\n");
			queryInfoConts.append("파일명" + div + StringUtil.nullCheck(vo.getFileName(), "")).append("\n");
			List<String> callTblList = vo.getCallTblList();
			if(callTblList != null) {
				for(String item : callTblList) {
					if(tblListConts.length() > 0) {
						tblListConts.append(AppAnalyzer.DIV);
					}
					tblListConts.append(item);
				}
			}
			queryInfoConts.append("호출테이블" + div + StringUtil.nullCheck(tblListConts, "")).append("\n");
			queryInfoConts.append("쿼리내용" + div + StringUtil.nullCheck(vo.getQueryBody(), "")).append("\n");
			
			FileUtil.writeFile(writeFilePath, fileName, queryInfoConts.toString()); 
		} catch (Exception e) {
			System.out.println("fileName["+fileName+"] 수행중 예외발생.");	
			e.printStackTrace();
		}
	}
	
	/**
	 * 쿼리ID로 특정디렉토리에서 쿼리VO를 복원하는 메소드
	 * @param key
	 * @param readFilePath
	 * @return
	 */
	public static QueryVo readQueryVo(String key, String readFilePath) {
		QueryVo vo = new QueryVo();
		String fileName = "";
		String div = "|";
		try {
			fileName = readFilePath + "/" + key+ ".txt";
			if(FileUtil.isFileExist(fileName)) {
				String[] lines = FileUtil.readFileByLines(fileName);
				for(String line : lines) {
					if(StringUtil.isEmpty(line.trim())) {continue;}
					if(line.startsWith("KEY" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setKey(words[1]);
						}
					}
					if(line.startsWith("네임스페이스" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setNamespace(words[1]);
						}
					}
					if(line.startsWith("쿼리ID" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setQueryId(words[1]);
						}
					}
					if(line.startsWith("쿼리종류" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setQueryKind(words[1]);
						}
					}
					if(line.startsWith("파일명" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setFileName(words[1]);
						}
					}
					if(line.startsWith("호출테이블" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							List<String> callTblList = new ArrayList<String>();
							String[] callTblStrList = StringUtil.toStrArray(words[1], AppAnalyzer.DIV);
							for(String callTblStr : callTblStrList) {
								callTblList.add(callTblStr);
							}
							vo.setCallTblList(callTblList);
						}
					}
					if(line.startsWith("쿼리내용" + div)) {
						String queryBody = FileUtil.readFile(fileName);
						String keyword = "쿼리내용" + div;
						queryBody = queryBody.substring(queryBody.indexOf(keyword)+keyword.length());
						vo.setQueryBody(queryBody);
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return vo;
	}
	


	
	/**
	 * UiVO를 특정디렉토리에 파일로 저장하는 메소드
	 * @param vo
	 * @param writeFilePath
	 */
	public static void writeUiVo(UiVo vo, String writeFilePath) {
		String fileName = "";
		StringBuffer uiConts = new StringBuffer();
		String div = "|";
		StringBuffer linkConts = new StringBuffer();
		try {
			fileName = StringUtil.nullCheck(vo.getUiId(), "") + ".txt";
			uiConts.append("UI아이디" + div + StringUtil.nullCheck(vo.getUiId(), "")).append("\n");
			uiConts.append("UI명" + div + StringUtil.nullCheck(vo.getUiName(), "")).append("\n");
			uiConts.append("파일명" + div + StringUtil.nullCheck(vo.getFileName(), "")).append("\n");
			List<String> linkList = vo.getLinkList();
			if(linkList != null) {
				for(String item : linkList) {
					if(linkConts.length() > 0) {
						linkConts.append(AppAnalyzer.DIV);
					}
					linkConts.append(item);
				}
			}
			uiConts.append("링크" + div + StringUtil.nullCheck(linkConts, "")).append("\n");
			FileUtil.writeFile(writeFilePath, fileName, uiConts.toString()); 
		} catch (Exception e) {
			System.out.println("fileName["+fileName+"] 수행중 예외발생.");	
			e.printStackTrace();
		}
	}
	
	/**
	 * UI아이디로 특정디렉토리에서 UiVO를 복원하는 메소드
	 * @param uiId
	 * @param readFilePath
	 * @return
	 */
	public static UiVo readUiVo(String uiId, String readFilePath) {
		UiVo vo = new UiVo();
		String fileName = "";
		String div = "|";
		try {
			fileName = readFilePath + "/" + uiId+ ".txt";
			if(FileUtil.isFileExist(fileName)) {
				String[] lines = FileUtil.readFileByLines(fileName);
				for(String line : lines) {
					if(StringUtil.isEmpty(line.trim())) {continue;}
					if(line.startsWith("UI아이디" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setUiId(words[1]);
						}
					}
					if(line.startsWith("UI명" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setUiName(words[1]);
						}
					}
					if(line.startsWith("파일명" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							vo.setFileName(words[1]);
						}
					}
					if(line.startsWith("링크" + div)) {
						String[] words = StringUtil.toStrArray(line, div);
						if(words.length > 1) {
							List<String> linkList = new ArrayList<String>();
							String[] linkStrList = StringUtil.toStrArray(words[1], AppAnalyzer.DIV);
							for(String linkStr : linkStrList) {
								linkList.add(linkStr);
							}
							vo.setLinkList(linkList);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return vo;
	}


	

}

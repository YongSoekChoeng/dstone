package net.dstone.boot.common.tools.analyzer.svc.mtd.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dstone.common.core.BaseObject;
import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.svc.mtd.ParseMtd;
import net.dstone.boot.common.tools.analyzer.util.ParseUtil;
import net.dstone.boot.common.tools.analyzer.vo.ClzzVo;
import net.dstone.boot.common.tools.analyzer.vo.MtdVo;
import net.dstone.boot.common.tools.analyzer.vo.QueryVo;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

public class TextParseMtd extends BaseObject implements ParseMtd {

	/**
	 * 파일로부터 메소드ID/메소드명/메소드URL/메소드내용 이 담긴 메소드정보목록 추출
	 * LIST[ MAP<클래스ID(CLASS_ID), 메서드ID(METHOD_ID), 메서드명(METHOD_NAME), 메서드URL(METHOD_URL), 메서드바디(METHOD_BODY)> ]
	 * @param classFile
	 * @return
	 */
	@Override
	public List<Map<String, String>> getMtdInfoList(String classFile) throws Exception  {
		String fileConts = FileUtil.readFile(classFile); 
		fileConts = StringUtil.trimTextForParse(fileConts);
		return ParseUtil.getMtdListFromJava(fileConts);
	}

	/**
	 * 호출메소드 목록 추출
	 * @param clzzVo
	 * @param analyzedMethodFile
	 * @return
	 */
	@Override
	public List<String> getCallMtdList(String analyzedMethodFile) throws Exception  {
		List<String> callsMtdList = new ArrayList<String>();
		
		// 메소드VO 정보 획득
		String functionId = FileUtil.getFileName(analyzedMethodFile, false);
		MtdVo mtdVo = ParseUtil.readMethodVo(functionId, AppAnalyzer.WRITE_PATH + "/method");
		
		// 클래스VO 정보 획득
		ClzzVo clzzVo = ParseUtil.readClassVo(mtdVo.getClassId(), AppAnalyzer.WRITE_PATH + "/class");

		// 클래스 호출알리아스 정보
		List<Map<String, String>> callClassAliasList = clzzVo.getCallClassAlias();
		
		String mtdBody = "";
		String callPackageClassId = "";
		String callAlias = "";
		String keyword = "";
		String callMtd = "";
		String[] div = {"("};
		ClzzVo classOrInterfaceVo = null;
		
		if( !StringUtil.isEmpty(mtdVo.getMethodBody()) ) {
			mtdBody = mtdVo.getMethodBody();
			String[] lines = StringUtil.toStrArray(mtdBody, "\n");
			for(String line : lines) {
				keyword = "";
				callMtd = "";
				if(callClassAliasList != null) {
					for(Map<String, String> callClassAlias : callClassAliasList) {
						callPackageClassId = callClassAlias.get("FULL_CLASS");
						// callPackageClassId 가 인터페이스 일 경우 구현클래스를 찾아서 대체하여 callMtd 에 삽입
						classOrInterfaceVo = ParseUtil.readClassVo(callPackageClassId, AppAnalyzer.WRITE_PATH + "/class");
						if("I".equals(classOrInterfaceVo.getClassOrInterface())) {
							List<String> implList = ParseUtil.findImplClassList(classOrInterfaceVo.getClassId(), classOrInterfaceVo.getResourceId());
							for(String impl : implList) {
								callPackageClassId = impl;
								callAlias = callClassAlias.get("ALIAS");
								// 클래스 호출알리아스+'.' 이후에 시작되는 메소드명 추출.(스페이스 이후에 키워드로 시작되거나 스페이스없이 키워드로 시작되는 라인이 있으면 검색)
								keyword =  callAlias+ "." ;
								if( line.indexOf(" " + keyword)>-1 || line.startsWith(keyword) ) {					
									callMtd = callPackageClassId + "." + StringUtil.nextWord(line, keyword, div);
								}
								// 클래스 호출알리아스Getter+'.' 이후에 시작되는 메소드명 추출.(스페이스 이후에 키워드로 시작되거나 스페이스없이 키워드로 시작되는 라인이 있으면 검색)
								keyword =  ParseUtil.getGetterNmFromField(callAlias)+ "." ;
								if( line.indexOf(" " + keyword)>-1 || line.startsWith(keyword) ) {					
									callMtd = callPackageClassId + "." + StringUtil.nextWord(line, keyword, div);
								}
								if( !StringUtil.isEmpty(callMtd) ) {
									if(!callsMtdList.contains(callMtd)) {
										callsMtdList.add(callMtd);
									}
									break;
								}
							}
						}else {
							callAlias = callClassAlias.get("ALIAS");
							// 클래스 호출알리아스+'.' 이후에 시작되는 메소드명 추출.(스페이스 이후에 키워드로 시작되거나 스페이스없이 키워드로 시작되는 라인이 있으면 검색)
							keyword =  callAlias+ "." ;
							if( line.indexOf(" " + keyword)>-1 || line.startsWith(keyword) ) {					
								callMtd = callPackageClassId + "." + StringUtil.nextWord(line, keyword, div);
							}
							// 클래스 호출알리아스Getter+'.' 이후에 시작되는 메소드명 추출.(스페이스 이후에 키워드로 시작되거나 스페이스없이 키워드로 시작되는 라인이 있으면 검색)
							keyword =  ParseUtil.getGetterNmFromField(callAlias)+ "." ;
							if( line.indexOf(" " + keyword)>-1 || line.startsWith(keyword) ) {					
								callMtd = callPackageClassId + "." + StringUtil.nextWord(line, keyword, div);
							}
							if( !StringUtil.isEmpty(callMtd) ) {
								if(!callsMtdList.contains(callMtd)) {
									callsMtdList.add(callMtd);
								}
								break;
							}
						}

					}
				}

			}
		}
		return callsMtdList;
	}

	/**
	 * 호출테이블 목록 추출
	 * 파일에서 매칭되는 queryKey를 찾아내어 해당 [쿼리정보.테이블목록]을 읽어온 후 [메소드정보.호출테이블]에 저장.
	 * @param analyzedMethodFile
	 * @return
	 */
	@Override
	public List<String> getCallTblList(String analyzedMethodFile) throws Exception  {
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
		
		if( !StringUtil.isEmpty(mtdVo.getMethodBody()) ) {
			mtdBody = mtdVo.getMethodBody();
			String[] lines = StringUtil.toStrArray(mtdBody, "\n");
			for(String line : lines) {
				keyword = "";
				for(String queryKey : queryKeyList) {
					/********************************************
					아래와 같이 queryKey => keyword 로 치환하는 작업.
					queryKey 	: net.dstone.sample.AdminDao_listUser
					keyword 	: "net.dstone.sample.AdminDao.listUser"
					********************************************/
					keyword = queryKey;
					if( keyword.indexOf("_")>-1 ) {
						keyword = keyword.substring(0, keyword.lastIndexOf("_")) + "." + keyword.substring(keyword.lastIndexOf("_")+1);
					}
					keyword = "\"" + keyword + "\"";
					if( line.indexOf(keyword) > -1 ) {
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

}

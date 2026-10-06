package net.dstone.boot.common.tools.analyzer.vo;

import java.util.List;

public class QueryVo {

	private String key;
	private String namespace;
	private String queryId;
	private String queryKind;
	private List<String> callTblList;
	protected String fileName;
	private String queryBody;

	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getNamespace() {
		return namespace;
	}
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}
	public String getQueryId() {
		return queryId;
	}
	public void setQueryId(String queryId) {
		this.queryId = queryId;
	}
	public String getQueryKind() {
		return queryKind;
	}
	public void setQueryKind(String queryKind) {
		this.queryKind = queryKind;
	}
	public String getQueryBody() {
		return queryBody;
	}
	public void setQueryBody(String queryBody) {
		this.queryBody = queryBody;
	}
	public List<String> getCallTblList() {
		return callTblList;
	}
	public void setCallTblList(List<String> callTblList) {
		this.callTblList = callTblList;
	}
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	@Override
	public String toString() {
		return "QueryVo [key=" + key + ", namespace=" + namespace + ", queryId=" + queryId + ", queryKind=" + queryKind
				+ ", callTblList=" + callTblList + ", fileName=" + fileName + ", queryBody=" + queryBody + "]";
	}

}

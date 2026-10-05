package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * 검색 요청 한 건의 조건입니다. 컨트롤러가 요청 본문을 여기에 옮겨 담아 SearchService에 넘깁니다.
 * </pre>
 */
public class SearchQuery {

	/** 찾을 내용(질문) */
	private String query;

	private String projectId;

	private Long revisionId;

	/** 어디에서 찾을지: CODE(분석 결과) / DOCUMENT(올린 일반 문서). 비어 있으면 프로젝트를 줬을 때 CODE, 안 줬을 때 DOCUMENT */
	private List<String> sourceTypes = new ArrayList<String>();

	/** FILE / TYPE / METHOD / MAPPER / VIEW / UPLOAD 가운데 찾을 것. 비어 있으면 전부 */
	private List<String> docTypes = new ArrayList<String>();

	/** 이 계층의 것만(CONTROLLER / SERVICE ...). 없으면 전부 */
	private String layer;

	/** HYBRID(기본. 뜻 + 이름) / VECTOR(뜻만) / KEYWORD(이름만) */
	private String mode;

	private int topK = 10;

	/** 호출자. 일반 문서는 이 호출자가 올린 것만 보인다. 인증을 꺼 두면 null */
	private String tenant;

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public Long getRevisionId() {
		return revisionId;
	}

	public void setRevisionId(Long revisionId) {
		this.revisionId = revisionId;
	}

	public List<String> getSourceTypes() {
		return sourceTypes;
	}

	public void setSourceTypes(List<String> sourceTypes) {
		this.sourceTypes = sourceTypes;
	}

	public List<String> getDocTypes() {
		return docTypes;
	}

	public void setDocTypes(List<String> docTypes) {
		this.docTypes = docTypes;
	}

	public String getLayer() {
		return layer;
	}

	public void setLayer(String layer) {
		this.layer = layer;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public int getTopK() {
		return topK;
	}

	public void setTopK(int topK) {
		this.topK = topK;
	}

	public String getTenant() {
		return tenant;
	}

	public void setTenant(String tenant) {
		this.tenant = tenant;
	}

}

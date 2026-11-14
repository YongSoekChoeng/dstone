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

	/** 찾을 프로젝트. 프로젝트나 리비전을 주면 코드에서, 둘 다 없으면 올린 일반 문서에서 찾는다 */
	private String projectId;

	/** 찾을 리비전. 없으면 그 프로젝트의 최신 READY 리비전 */
	private Long revisionId;

	/** 올린 문서를 찾을 때만: 이 묶음의 문서에서만. 비어 있으면 전부 */
	private List<String> collections = new ArrayList<String>();

	/** 올린 문서를 찾을 때만: 이 문서(sourceId)에서만. 비어 있으면 전부 */
	private List<String> documentIds = new ArrayList<String>();

	/** 코드를 찾을 때만: FILE / TYPE / METHOD / MAPPER / VIEW 가운데 찾을 것. 비어 있으면 전부 */
	private List<String> docTypes = new ArrayList<String>();

	/** 코드를 찾을 때만: 이 계층의 것만(CONTROLLER / SERVICE ...). 없으면 전부 */
	private String layer;

	/** HYBRID(기본. 뜻 + 이름) / VECTOR(뜻만) / KEYWORD(이름만) */
	private String mode;

	/** 돌려줄 최대 건수 */
	private int topK = 10;

	/** 뜻으로 찾은 결과를 남길 최소 유사도. null이면 설정값(코드 / 올린 문서 따로), 0이면 거르지 않는다 */
	private Double minScore;

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

	public List<String> getCollections() {
		return collections;
	}

	public void setCollections(List<String> collections) {
		this.collections = collections;
	}

	public List<String> getDocumentIds() {
		return documentIds;
	}

	public void setDocumentIds(List<String> documentIds) {
		this.documentIds = documentIds;
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

	public Double getMinScore() {
		return minScore;
	}

	public void setMinScore(Double minScore) {
		this.minScore = minScore;
	}

	public String getTenant() {
		return tenant;
	}

	public void setTenant(String tenant) {
		this.tenant = tenant;
	}

}

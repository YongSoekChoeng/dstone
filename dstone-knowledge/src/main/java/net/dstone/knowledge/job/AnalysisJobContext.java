package net.dstone.knowledge.job;

import java.util.Map;

import net.dstone.knowledge.semantic.rule.SemanticRules;

/**
 * <pre>
 * 분석 Job 하나가 도는 동안 각 단계가 함께 보는 정보입니다.
 * (어느 프로젝트의 어느 리비전을 분석 중인지, 취소 요청이 들어왔는지)
 * </pre>
 */
public class AnalysisJobContext {

	private final String analysisId;
	private final String projectId;
	private final long revisionId;

	/** analysis_project 한 행. 키는 localPath, sourceEncoding, javaVersion, rootPackages 등 */
	private final Map<String, Object> project;

	/** 다른 스레드(취소 API)가 바꾸고 분석 스레드가 읽기 때문에 volatile입니다. */
	private volatile boolean cancelRequested = false;

	/** SEMANTIC 단계가 시작할 때 읽은 의미 분석 규칙(conf/rules). 그 전에는 null */
	private SemanticRules semanticRules;

	public AnalysisJobContext(String analysisId, String projectId, long revisionId, Map<String, Object> project) {
		this.analysisId = analysisId;
		this.projectId = projectId;
		this.revisionId = revisionId;
		this.project = project;
	}

	public String getAnalysisId() {
		return analysisId;
	}

	public String getProjectId() {
		return projectId;
	}

	public long getRevisionId() {
		return revisionId;
	}

	/** 프로젝트 설정 값 하나를 꺼냅니다. 없거나 비어 있으면 null */
	public String getProjectValue(String key) {
		Object value = project.get(key);
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.length() == 0 ? null : text;
	}

	/** SEMANTIC 단계의 플러그인이 쓰는 규칙. SemanticPass 가 시작할 때 채웁니다 */
	public SemanticRules getSemanticRules() {
		if (semanticRules == null) {
			throw new IllegalStateException("의미 분석 규칙이 아직 없습니다. SemanticPass 를 거쳐 플러그인을 불러야 합니다.");
		}
		return semanticRules;
	}

	public void setSemanticRules(SemanticRules semanticRules) {
		this.semanticRules = semanticRules;
	}

	public void requestCancel() {
		this.cancelRequested = true;
	}

	public boolean isCancelRequested() {
		return cancelRequested;
	}

	/** 취소 요청이 들어와 있으면 JobCancelledException을 던져 단계를 멈춥니다. */
	public void checkCancelled() {
		if (cancelRequested) {
			throw new JobCancelledException();
		}
	}

}

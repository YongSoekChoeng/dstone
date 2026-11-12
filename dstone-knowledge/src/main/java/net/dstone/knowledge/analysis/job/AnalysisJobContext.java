package net.dstone.knowledge.analysis.job;

import java.util.Map;

import net.dstone.knowledge.analysis.pass08.semantic.rule.ProjectRules;

/**
 * <pre>
 * 분석 Job 하나가 도는 동안 각 단계가 함께 보는 정보입니다.
 * (어느 프로젝트의 어느 리비전을 분석 중인지, 취소 요청이 들어왔는지)
 * </pre>
 */
public class AnalysisJobContext {

	/** 이번 분석 Job의 ID (analysis_job.analysis_id) */
	private final String analysisId;
	/** 분석 중인 프로젝트 ID */
	private final String projectId;
	/** 이번 분석 결과가 쌓이는 리비전. 모든 analysis_* 행이 이 값으로 묶인다 */
	private final long revisionId;

	/** analysis_project 한 행. 키는 localPath, sourceEncoding, javaVersion, rootPackages 등 */
	private final Map<String, Object> project;

	/** 다른 스레드(취소 API)가 바꾸고 분석 스레드가 읽기 때문에 volatile입니다. */
	private volatile boolean cancelRequested = false;

	/** SEMANTIC 단계가 시작할 때 읽은 의미 분석 규칙(conf/rules). 그 전에는 null */
	private ProjectRules projectRules;

	/** @param project analysis_project 한 행(소스 경로, 인코딩, Java 버전 …) */
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

	/** SEMANTIC 단계의 처리기가 쓰는 이 프로젝트의 규칙(conf/rules). SemanticPass 가 시작할 때 채웁니다 */
	public ProjectRules getProjectRules() {
		if (projectRules == null) {
			throw new IllegalStateException("의미 분석 규칙이 아직 없습니다. SemanticPass 를 거쳐 처리기를 불러야 합니다.");
		}
		return projectRules;
	}

	public void setProjectRules(ProjectRules projectRules) {
		this.projectRules = projectRules;
	}

	/** 취소 API가 부릅니다. 플래그만 세우고, 실제로 멈추는 것은 단계가 checkCancelled()를 부를 때입니다. */
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

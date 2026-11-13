package net.dstone.knowledge.analysis.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.AnalysisJobDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.api.service.RetentionService;
import net.dstone.knowledge.common.util.ErrorText;

/**
 * <pre>
 * 분석 Job을 백그라운드에서 실행합니다.
 *
 * 하는 일은 단순합니다. 등록된 단계(AnalysisPass)를 순서대로 하나씩 돌립니다.
 * - 이 리비전에서 이미 끝난(DONE) 단계는 건너뜁니다. 그래서 죽거나 취소된 뒤 다시 시작하면 이어서 합니다.
 * - 단계가 예외를 던지면 Job은 FAILED, 취소 요청으로 멈추면 CANCELLED가 됩니다.
 * - 끝까지 갔는데 분석 오류(analysis_error)가 한 건이라도 있으면 DONE_WITH_WARNING입니다.
 * </pre>
 */
@Component
public class AnalysisJobRunner extends BaseObject {

	@Autowired
	@Qualifier("analysisJobExecutor")
	private ThreadPoolTaskExecutor analysisJobExecutor;

	/** 등록된 모든 단계. Spring이 AnalysisPass 구현 빈을 모아서 넣어 줍니다. */
	@Autowired
	private List<AnalysisPass> passes;

	@Autowired
	private AnalysisJobDao analysisJobDao;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private RetentionService retentionService;

	/** 지금 대기 중이거나 돌고 있는 Job들. 취소 요청을 전달할 때 여기서 찾습니다. */
	private final Map<String, AnalysisJobContext> activeJobs = new ConcurrentHashMap<String, AnalysisJobContext>();

	/**
	 * <pre>
	 * Job을 실행 대기열에 넣습니다. 바로 돌아오고, 실제 분석은 별도 스레드에서 돕니다.
	 * 동시에 돌 수 있는 수를 넘으면 차례가 올 때까지 READY 상태로 기다립니다.
	 * </pre>
	 */
	public void submit(final AnalysisJobContext context) {
		activeJobs.put(context.getAnalysisId(), context);
		try {
			analysisJobExecutor.execute(new Runnable() {
				@Override
				public void run() {
					// 분석은 저장 SQL을 수천~수만 번 보낸다. 그걸 다 로그로 남기면 로그가 SQL로 덮이고 느려지기도 해서,
					// 이 스레드에서는 SQL 로그를 끈다(log4j2.xml의 SUPPRESS_SQL_LOG 필터). 진행 상황은 단계별 로그로 본다.
					ThreadContext.put("SUPPRESS_SQL_LOG", "Y");
					try {
						runJob(context);
					} finally {
						ThreadContext.remove("SUPPRESS_SQL_LOG");
						activeJobs.remove(context.getAnalysisId());
					}
				}
			});
		} catch (RuntimeException e) {
			// 대기열이 꽉 차서 받아 주지 않은 경우
			activeJobs.remove(context.getAnalysisId());
			throw e;
		}
	}

	/**
	 * <pre>
	 * 취소를 요청합니다. 돌고 있는 단계가 다음 확인 지점에서 멈춥니다.
	 * </pre>
	 *
	 * @return 이 서버에서 대기 중이거나 돌고 있는 Job이면 true
	 */
	public boolean requestCancel(String analysisId) {
		AnalysisJobContext context = activeJobs.get(analysisId);
		if (context == null) {
			return false;
		}
		context.requestCancel();
		return true;
	}

	/**
	 * <pre>
	 * Job 하나를 처음부터 끝까지 돌립니다(작업 스레드에서).
	 *
	 * 흐름:
	 *   1) Job을 RUNNING, 리비전을 ANALYZING으로 바꾼다.
	 *   2) 단계를 order() 순서로 돈다. 이미 DONE인 단계는 건너뛴다.
	 *   3) 다 끝나면 오류 건수를 보고 DONE / DONE_WITH_WARNING으로 끝내고, 오래된 리비전을 정리한다.
	 *   - 취소되면 CANCELLED, 그 밖의 예외면 FAILED로 끝낸다.
	 * </pre>
	 */
	private void runJob(AnalysisJobContext context) {
		String analysisId = context.getAnalysisId();
		long revisionId = context.getRevisionId();
		String runningPass = null;
		try {
			// 대기하는 동안 취소된 경우
			context.checkCancelled();

			// 1) 시작 표시
			analysisJobDao.updateJobStart(analysisId);
			revisionDao.updateRevisionStatus(revisionId, "ANALYZING");
			info("분석 시작: analysisId=" + analysisId + ", projectId=" + context.getProjectId() + ", revisionId=" + revisionId);

			// 2) 단계를 순서대로 실행
			List<AnalysisPass> ordered = orderedPasses();
			for (int i = 0; i < ordered.size(); i++) {
				AnalysisPass pass = ordered.get(i);
				context.checkCancelled();

				if ("DONE".equals(revisionDao.selectRevisionPassStatus(revisionId, pass.name()))) {
					info("단계 건너뜀(이미 끝남): " + pass.name() + ", analysisId=" + analysisId);
					continue;
				}

				runningPass = pass.name();
				analysisJobDao.updateJobPass(analysisId, pass.name());
				revisionDao.startRevisionPass(revisionId, pass.name(), analysisId);
				long startedAt = System.currentTimeMillis();

				/**************************************************************************************************************************************
				
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
				  단계: ① SCAN
				  테이블(CRUD): 
					analysis_file C
				  기능: 소스 폴더의 파일을 모두 훑어 목록을 만들고 종류(Java, JSP, XML, properties 등)를 나눕니다. 이후 모든 단계가 이 목록을 기준으로 돕니다.
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
				  단계: ② CARRY(증분 분석만)
				  테이블(CRUD): 
					analysis_revision R
					analysis_file U (carried_from_file_id, 파싱 결과)
					analysis_symbol / analysis_method / analysis_field / analysis_annotation / analysis_reference 복사
					analysis_mapper / analysis_config / analysis_resource 복사
				  기능: 앞 리비전과 비교해 바뀌지 않은 파일의 선언과 자원 행을 그대로 옮겨 옵니다. 이 파일들은 다시 파싱하지 않습니다.
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
				  단계: ③ DECLARE
				  테이블(CRUD): 
					analysis_file R/U (parse_status, package_name 등)
					analysis_symbol / analysis_method / analysis_field / analysis_annotation D·C
					analysis_reference D·C
					analysis_mapper D·C (JAVA: Java 문자열 SQL)
				  기능: Java 파일과 JSP 안의 Java를 한 파일씩 파싱해서 클래스, 메소드, 필드, 애노테이션을 기록합니다. 호출이나 타입 참조는 대상을 풀지 않고 이름만 적어 둡니다.
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
				  단계: ④ RESOURCE
				  테이블(CRUD): 
					analysis_file R
					analysis_mapper D·C (MYBATIS, IBATIS, QUERY_XML)
					analysis_config D·C
					analysis_resource D·C (빈, 의존성, DDL)
				  기능: Java가 아닌 파일을 읽습니다. 대상은 MyBatis/iBATIS 매퍼 XML의 SQL, properties·YAML 설정, 빌드 파일 의존성, DDL입니다.
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
				  단계: ⑤ CARRY_RESOLVE(증분 분석만)
				  테이블(CRUD): 
					analysis_relation 복사
					analysis_reference U (status → PENDING)
				  기능: 앞 리비전에서 풀어 둔 관계 가운데 옮겨도 안전한 것만 복사합니다. 바뀐 파일의 영향을 받는 참조는 다시 풀도록 PENDING으로 되돌립니다.
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
				  단계: ⑥ RESOLVE
				  테이블(CRUD): 
					analysis_symbol / analysis_method / analysis_field / analysis_reference R
					analysis_reference U (status, fail_reason)
					analysis_relation D·C (CALLS 등 7종)
				  기능: Java 파일을 다시 파싱하고 DB의 선언 정보를 조회해서 ③에서 이름만 적어 둔 참조의 실제 대상을 찾습니다. 그 결과가 "누가 누구를 부르나" 관계가 됩니다.
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
				  단계: ⑦ LINK
				  테이블(CRUD):
					analysis_symbol / analysis_method R
					analysis_relation D·C (OVERRIDES, CALLS_POSSIBLE_IMPLEMENTATION)
					analysis_metric D·C
				  기능: 파싱 없이 SQL만으로 재정의 관계와 인터페이스→구현 관계를 잇고, 품질 지표를 계산합니다.
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
				  단계: ⑧ SEMANTIC
				  테이블(CRUD): 
					analysis_* 전반 R (+ 규칙 파일 conf/rules, web.xml, Struts 설정)
					analysis_symbol U (layer, layer_confidence, properties_json)
					analysis_method U (proxy_related)
					analysis_endpoint D·C/U (method_id)
					analysis_relation D·C (INJECTS, EXECUTES_SQL, READS_TABLE, WRITES_TABLE, RENDERS, REQUESTS, INCLUDES)
					analysis_mapper D·C (JPA), U (method_id)
					analysis_resource D·C (매핑, 화면명, 기능명, 이름 사전, 규칙 기록)
				  기능: 규칙과 처리기(LayerResolver, EntryFinder, SqlLinker 등)로 의미를 붙입니다. 계층을 정하고, 진입점(URL·서블릿·JSP·거래)을 찾고, 주입 관계와 SQL·테이블·화면 연결을 만듭니다.
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
				  단계: ⑨ DOCUMENT
				  테이블(CRUD): 
					analysis_* 전반 R (+ 소스의 해당 줄)
					rag_document, rag_chunk D·C
					rag_embedding C (새 content_hash만, 대기열)
				  기능: 분석 결과를 파일 단위 설명 문서로 풀어 쓰고 청크로 자릅니다. 임베딩 대기열에도 올리며, 실제 임베딩 계산은 Job과 별도로 도는 EmbeddingWorker가 맡습니다.
				  ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
					 
				**************************************************************************************************************************************/
				
				pass.run(context);

				revisionDao.endRevisionPass(revisionId, pass.name(), "DONE");
				runningPass = null;
				info("단계 끝: " + pass.name() + ", analysisId=" + analysisId + ", " + (System.currentTimeMillis() - startedAt) + "ms");
			}

			// 3) 마무리. 오류가 한 건이라도 있었으면 경고 상태로 끝낸다.
			String status = analysisJobDao.countError(analysisId) > 0 ? "DONE_WITH_WARNING" : "DONE";
			analysisJobDao.updateJobEnd(analysisId, status, null);
			revisionDao.updateRevisionStatus(revisionId, "READY");
			info("분석 끝: analysisId=" + analysisId + ", status=" + status);
			// 새 리비전이 생겼으니 오래된 리비전을 정리한다. 실패해도 분석 결과에는 영향이 없다.
			retentionService.applyQuietly(context.getProjectId());

		} catch (JobCancelledException e) {
			// 취소: 하던 단계는 끝나지 않은 것으로 남긴다. 같은 리비전으로 다시 시작하면 그 단계부터 한다.
			endQuietly(context, runningPass, "CANCELLED", "CANCELLED", e.getMessage(), "CREATED");
			info("분석 취소: analysisId=" + analysisId);
		} catch (Throwable t) {
			error("분석 실패: analysisId=" + analysisId + "\n" + ErrorText.stackTraceOf(t));
			endQuietly(context, runningPass, "FAILED", "FAILED", ErrorText.summaryOf(t), "FAILED");
		}
	}

	/**
	 * <pre>
	 * Job을 끝난 상태로 기록합니다.
	 * 실패 원인이 DB 장애라면 이 기록마저 실패할 수 있습니다. 그때는 로그만 남깁니다.
	 * (그렇게 RUNNING으로 남은 Job은 다음 기동 때 AnalysisJobRecovery가 정리합니다.)
	 * </pre>
	 */
	private void endQuietly(AnalysisJobContext context, String runningPass, String passStatus, String jobStatus, String message, String revisionStatus) {
		try {
			if (runningPass != null) {
				revisionDao.endRevisionPass(context.getRevisionId(), runningPass, passStatus);
			}
			analysisJobDao.updateJobEnd(context.getAnalysisId(), jobStatus, message);
			revisionDao.updateRevisionStatus(context.getRevisionId(), revisionStatus);
		} catch (Throwable t) {
			error("Job 종료 상태를 기록하지 못했습니다: analysisId=" + context.getAnalysisId() + "\n" + ErrorText.stackTraceOf(t));
		}
	}

	/**
	 * <pre>
	 * 그 단계와 그 뒤에 도는 단계의 이름을 실행 순서대로 돌려줍니다. "이 단계부터 다시" 할 때 무엇을 되돌릴지 정하는 데 씁니다.
	 * </pre>
	 *
	 * @return 없는 단계 이름이면 빈 목록
	 */
	public List<String> passNamesFrom(String passName) {
		List<String> names = new ArrayList<String>();
		List<AnalysisPass> ordered = orderedPasses();
		for (int i = 0; i < ordered.size(); i++) {
			if (!names.isEmpty() || ordered.get(i).name().equalsIgnoreCase(passName)) {
				names.add(ordered.get(i).name());
			}
		}
		return names;
	}

	/** 모든 단계의 이름(실행 순서대로) */
	public List<String> passNames() {
		List<String> names = new ArrayList<String>();
		List<AnalysisPass> ordered = orderedPasses();
		for (int i = 0; i < ordered.size(); i++) {
			names.add(ordered.get(i).name());
		}
		return names;
	}

	/** 등록된 단계를 order() 오름차순으로 정렬한 사본 */
	private List<AnalysisPass> orderedPasses() {
		List<AnalysisPass> ordered = new ArrayList<AnalysisPass>(passes);
		Collections.sort(ordered, new Comparator<AnalysisPass>() {
			@Override
			public int compare(AnalysisPass a, AnalysisPass b) {
				return Integer.compare(a.order(), b.order());
			}
		});
		return ordered;
	}

}

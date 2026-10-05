package net.dstone.knowledge.api.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.AnalysisFileDao;
import net.dstone.knowledge.api.dao.AnalysisJobDao;
import net.dstone.knowledge.api.dao.DeclarationDao;
import net.dstone.knowledge.api.dao.FilePassDao;
import net.dstone.knowledge.api.dao.RelationDao;
import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 리비전(어느 시점의 소스를 분석한 결과 한 벌)을 조회하고 지웁니다.
 * </pre>
 */
@Service
public class RevisionService extends BaseObject {

	private static final int MAX_PAGE_SIZE = 500;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private AnalysisJobDao analysisJobDao;

	@Autowired
	private AnalysisFileDao analysisFileDao;

	@Autowired
	private DeclarationDao declarationDao;

	@Autowired
	private FilePassDao filePassDao;

	@Autowired
	private RelationDao relationDao;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	public List<Map<String, Object>> getRevisionList(String projectId) {
		projectService.getProject(projectId);
		return revisionDao.selectRevisionList(projectId);
	}

	/**
	 * <pre>
	 * 리비전 하나를 조회합니다.
	 * 단계별 진행 상태, 이 리비전을 돌린 Job들, 스캔한 파일의 요약(종류별/인코딩별/소스 루트별)을 같이 돌려줍니다.
	 * </pre>
	 */
	public Map<String, Object> getRevision(long revisionId) {
		Map<String, Object> result = new LinkedHashMap<String, Object>(findRevision(revisionId));
		result.put("passes", revisionDao.selectRevisionPassList(revisionId));
		result.put("jobs", analysisJobDao.selectJobListByRevision(revisionId));

		Map<String, Object> files = new LinkedHashMap<String, Object>();
		files.put("total", analysisFileDao.countFile(condition(revisionId, null, null, null, null)));
		files.put("byType", analysisFileDao.selectSummaryByType(revisionId));
		files.put("byEncoding", analysisFileDao.selectSummaryByEncoding(revisionId));
		files.put("javaSourceRoots", analysisFileDao.selectSummaryBySourceRoot(revisionId));
		result.put("files", files);

		// DECLARE 단계의 결과. 아직 돌리지 않았으면 전부 0이거나 비어 있다.
		Map<String, Object> declarations = new LinkedHashMap<String, Object>();
		declarations.put("javaFiles", declarationDao.selectParseSummary(revisionId));
		declarations.put("filePasses", filePassDao.selectFilePassSummary(revisionId));
		declarations.put("types", declarationDao.selectTypeSummary(revisionId));
		declarations.put("members", declarationDao.selectMemberSummary(revisionId));
		declarations.put("synthetic", declarationDao.selectSyntheticSummary(revisionId));
		declarations.put("references", declarationDao.selectReferenceSummary(revisionId));
		result.put("declarations", declarations);

		// RESOLVE / LINK 단계의 결과
		Map<String, Object> relations = new LinkedHashMap<String, Object>();
		relations.put("byType", relationDao.selectRelationSummary(revisionId));
		relations.put("unresolvedReasons", relationDao.selectUnresolvedReasons(revisionId));
		relations.put("metrics", relationDao.selectMetricsByRevision(revisionId));
		result.put("relations", relations);

		// SEMANTIC 단계의 결과
		Map<String, Object> semantic = new LinkedHashMap<String, Object>();
		semantic.put("endpoints", semanticDao.selectEndpointSummary(revisionId));
		semantic.put("layers", semanticDao.selectLayerSummary(revisionId));
		// RESOURCE 단계가 읽은 것: SQL statement, 설정 값, Spring 빈, 의존성 ...
		semantic.put("resources", resourceDao.selectResourceSummary(revisionId));
		result.put("semantic", semantic);
		return result;
	}

	/**
	 * <pre>
	 * 스캔한 파일 목록을 조회합니다.
	 * </pre>
	 *
	 * @param page 1부터 시작
	 */
	public Map<String, Object> getFileList(long revisionId, String language, String fileType, String encoding, String pathLike, int page, int size) {
		findRevision(revisionId);
		int pageNo = page < 1 ? 1 : page;
		int pageSize = size < 1 ? 50 : Math.min(size, MAX_PAGE_SIZE);

		Map<String, Object> condition = condition(revisionId, language, fileType, encoding, pathLike);
		condition.put("size", pageSize);
		condition.put("offset", (pageNo - 1) * pageSize);

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("total", analysisFileDao.countFile(condition));
		result.put("page", pageNo);
		result.put("size", pageSize);
		result.put("files", analysisFileDao.selectFileList(condition));
		return result;
	}

	/**
	 * <pre>
	 * 진입점 목록. 밖에서 이 프로그램으로 들어오는 입구들입니다.
	 * </pre>
	 *
	 * @param endpointType HTTP / SERVLET / MAIN / SCHEDULED / LISTENER / THREAD / JSP (없으면 전부)
	 * @param path 주소에 이 글자가 들어간 것만 (없으면 전부)
	 * @param page 1부터 시작
	 */
	public Map<String, Object> getEndpointList(long revisionId, String endpointType, String path, int page, int size) {
		findRevision(revisionId);
		int pageNo = page < 1 ? 1 : page;
		int pageSize = size < 1 ? 50 : Math.min(size, MAX_PAGE_SIZE);

		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("endpointType", endpointType);
		condition.put("path", path);
		condition.put("size", pageSize);
		condition.put("offset", (pageNo - 1) * pageSize);

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("total", semanticDao.countEndpoint(condition));
		result.put("page", pageNo);
		result.put("size", pageSize);
		result.put("endpoints", semanticDao.selectEndpointList(condition));
		return result;
	}

	/**
	 * <pre>
	 * 리비전과 거기에 딸린 분석 결과를 모두 지웁니다. 되돌릴 수 없습니다.
	 * 분석이 돌고 있는 리비전은 지울 수 없습니다(먼저 취소해야 합니다).
	 * </pre>
	 */
	public void deleteRevision(final long revisionId) {
		findRevision(revisionId);
		if (analysisJobDao.countActiveJobByRevision(revisionId) > 0) {
			throw ApiException.conflict("분석이 돌고 있는 리비전은 지울 수 없습니다. 먼저 분석을 취소하세요.");
		}
		txTemplateCommon.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				revisionDao.deleteRevisionData(revisionId);
			}
		});
		info("리비전 삭제: revisionId=" + revisionId);
	}

	private Map<String, Object> findRevision(long revisionId) {
		Map<String, Object> revision = revisionDao.selectRevision(revisionId);
		if (revision == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		return revision;
	}

	private Map<String, Object> condition(long revisionId, String language, String fileType, String encoding, String pathLike) {
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("language", language);
		condition.put("fileType", fileType);
		condition.put("encoding", encoding);
		condition.put("pathLike", pathLike);
		return condition;
	}

}

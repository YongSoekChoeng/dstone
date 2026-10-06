package net.dstone.ai.api.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import net.dstone.ai.api.dto.DocumentSourceSummary;
import net.dstone.ai.api.dto.IngestResponse;
import net.dstone.ai.common.knowledge.KnowledgeCallException;
import net.dstone.ai.common.knowledge.KnowledgeClient;
import net.dstone.ai.common.rag.RagSourceId;
import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * RAG가 검색할 문서를 올리고(적재), 지우고, 목록을 봅니다.
 *
 * 문서를 이 엔진이 직접 임베딩해서 저장하지 않습니다. dstone-knowledge에 맡깁니다(POST / GET / DELETE /api/documents).
 * 글자 뽑기(PDF, Word 등), 조각 내기, 임베딩, 벡터 저장은 모두 dstone-knowledge가 합니다.
 * 그래서 이 엔진에는 임베딩 모델도 벡터 저장소(pgvector)도 필요 없습니다.
 *
 * 알아 둘 점:
 *   - 올리면 조각 수가 바로 돌아오지만, 임베딩은 dstone-knowledge가 뒤에서 따로 합니다(보통 수 초 ~ 수십 초).
 *     그 전에는 뜻으로 찾는 검색에 나오지 않고, 이름(영문 낱말)으로 찾는 검색에만 나옵니다.
 *   - 같은 sourceId로 다시 올리면 바꿔 넣습니다. 글자를 뽑지 못한 파일은 오류이고 기존 문서는 그대로 남습니다.
 *   - caller(이 엔진을 부른 앱)별로 문서를 가리는 일은 이 엔진이 합니다(common.rag.RagSourceId 참고).
 *
 * 실제로 검색하는 쪽은 common.rag.RagRetrievalChain입니다.
 * </pre>
 */
@Service
public class EmbedService extends BaseService {

	private static final String DOCUMENTS_PATH = "/api/documents";

	/** 목록을 가져올 때 한 번에 받는 건수 */
	private static final int LIST_PAGE_SIZE = 200;

	/** 목록을 끝없이 넘기지 않게 두는 상한(200건 × 50쪽 = 1만 건) */
	private static final int LIST_MAX_PAGES = 50;

	@Autowired
	private KnowledgeClient knowledgeClient;
	@Autowired
	private ConfigProperty configProperty;

	/**
	 * RAG 관련 기능을 쓰기 전에 항상 거치는 관문입니다. dstone.ai.rag.enabled=true로 켜져 있고
	 * dstone-knowledge의 주소가 설정돼 있을 때만 통과시키고, 그렇지 않으면 바로 예외를 던집니다.
	 */
	private void requireRag() {
		if (!Boolean.parseBoolean(this.configProperty.getProperty("dstone.ai.rag.enabled"))) {
			throw new IllegalStateException("RAG가 비활성화되어 있습니다(dstone.ai.rag.enabled=false 또는 미설정).");
		}
		if (!this.knowledgeClient.isConfigured()) {
			throw new IllegalStateException("dstone.ai.rag.enabled=true인데 dstone-knowledge 주소가 없습니다. dstone.ai.tool.knowledge.base-url 설정을 확인하십시오.");
		}
	}

	/**
	 * <pre>
	 * 문서 하나를 올립니다. 같은 sourceId로 이미 올린 문서가 있으면 바꿔 넣습니다.
	 * </pre>
	 *
	 * @param resource 올릴 원문 파일입니다. 파일 이름이 있어야 dstone-knowledge가 파일 종류를 알 수 있습니다.
	 * @param sourceId 문서를 가리키는 이름입니다. 다시 올릴 때 "같은 문서"인지 판단하는 기준입니다.
	 * @param caller   이 문서를 올리는 앱이나 서비스(tenant)입니다. 없으면 누구에게나 보이는 문서가 됩니다.
	 * @return 문서 이름과, 나뉜 조각 수
	 */
	public IngestResponse ingest(Resource resource, String sourceId, String caller) {
		if (StringUtil.isEmpty(sourceId)) {
			throw new IllegalArgumentException("sourceId는 필수입니다(재적재 시 upsert 기준 키로 쓰임).");
		}
		this.requireRag();
		Map<String, String> fields = new LinkedHashMap<String, String>();
		fields.put("sourceId", RagSourceId.stored(caller, sourceId));
		// 검색 결과에 보일 제목에는 caller를 붙이지 않은 원래 이름을 쓴다.
		fields.put("title", sourceId);
		try {
			JsonNode result = this.knowledgeClient.upload(DOCUMENTS_PATH, resource, fields);
			return new IngestResponse(sourceId, result.path("chunks").asInt(0));
		} catch (KnowledgeCallException e) {
			throw this.failure("문서를 올리지 못했습니다", e);
		}
	}

	/**
	 * <pre>
	 * 문서 하나를 지웁니다. 다른 caller가 같은 sourceId를 썼더라도 서로의 문서는 지워지지 않습니다.
	 * 올린 적이 없는 문서면 아무 일도 하지 않습니다.
	 * </pre>
	 *
	 * @param sourceId 지울 문서의 이름입니다.
	 * @param caller   이 요청을 보낸 앱이나 서비스(tenant)입니다.
	 */
	public void deleteBySourceId(String sourceId, String caller) {
		this.requireRag();
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("sourceId", RagSourceId.stored(caller, sourceId));
		try {
			this.knowledgeClient.delete(DOCUMENTS_PATH, params);
		} catch (KnowledgeCallException e) {
			if (e.status() == 404) {
				return;
			}
			throw this.failure("문서를 지우지 못했습니다", e);
		}
	}

	/**
	 * <pre>
	 * 지금 올라가 있는 문서와 문서마다의 조각 수를 돌려줍니다.
	 * </pre>
	 *
	 * @param caller 이 caller가 올린 문서만 봅니다. 비워 두면 전부 돌려줍니다.
	 */
	public List<DocumentSourceSummary> listSources(String caller) {
		this.requireRag();
		List<DocumentSourceSummary> sources = new ArrayList<DocumentSourceSummary>();
		try {
			for (int page = 1; page <= LIST_MAX_PAGES; page++) {
				Map<String, String> params = new LinkedHashMap<String, String>();
				params.put("page", String.valueOf(page));
				params.put("size", String.valueOf(LIST_PAGE_SIZE));
				JsonNode result = this.knowledgeClient.get(DOCUMENTS_PATH, params);
				JsonNode documents = result.path("documents");
				for (int i = 0; i < documents.size(); i++) {
					String storedId = documents.get(i).path("sourceId").asText();
					if (RagSourceId.visibleTo(caller, storedId)) {
						sources.add(new DocumentSourceSummary(RagSourceId.shown(caller, storedId), RagSourceId.tenantOf(storedId), documents.get(i).path("chunks").asLong(0)));
					}
				}
				if (documents.size() == 0 || page * LIST_PAGE_SIZE >= result.path("total").asInt(0)) {
					break;
				}
			}
		} catch (KnowledgeCallException e) {
			throw this.failure("문서 목록을 가져오지 못했습니다", e);
		}
		return sources;
	}

	/** dstone-knowledge가 "요청이 잘못됐다"(400)고 한 것은 호출한 쪽의 잘못으로, 나머지는 서버 쪽 문제로 알립니다. */
	private RuntimeException failure(String what, KnowledgeCallException e) {
		String message = what + ": " + e.reason();
		return e.status() == 400 ? new IllegalArgumentException(message) : new IllegalStateException(message);
	}

}

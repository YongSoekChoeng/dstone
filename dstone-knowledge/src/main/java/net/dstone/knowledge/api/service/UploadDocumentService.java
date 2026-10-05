package net.dstone.knowledge.api.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RagDao;
import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.common.util.HashText;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.rag.DocumentTextExtractor;
import net.dstone.knowledge.rag.RagSettings;
import net.dstone.knowledge.rag.TextChunker;

/**
 * <pre>
 * 일반 문서(설계서, 운영 매뉴얼, 회의록 같은 PDF / Word / 텍스트)를 올리고, 목록을 보고, 지웁니다.
 *
 * 올린 문서는 코드 분석 결과와 같은 곳(rag_document / rag_chunk / rag_embedding)에 들어가고 같은 검색으로 찾습니다.
 * 다른 점은 세 가지입니다.
 *   - 리비전이 없다. 분석과 상관없이 올리고 지운다. 프로젝트에 붙여 올릴 수는 있다(projectId).
 *   - 호출자(tenant)별로 나뉜다. 다른 호출자가 올린 문서는 목록에도 검색에도 나오지 않는다.
 *   - 문서 이름(sourceId)이 열쇠다. 같은 호출자가 같은 이름으로 다시 올리면 앞의 것을 바꿔 넣는다.
 *
 * 올리면 바로 돌아옵니다. 임베딩은 뒤에서 진행되고(EmbeddingWorker), 코드보다 먼저 처리됩니다.
 * 임베딩이 끝나기 전에도 이름으로 찾는 검색에는 나옵니다.
 * </pre>
 */
@Service
public class UploadDocumentService extends BaseObject {

	private static final int MAX_PAGE_SIZE = 200;

	@Autowired
	private RagDao ragDao;

	@Autowired
	private RagSettings ragSettings;

	@Autowired
	private DocumentTextExtractor documentTextExtractor;

	@Autowired
	private ProjectService projectService;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/**
	 * <pre>
	 * 문서 하나를 올립니다.
	 * 글을 한 글자도 뽑지 못하면(스캔한 PDF, 빈 파일) 400이고, 같은 이름으로 이미 올린 문서가 있어도 그대로 둡니다.
	 * 잘못 올린 파일 때문에 멀쩡하던 문서가 사라지면 안 되기 때문입니다.
	 * </pre>
	 *
	 * @param fileName 올린 파일의 이름
	 * @param sourceId 문서 이름(열쇠). 없으면 파일 이름
	 * @param title 검색 결과에 보일 제목. 없으면 파일 이름
	 * @param projectId 이 문서를 붙일 프로젝트. 없어도 됨
	 * @param tenant 호출자. 인증을 꺼 두었으면 null
	 */
	public Map<String, Object> upload(InputStream input, String fileName, String sourceId, String title, String projectId, final String tenant) {
		String name = fileName == null || fileName.trim().length() == 0 ? null : fileName.trim();
		final String id = textOr(sourceId, name);
		if (id == null) {
			throw ApiException.badRequest("sourceId(문서 이름)나 파일 이름이 있어야 합니다.");
		}
		if (id.length() > 200) {
			throw ApiException.badRequest("sourceId는 200자 이내여야 합니다.");
		}
		String project = textOr(projectId, null);
		if (project != null) {
			// 없는 프로젝트면 404
			projectService.getProject(project);
		}

		DocumentTextExtractor.Extracted extracted;
		try {
			extracted = documentTextExtractor.extract(input, name, ragSettings.uploadMaxChars());
		} catch (IOException e) {
			throw ApiException.badRequest("파일을 읽지 못했습니다: " + ErrorText.summaryOf(e));
		} catch (TikaException e) {
			throw ApiException.badRequest("문서에서 글을 뽑지 못했습니다(깨진 파일이거나 암호가 걸려 있을 수 있습니다): " + ErrorText.summaryOf(e));
		}

		// JSONL은 한 줄이 한 건이다. 나누거나 합치지 않는다.
		boolean lineBased = name != null && name.toLowerCase().endsWith(".jsonl");
		List<String> parts = lineBased ? TextChunker.lines(extracted.text)
				: TextChunker.split(extracted.text, ragSettings.chunkMaxChars(), ragSettings.uploadOverlapChars());
		if (parts.isEmpty()) {
			throw ApiException.badRequest("문서에서 뽑은 글이 없습니다. 스캔한 PDF처럼 글자가 그림으로만 들어 있으면 읽지 못합니다. 이미 올린 문서는 그대로 두었습니다.");
		}

		String documentTitle = textOr(title, textOr(name, id));
		int chars = 0;
		final List<Map<String, Object>> chunks = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < parts.size(); i++) {
			// 청크만 따로 읽어도 어느 문서의 몇 번째 조각인지 알 수 있게 첫 줄에 적는다.
			String content = "[문서] " + documentTitle + " (" + (i + 1) + "/" + parts.size() + ")\n" + parts.get(i);
			Map<String, Object> chunk = new HashMap<String, Object>();
			chunk.put("chunkNo", Integer.valueOf(i));
			chunk.put("content", content);
			chunk.put("contentHash", HashText.sha256(content));
			chunk.put("charCount", Integer.valueOf(content.length()));
			chunks.add(chunk);
			chars += parts.get(i).length();
		}

		Map<String, Object> metadata = new LinkedHashMap<String, Object>();
		metadata.put("docType", "UPLOAD");
		metadata.put("sourceId", id);
		metadata.put("fileName", name);
		metadata.put("contentType", extracted.contentType);
		metadata.put("chars", Integer.valueOf(chars));
		metadata.put("truncated", Boolean.valueOf(extracted.truncated));

		final Map<String, Object> document = new HashMap<String, Object>();
		document.put("sourceId", id);
		document.put("tenant", tenant);
		document.put("projectId", project);
		document.put("title", documentTitle);
		document.put("fileName", name);
		document.put("metadataJson", JsonText.of(metadata));
		document.put("contentHash", HashText.sha256(extracted.text));

		// 앞의 것을 지우고 새것을 넣는 일을 한 트랜잭션으로 묶는다. 도중에 실패하면 앞의 것이 그대로 남는다.
		Boolean replaced = txTemplateCommon.execute(new TransactionCallback<Boolean>() {
			@Override
			public Boolean doInTransaction(TransactionStatus status) {
				Long existing = ragDao.selectUploadDocSeq(ownerOf(tenant, id));
				if (existing != null) {
					ragDao.deleteUpload(existing.longValue());
				}
				ragDao.insertUpload(document, chunks, ragSettings.embeddingModel(), ragSettings.embeddingDimensions(), ragSettings.uploadEmbeddingPriority());
				return Boolean.valueOf(existing != null);
			}
		});

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("sourceId", id);
		result.put("title", documentTitle);
		result.put("projectId", project);
		result.put("tenant", tenant);
		result.put("contentType", extracted.contentType);
		result.put("chars", Integer.valueOf(chars));
		result.put("chunks", Integer.valueOf(chunks.size()));
		// 글자 수 상한에 걸려 뒷부분을 버렸는지
		result.put("truncated", Boolean.valueOf(extracted.truncated));
		// 같은 이름의 문서를 바꿔 넣었는지
		result.put("replaced", replaced);
		return result;
	}

	/**
	 * @param projectId 이 프로젝트에 붙인 문서만. 없으면 호출자의 문서 전부
	 */
	public Map<String, Object> getDocumentList(String tenant, String projectId, int page, int size) {
		int pageNo = page < 1 ? 1 : page;
		int pageSize = size < 1 ? 50 : Math.min(size, MAX_PAGE_SIZE);
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("tenant", tenant);
		condition.put("projectId", textOr(projectId, null));
		condition.put("model", ragSettings.embeddingModel());
		condition.put("size", Integer.valueOf(pageSize));
		condition.put("offset", Integer.valueOf((pageNo - 1) * pageSize));

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("total", Integer.valueOf(ragDao.countUploadDocuments(condition)));
		result.put("page", Integer.valueOf(pageNo));
		result.put("size", Integer.valueOf(pageSize));
		result.put("documents", ragDao.selectUploadDocuments(condition));
		return result;
	}

	/** 문서 하나를 지웁니다. 다른 호출자의 문서는 같은 이름이어도 지워지지 않습니다. */
	public Map<String, Object> delete(String tenant, String sourceId) {
		String id = textOr(sourceId, null);
		if (id == null) {
			throw ApiException.badRequest("sourceId(문서 이름)는 필수입니다.");
		}
		Long docSeq = ragDao.selectUploadDocSeq(ownerOf(tenant, id));
		if (docSeq == null) {
			throw ApiException.notFound("올린 적이 없는 문서입니다: " + id);
		}
		final long target = docSeq.longValue();
		txTemplateCommon.execute(new TransactionCallback<Object>() {
			@Override
			public Object doInTransaction(TransactionStatus status) {
				ragDao.deleteUpload(target);
				return null;
			}
		});
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("sourceId", id);
		result.put("deleted", Boolean.TRUE);
		return result;
	}

	private Map<String, Object> ownerOf(String tenant, String sourceId) {
		Map<String, Object> owner = new HashMap<String, Object>();
		owner.put("tenant", tenant);
		owner.put("sourceId", sourceId);
		return owner;
	}

	private String textOr(String value, String defaultValue) {
		return value == null || value.trim().length() == 0 ? defaultValue : value.trim();
	}

}

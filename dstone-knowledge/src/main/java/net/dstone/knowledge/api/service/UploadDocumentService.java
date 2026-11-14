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
import net.dstone.knowledge.rag.DocumentBlock;
import net.dstone.knowledge.rag.DocumentChunker;
import net.dstone.knowledge.rag.DocumentTextExtractor;
import net.dstone.knowledge.rag.RagSettings;
import net.dstone.knowledge.rag.TextChunker;

/**
 * <pre>
 * 일반 문서(설계서, 운영 매뉴얼, 개발 가이드 같은 PDF / Word / 텍스트)를 올리고, 목록을 보고, 지웁니다.
 *
 * 올린 문서는 코드 분석 결과와 같은 곳(rag_document / rag_chunk / rag_embedding)에 들어가지만 검색은 따로 합니다(/api/search에서 프로젝트를 주지 않을 때).
 * 코드와 다른 점은 이렇습니다.
 *   - 리비전도 프로젝트도 없다. 분석과 상관없이 올리고 지운다.
 *   - 묶음(collection)으로 묶을 수 있다. 예: nexacro, websquare. 검색과 목록에서 묶음으로 범위를 좁힌다.
 *   - 호출자(tenant)별로 나뉜다. 다른 호출자가 올린 문서는 목록에도 검색에도 나오지 않는다.
 *   - 문서 이름(sourceId)이 열쇠다. 같은 호출자가 같은 이름으로 다시 올리면 앞의 것을 바꿔 넣는다.
 *
 * 청크는 문서의 장/절을 살려 나눕니다(DocumentChunker). 청크마다 제목 경로(예: 2. 엔진 설치 > 2.2 설정)와 PDF 쪽을 덧붙여 둡니다.
 * 검색으로 찾은 조각의 앞뒤는 getChunks()로 이어 읽습니다.
 *
 * 올리면 바로 돌아옵니다. 임베딩은 뒤에서 진행되고(EmbeddingWorker), 코드보다 먼저 처리됩니다.
 * 임베딩이 끝나기 전에도 이름으로 찾는 검색에는 나옵니다.
 * </pre>
 */
@Service
public class UploadDocumentService extends BaseObject {

	private static final int MAX_PAGE_SIZE = 200;

	/** getChunks()가 한 번에 돌려주는 최대 청크 수 */
	private static final int MAX_CHUNK_RANGE = 10;

	/** 묶음 이름의 최대 길이(rag_document.collection) */
	private static final int MAX_COLLECTION_LENGTH = 100;

	@Autowired
	private RagDao ragDao;

	@Autowired
	private RagSettings ragSettings;

	@Autowired
	private DocumentTextExtractor documentTextExtractor;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/**
	 * <pre>
	 * 문서 하나를 올립니다.
	 * 글을 한 글자도 뽑지 못하면(스캔한 PDF, 빈 파일) 400이고, 같은 이름으로 이미 올린 문서가 있어도 그대로 둡니다.
	 * 잘못 올린 파일 때문에 멀쩡하던 문서가 사라지면 안 되기 때문입니다.
	 *
	 * 흐름:
	 *   1) 파일에서 글과 모양(제목, 문단, 표의 행, PDF 북마크)을 뽑는다(Tika, PDFBox). 글이 없으면 400.
	 *   2) 청크로 나눈다. JSONL 은 한 줄이 한 청크, 그 밖은 장/절 단위로(DocumentChunker).
	 *   3) 청크 첫 줄에 "어느 문서의 몇 번째 조각"인지, 둘째 줄에 제목 경로와 쪽을 적는다. 청크만 따로 읽어도 위치를 알 수 있게.
	 *   4) 같은 이름의 옛 문서를 지우고 새것을 넣는 일을 한 트랜잭션으로 한다. 청크는 임베딩 대기열에 오른다.
	 * </pre>
	 *
	 * @param fileName 올린 파일의 이름
	 * @param sourceId 문서 이름(열쇠). 없으면 파일 이름
	 * @param title 검색 결과에 보일 제목. 없으면 파일 이름
	 * @param collection 묶음 이름. 없어도 됨
	 * @param tenant 호출자. 인증을 꺼 두었으면 null
	 */
	public Map<String, Object> upload(InputStream input, String fileName, String sourceId, String title, String collection, final String tenant) {
		String name = fileName == null || fileName.trim().length() == 0 ? null : fileName.trim();
		final String id = textOr(sourceId, name);
		if (id == null) {
			throw ApiException.badRequest("sourceId(문서 이름)나 파일 이름이 있어야 합니다.");
		}
		if (id.length() > 200) {
			throw ApiException.badRequest("sourceId는 200자 이내여야 합니다.");
		}
		String group = collectionOf(collection);

		DocumentTextExtractor.Extracted extracted;
		try {
			extracted = documentTextExtractor.extract(input, name, ragSettings.uploadMaxChars());
		} catch (IOException e) {
			throw ApiException.badRequest("파일을 읽지 못했습니다: " + ErrorText.summaryOf(e));
		} catch (TikaException e) {
			throw ApiException.badRequest("문서에서 글을 뽑지 못했습니다(깨진 파일이거나 암호가 걸려 있을 수 있습니다): " + ErrorText.summaryOf(e));
		}

		List<DocumentChunker.Chunk> parts;
		boolean lineBased = name != null && name.toLowerCase().endsWith(".jsonl");
		if (lineBased) {
			// JSONL은 한 줄이 한 건이다. 나누거나 합치지 않는다.
			parts = new ArrayList<DocumentChunker.Chunk>();
			List<String> lines = TextChunker.lines(extracted.text);
			for (int i = 0; i < lines.size(); i++) {
				parts.add(DocumentChunker.plain(lines.get(i)));
			}
		} else {
			parts = DocumentChunker.chunk(extracted.blocks, extracted.bookmarks, ragSettings.uploadChunkMaxChars(), ragSettings.uploadOverlapChars());
		}
		if (parts.isEmpty()) {
			throw ApiException.badRequest("문서에서 뽑은 글이 없습니다. 스캔한 PDF처럼 글자가 그림으로만 들어 있으면 읽지 못합니다. 이미 올린 문서는 그대로 두었습니다.");
		}

		String documentTitle = textOr(title, textOr(name, id));
		int chars = 0;
		final List<Map<String, Object>> chunks = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < parts.size(); i++) {
			DocumentChunker.Chunk part = parts.get(i);
			String content = "[문서] " + documentTitle + " (" + (i + 1) + "/" + parts.size() + ")\n";
			String location = locationOf(part);
			if (location.length() > 0) {
				content += "[위치] " + location + "\n";
			}
			content += part.text;
			Map<String, Object> chunkMetadata = new LinkedHashMap<String, Object>();
			if (part.headingPath.length() > 0) {
				chunkMetadata.put("headingPath", part.headingPath);
			}
			if (part.pageStart != null) {
				chunkMetadata.put("pageStart", part.pageStart);
				chunkMetadata.put("pageEnd", part.pageEnd);
			}
			Map<String, Object> chunk = new HashMap<String, Object>();
			chunk.put("chunkNo", Integer.valueOf(i));
			chunk.put("content", content);
			chunk.put("contentHash", HashText.sha256(content));
			chunk.put("charCount", Integer.valueOf(content.length()));
			chunk.put("metadataJson", JsonText.of(chunkMetadata));
			chunks.add(chunk);
			chars += part.text.length();
		}

		Map<String, Object> metadata = new LinkedHashMap<String, Object>();
		metadata.put("docType", "UPLOAD");
		metadata.put("sourceId", id);
		metadata.put("fileName", name);
		metadata.put("contentType", extracted.contentType);
		metadata.put("chars", Integer.valueOf(chars));
		metadata.put("truncated", Boolean.valueOf(extracted.truncated));
		// 어떻게 나눴는지: HEADING(제목 스타일), BOOKMARK(PDF 북마크), NONE(제목 없음), LINE(JSONL 한 줄씩)
		metadata.put("structure", structureOf(lineBased, extracted));

		final Map<String, Object> document = new HashMap<String, Object>();
		document.put("sourceId", id);
		document.put("tenant", tenant);
		document.put("collection", group);
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
		result.put("collection", group);
		result.put("tenant", tenant);
		result.put("contentType", extracted.contentType);
		result.put("structure", metadata.get("structure"));
		result.put("chars", Integer.valueOf(chars));
		result.put("chunks", Integer.valueOf(chunks.size()));
		// 글자 수 상한에 걸려 뒷부분을 버렸는지
		result.put("truncated", Boolean.valueOf(extracted.truncated));
		// 같은 이름의 문서를 바꿔 넣었는지
		result.put("replaced", replaced);
		return result;
	}

	/**
	 * <pre>
	 * 호출자가 올린 문서 목록(페이지)
	 * </pre>
	 *
	 * @param collection 이 묶음의 문서만. 없으면 호출자의 문서 전부
	 */
	public Map<String, Object> getDocumentList(String tenant, String collection, int page, int size) {
		int pageNo = page < 1 ? 1 : page;
		int pageSize = size < 1 ? 50 : Math.min(size, MAX_PAGE_SIZE);
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("tenant", tenant);
		condition.put("collection", textOr(collection, null));
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

	/** 호출자가 올린 문서의 묶음과 묶음마다의 문서 수. 묶음이 없는 문서는 collection 이 비어 있는 한 줄로 나온다 */
	public List<Map<String, Object>> getCollections(String tenant) {
		Map<String, Object> owner = new HashMap<String, Object>();
		owner.put("tenant", tenant);
		return ragDao.selectUploadCollections(owner);
	}

	/**
	 * <pre>
	 * 문서 하나의 청크를 순번 범위로 돌려줍니다. 검색으로 찾은 조각의 앞뒤를 이어 읽을 때 씁니다.
	 * 한 번에 MAX_CHUNK_RANGE 개까지입니다. 범위가 문서 밖이면 문서 안쪽만 줍니다.
	 * </pre>
	 *
	 * @param from 처음 순번(0부터, 포함)
	 * @param to 마지막 순번(포함)
	 */
	public Map<String, Object> getChunks(String tenant, String sourceId, int from, int to) {
		String id = textOr(sourceId, null);
		if (id == null) {
			throw ApiException.badRequest("sourceId(문서 이름)는 필수입니다.");
		}
		if (from < 0 || to < from) {
			throw ApiException.badRequest("청크 순번은 0 이상이고 from이 to보다 클 수 없습니다: " + from + " ~ " + to);
		}
		if (to - from + 1 > MAX_CHUNK_RANGE) {
			throw ApiException.badRequest("청크는 한 번에 " + MAX_CHUNK_RANGE + "개까지 가져옵니다: " + from + " ~ " + to);
		}
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("tenant", tenant);
		condition.put("sourceId", id);
		condition.put("model", ragSettings.embeddingModel());
		condition.put("size", Integer.valueOf(1));
		condition.put("offset", Integer.valueOf(0));
		List<Map<String, Object>> found = ragDao.selectUploadDocuments(condition);
		Long docSeq = ragDao.selectUploadDocSeq(ownerOf(tenant, id));
		if (found.isEmpty() || docSeq == null) {
			throw ApiException.notFound("올린 적이 없는 문서입니다: " + id);
		}
		Map<String, Object> document = found.get(0);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("sourceId", id);
		result.put("title", document.get("title"));
		result.put("collection", document.get("collection"));
		result.put("totalChunks", Integer.valueOf(ragDao.countUploadChunks(docSeq.longValue())));
		result.put("chunks", ragDao.selectUploadChunks(docSeq.longValue(), from, to));
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

	/**
	 * <pre>
	 * 묶음 이름을 확인합니다. 비었으면 null(묶음 없음).
	 * 쉼표는 쓸 수 없습니다. 검색 Tool이 묶음 여러 개를 쉼표로 받기 때문입니다.
	 * </pre>
	 */
	public static String collectionOf(String collection) {
		if (collection == null || collection.trim().length() == 0) {
			return null;
		}
		String name = collection.trim();
		if (name.length() > MAX_COLLECTION_LENGTH) {
			throw ApiException.badRequest("collection(묶음 이름)은 " + MAX_COLLECTION_LENGTH + "자 이내여야 합니다.");
		}
		if (name.indexOf(',') >= 0) {
			throw ApiException.badRequest("collection(묶음 이름)에는 쉼표를 쓸 수 없습니다: " + name);
		}
		return name;
	}

	/** 청크 머리말의 위치: 제목 경로와 쪽. 둘 다 없으면 빈 글 */
	private static String locationOf(DocumentChunker.Chunk part) {
		String location = part.headingPath;
		if (part.pageStart != null) {
			String pages = part.pageStart.equals(part.pageEnd) ? "p." + part.pageStart : "p." + part.pageStart + "-" + part.pageEnd;
			location = location.length() == 0 ? pages : location + " (" + pages + ")";
		}
		return location;
	}

	private static String structureOf(boolean lineBased, DocumentTextExtractor.Extracted extracted) {
		if (lineBased) {
			return "LINE";
		}
		if (!extracted.bookmarks.isEmpty()) {
			return "BOOKMARK";
		}
		for (int i = 0; i < extracted.blocks.size(); i++) {
			if (DocumentBlock.HEADING.equals(extracted.blocks.get(i).kind)) {
				return "HEADING";
			}
		}
		return "NONE";
	}

	/** 문서 주인을 가리키는 조건(호출자 + 원본 ID) */
	private Map<String, Object> ownerOf(String tenant, String sourceId) {
		Map<String, Object> owner = new HashMap<String, Object>();
		owner.put("tenant", tenant);
		owner.put("sourceId", sourceId);
		return owner;
	}

	/** 비었으면 기본값, 아니면 앞뒤 공백을 뗀 값 */
	private String textOr(String value, String defaultValue) {
		return value == null || value.trim().length() == 0 ? defaultValue : value.trim();
	}

}

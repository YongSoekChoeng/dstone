package net.dstone.boot.ai.service;

import java.io.File;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;

import net.dstone.boot.ai.dao.DocumentDao;
import net.dstone.boot.ai.vo.DocumentVo;
import net.dstone.boot.ai.vo.IngestResult;
import net.dstone.common.config.ConfigProperty;

@Service
public class DocumentService extends net.dstone.boot.common.biz.BaseService {

	@Autowired
	private ConfigProperty configProperty;

	@Autowired
	private DocumentDao documentDao;

	/**
	 * dstone-ai-engine에 문서를 적재(POST /api/ai/rag/documents)한 뒤, 그 결과(청크 수)를 TB_AI_DOCUMENT에
	 * 자체 기록한다. dstone-ai-engine은 적재된 sourceId 목록을 조회하는 API가 없어(업로드/삭제/검색만 제공)
	 * "무엇을 올렸는지" 화면에 보여주려면 이 메타데이터가 필요하다.
	 */
	public DocumentVo uploadDocument(String sourceId, String originalFileName, File savedFile, String uploaderId) {

		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
		multipartBodyBuilder.part("file", new FileSystemResource(savedFile)).filename(originalFileName);
		multipartBodyBuilder.part("sourceId", sourceId);

		IngestResult ingestResult = this.getWebClient().post()
				.uri(baseUrl + "/api/ai/rag/documents")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.bodyValue(multipartBodyBuilder.build())
				.retrieve()
				.bodyToMono(IngestResult.class)
				.block();

		DocumentVo documentVo = new DocumentVo();
		documentVo.setSOURCE_ID(sourceId);
		documentVo.setFILE_NAME(originalFileName);
		documentVo.setCHUNK_COUNT(ingestResult != null ? ingestResult.chunkCount() : 0);
		documentVo.setUPLOADER_ID(uploaderId);

		try {
			this.documentDao.insertDocument(documentVo);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return documentVo;
	}

	public void deleteDocument(String sourceId) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		this.getWebClient().delete()
				.uri(baseUrl + "/api/ai/rag/documents/{sourceId}", sourceId)
				.retrieve()
				.toBodilessEntity()
				.block();
		try {
			this.documentDao.deleteDocument(sourceId);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public List<DocumentVo> listDocument() {
		try {
			return this.documentDao.listDocument();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}

package net.dstone.knowledge.rag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RagDao;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisPass;
import net.dstone.knowledge.job.FileHandler;
import net.dstone.knowledge.job.FilePassRunner;
import net.dstone.knowledge.job.FileResult;
import net.dstone.knowledge.scanner.EncodingDetector;

/**
 * <pre>
 * DOCUMENT 단계: 분석 결과로 RAG 문서와 청크를 만들고, 청크를 임베딩 대기열에 올립니다.
 *
 * 파일 하나마다 하는 일:
 *   1) 그 파일의 분석 결과(타입, 메소드, 호출 관계, 진입점 ...)를 DB에서 읽는다.
 *   2) 소스를 다시 읽어, 사실과 소스를 엮은 글을 짓는다(CodeDocumentBuilder). 파싱은 하지 않는다. 줄 번호로 잘라 온다.
 *   3) 문서와 청크를 저장하고, 청크의 내용 해시를 임베딩 대기열(rag_embedding, PENDING)에 올린다.
 *
 * 만드는 문서:
 *   Java 파일   → FILE / TYPE / METHOD 문서 (CodeDocumentBuilder)
 *   SQL 매퍼    → statement마다 MAPPER 문서 (ResourceDocumentBuilder)
 *   화면(JSP)   → 파일마다 VIEW 문서 (ResourceDocumentBuilder)
 *
 * 임베딩 자체는 여기서 하지 않습니다. 임베딩은 가장 오래 걸리는 일이라(청크 하나에 0.5초쯤) 분석 Job과 떼어 두었습니다.
 * 서버 안의 EmbeddingWorker가 대기열을 알아서 비웁니다. 그래서 이 단계는 금방 끝나고,
 * Job이 DONE이 된 뒤에도 임베딩은 뒤에서 계속 돕니다. 검색은 임베딩이 끝난 청크부터 됩니다.
 *
 * 같은 내용의 청크는 리비전이 달라도, 분석을 다시 돌려도 다시 임베딩하지 않습니다(키가 내용 해시이기 때문입니다).
 *
 * 문서는 언제든 분석 결과에서 다시 만들 수 있습니다. 이 단계가 만드는 것은 원천 데이터가 아닙니다.
 * </pre>
 */
@Component
public class DocumentPass extends BaseObject implements AnalysisPass {

	public static final String NAME = "DOCUMENT";

	/** 메소드 문서에 적는 "호출받는 곳"의 최대 수. 넘는 것은 "외 N곳"으로 적습니다. */
	private static final int MAX_CALLERS = 10;

	@Autowired
	private FilePassRunner filePassRunner;

	@Autowired
	private RagDao ragDao;

	@Autowired
	private RagSettings ragSettings;

	@Autowired
	private EncodingDetector encodingDetector;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int order() {
		return 600;
	}

	@Override
	public void run(final AnalysisJobContext context) throws Exception {
		final Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		final boolean skipAccessors = ragSettings.skipAccessors();
		final int chunkMaxChars = ragSettings.chunkMaxChars();
		final String model = ragSettings.embeddingModel();
		final int dimensions = ragSettings.embeddingDimensions();
		final int[] counts = new int[2];

		final ResourceDocumentBuilder resourceBuilder = new ResourceDocumentBuilder(context.getProjectId(), context.getRevisionId(), chunkMaxChars);

		filePassRunner.run(context, NAME, "JAVA", new FileHandler<CodeDocumentBuilder.Result>() {
			@Override
			public CodeDocumentBuilder.Result prepare(Map<String, Object> file) throws Exception {
				if (!"OK".equals(file.get("parseStatus"))) {
					// 파싱하지 못한 파일은 분석 결과가 없어서 문서를 만들 재료가 없다.
					return null;
				}
				long fileId = ((Number) file.get("fileId")).longValue();
				CodeDocumentBuilder.Material material = new CodeDocumentBuilder.Material();
				material.types = ragDao.selectTypesByFile(fileId);
				material.fields = ragDao.selectFieldsByFile(fileId);
				material.methods = ragDao.selectMethodsByFile(fileId);
				material.annotations = ragDao.selectAnnotationsByFile(fileId);
				material.typeRelations = ragDao.selectTypeRelationsByFile(fileId);
				material.callees = ragDao.selectCalleesByFile(fileId);
				material.callers = ragDao.selectCallersByFile(fileId, MAX_CALLERS);
				material.endpoints = ragDao.selectEndpointsByFile(context.getRevisionId(), fileId);
				material.links = ragDao.selectLinksByFile(fileId);

				byte[] bytes = Files.readAllBytes(root.resolve((String) file.get("path")));
				String text = encodingDetector.decode(bytes, (String) file.get("encoding"));
				return new CodeDocumentBuilder(context.getProjectId(), context.getRevisionId(), skipAccessors, chunkMaxChars).build(file, text, material);
			}

			@Override
			public FileResult write(Map<String, Object> file, CodeDocumentBuilder.Result prepared) throws Exception {
				if (prepared == null) {
					return FileResult.skipped("DECLARE 단계에서 처리되지 않은 파일입니다.");
				}
				long fileId = ((Number) file.get("fileId")).longValue();
				ragDao.replaceDocumentsInBatch(context.getRevisionId(), fileId, (String) file.get("path"), prepared.documents, prepared.chunks, model, dimensions);
				counts[0] += prepared.documents.size();
				counts[1] += prepared.chunks.size();
				return FileResult.done();
			}

			@Override
			public void reset() {
				// 파일 사이에 걸쳐 들고 있는 것이 없다.
			}
		});

		// SQL 매퍼: statement 하나가 문서 하나. 매퍼가 아닌 XML은 statement가 없어서 건너뛴다.
		// 리치클라이언트 화면(WebSquare, Nexacro)도 XML이다. 이것은 JSP처럼 파일 하나가 화면 문서 하나다.
		filePassRunner.run(context, NAME, "XML", new FileHandler<CodeDocumentBuilder.Result>() {
			@Override
			public CodeDocumentBuilder.Result prepare(Map<String, Object> file) throws Exception {
				long fileId = ((Number) file.get("fileId")).longValue();
				if (ResourceDocumentBuilder.isRichClientScreen((String) file.get("fileType"))) {
					byte[] bytes = Files.readAllBytes(root.resolve((String) file.get("path")));
					String text = encodingDetector.decode(bytes, (String) file.get("encoding"));
					return resourceBuilder.buildView(file, text, ragDao.selectViewLinksByFile(context.getRevisionId(), fileId));
				}
				List<Map<String, Object>> statements = ragDao.selectStatementsByFile(fileId);
				if (statements.isEmpty()) {
					return null;
				}
				return resourceBuilder.buildMapper(file, statements, ragDao.selectStatementTablesByFile(fileId), ragDao.selectStatementExecutorsByFile(fileId));
			}

			@Override
			public FileResult write(Map<String, Object> file, CodeDocumentBuilder.Result prepared) throws Exception {
				if (prepared == null) {
					return FileResult.skipped("SQL statement도 화면도 아닌 XML입니다.");
				}
				return save(context, file, prepared, model, dimensions, counts);
			}

			@Override
			public void reset() {
			}
		});

		// 화면(JSP): 파일 하나가 문서 하나. Java 코드가 없는 JSP도 만든다(화면과 주소의 연결은 Java 코드와 상관없다).
		filePassRunner.run(context, NAME, "JSP", new FileHandler<CodeDocumentBuilder.Result>() {
			@Override
			public CodeDocumentBuilder.Result prepare(Map<String, Object> file) throws Exception {
				long fileId = ((Number) file.get("fileId")).longValue();
				byte[] bytes = Files.readAllBytes(root.resolve((String) file.get("path")));
				String text = encodingDetector.decode(bytes, (String) file.get("encoding"));
				return resourceBuilder.buildView(file, text, ragDao.selectViewLinksByFile(context.getRevisionId(), fileId));
			}

			@Override
			public FileResult write(Map<String, Object> file, CodeDocumentBuilder.Result prepared) throws Exception {
				return save(context, file, prepared, model, dimensions, counts);
			}

			@Override
			public void reset() {
			}
		});

		info("DOCUMENT: 문서 " + counts[0] + "건, 청크 " + counts[1] + "건을 만들고 임베딩 대기열에 올렸습니다. model=" + model + ", analysisId=" + context.getAnalysisId());
	}

	/** 파일 하나의 문서와 청크를 저장하고 임베딩 대기열에 올립니다. */
	private FileResult save(AnalysisJobContext context, Map<String, Object> file, CodeDocumentBuilder.Result prepared, String model, int dimensions, int[] counts) {
		long fileId = ((Number) file.get("fileId")).longValue();
		ragDao.replaceDocumentsInBatch(context.getRevisionId(), fileId, (String) file.get("path"), prepared.documents, prepared.chunks, model, dimensions);
		counts[0] += prepared.documents.size();
		counts[1] += prepared.chunks.size();
		return FileResult.done();
	}

}

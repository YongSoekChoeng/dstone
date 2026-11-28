package net.dstone.ai.workflow.sm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import net.dstone.ai.api.controller.WorkFlowExecutionController;
import net.dstone.ai.api.dto.WorkFlowDeliverable;
import net.dstone.ai.api.service.WorkFlowDeliverableService;
import net.dstone.ai.support.WorkflowStepper;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * 승인 step의 approval.deliverable로 개발 결과를 내려받는 기능을 sm-workflow의 개발 승인(step19)에서 확인합니다(LLM 없음).
 * - 승인 대기 중에는 바뀐 파일, 삭제 목록, 원본 비교 해시를 주고 ZIP에는 문서도 담는다.
 * - 줄바꿈만 다른 파일은 해시가 같다. 원본 폴더 밖을 가리키는 삭제 줄은 버린다.
 * - 승인하고 끝난 실행에서도 받을 수 있고, 승인 step에 오기 전에는 받을 것이 없다.
 * </pre>
 */
public class SmDeliverableTest extends WorkflowTestBase {

	private static final String NOW = "20261128-1500";

	@Autowired
	private WorkFlowDeliverableService deliverableService;

	@Autowired
	private WorkFlowExecutionController controller;

	@TempDir
	Path workDir;

	@Test
	void 개발_승인에서_바뀐_파일과_삭제_목록을_내려받는다() throws IOException {
		Path source = this.workDir.resolve("source");
		this.write(source.resolve("src/A.java"), "class A {\r\n}\r\n");
		this.write(source.resolve("src/Old.java"), "class Old {}");
		Path folder = this.workDir.resolve(NOW);
		this.write(folder.resolve("01.customer_requirement.md"), "# 요구사항");
		Path dev = folder.resolve("dev-resources");
		this.write(dev.resolve("src/A.java"), "class A {\n\tint x;\n}\n");
		this.write(dev.resolve("src/main/webapp/b.jsp"), "<p/>");
		this.write(dev.resolve("_delete-files.txt"), "# 설명\nsrc/Old.java\n../outside.txt\n/etc/../passwd\nC:/x.txt\n");

		WorkflowStepper flow = this.stepper("sm-workflow", this.workDir.toString().replace("\\", "/"));
		flow.put("now", NOW).put("projectId", "myApp").put("projectRevision", Map.of("sourceRoot", source.toString().replace("\\", "/")));
		flow.jumpTo("step19");
		assertThat(this.deliverableService.describe(flow.execution().executionId(), false).available()).isFalse();

		flow.step("step19");
		String executionId = flow.execution().executionId();
		WorkFlowDeliverable data = this.deliverableService.describe(executionId, true);

		assertThat(data.available()).as(data.reason()).isTrue();
		assertThat(data.key()).isEqualTo("myApp");
		assertThat(data.documentCount()).isEqualTo(1);
		assertThat(data.files()).extracting(WorkFlowDeliverable.Item::path).containsExactly("src/A.java", "src/main/webapp/b.jsp");
		assertThat(data.deletes()).extracting(WorkFlowDeliverable.Deletion::path).containsExactly("src/Old.java");
		assertThat(data.deletes().get(0).baseHash()).isNotNull();
		// 원본(CRLF)과 같은 내용을 LF로 쓴 파일의 해시가 원본 해시와 같아야 로컬에서 '수정'으로 판단합니다.
		this.write(dev.resolve("src/A.java"), "class A {\n}\n");
		WorkFlowDeliverable same = this.deliverableService.describe(executionId, true);
		assertThat(same.files().get(0).hash()).isEqualTo(same.files().get(0).baseHash());
		assertThat(data.files().get(1).baseHash()).isNull();

		assertThat(this.zipNames(this.deliverableService.zip(executionId)))
			.containsExactly("source/src/A.java", "source/src/main/webapp/b.jsp", "delete-files.txt", "documents/01.customer_requirement.md");

		ResponseEntity<Object> response = this.controller.deliverableZip(executionId, new MockHttpServletRequest());
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("deliverable-" + executionId + ".zip");
		assertThat(this.controller.deliverable("none", false, new MockHttpServletRequest()).getStatusCode().value()).isEqualTo(404);

		flow.decide("승인(완료)", "");
		assertThat(this.deliverableService.describe(executionId, false).available()).isTrue();
	}

	private void write(Path path, String text) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, text, StandardCharsets.UTF_8);
	}

	private List<String> zipNames(byte[] bytes) throws IOException {
		List<String> names = new ArrayList<String>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
			ZipEntry entry = zip.getNextEntry();
			while (entry != null) {
				names.add(entry.getName());
				entry = zip.getNextEntry();
			}
		}
		return names;
	}

}

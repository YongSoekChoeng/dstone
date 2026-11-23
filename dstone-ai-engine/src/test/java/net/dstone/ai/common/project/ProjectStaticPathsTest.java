package net.dstone.ai.common.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 프로젝트 관리의 '시험 서버 정적 경로'(한 줄에 "/주소=폴더")를 읽고 검사하는 규칙을 봅니다.
 * </pre>
 */
public class ProjectStaticPathsTest {

	@Test
	void 줄마다_주소와_폴더를_읽고_모양을_맞춘다() {
		List<String[]> paths = ProjectStore.parseStaticPaths("# 업로드\n /Upload/ = D:\\app\\uploadData\\Upload \n\n/files/img=/data/img\n");
		assertThat(paths).hasSize(2);
		assertThat(paths.get(0)).containsExactly("/Upload", "D:/app/uploadData/Upload");
		assertThat(paths.get(1)).containsExactly("/files/img", "/data/img");
		assertThat(ProjectStore.staticPathsText(" /Upload/=D:/a ")).isEqualTo("/Upload=D:/a");
		assertThat(ProjectStore.staticPathsText("  ")).isNull();
	}

	@Test
	void 앱_전체나_모양이_틀린_줄은_거절한다() {
		assertThat(rejected("/=D:/a")).isTrue();
		assertThat(rejected("Upload=D:/a")).isTrue();
		assertThat(rejected("/Upload")).isTrue();
		assertThat(rejected("/a/../b=D:/a")).isTrue();
		assertThat(rejected("/a=D:/x\n/a/=D:/y")).isTrue();
		assertThat(rejected("/a=D:/x")).isFalse();
	}

	/** 그 글을 읽다가 모양이 틀렸다고 거절하는지 봅니다. */
	private static boolean rejected(String text) {
		try {
			ProjectStore.parseStaticPaths(text);
			return false;
		} catch (IllegalArgumentException e) {
			return true;
		}
	}

}

package mref;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.jsonwebtoken.Claims;

/**
 * 메소드 참조(::) 해석 표본. io.jsonwebtoken 은 jar 없이 import 만 있다.
 */
public class MethodRefs {

	private List<String> names = new ArrayList<String>();

	/** 프로젝트 밖 타입(jar 없음): EXTERNAL io.jsonwebtoken.Claims.getSubject */
	public String subject(Claims claims) {
		return read(claims, Claims::getSubject);
	}

	/** JDK 타입: 해석기가 푼다. EXTERNAL java.lang.String.trim() */
	public List<String> trimAll(List<String> values) {
		return values.stream().map(String::trim).collect(Collectors.toList());
	}

	/** 프로젝트 타입의 메소드: MemberVO#getName 으로 정확히 */
	public List<String> namesOf(List<MemberVO> members) {
		return members.stream().map(MemberVO::getName).collect(Collectors.toList());
	}

	/** this::메소드: MethodRefs#upper */
	public List<String> upperAll(List<String> values) {
		return values.stream().map(this::upper).collect(Collectors.toList());
	}

	/** 변수::메소드(프로젝트 타입, 오버로드 2개): Formatter#format 후보 */
	public List<String> formatAll(List<String> values, Formatter formatter) {
		return values.stream().map(formatter::format).collect(Collectors.toList());
	}

	/** 필드::메소드(JDK 타입): EXTERNAL java.util.List.add */
	public void collect(List<String> values) {
		values.forEach(names::add);
	}

	/** 생성자 참조(프로젝트 타입): MethodRefs 안의 Formatter 생성자 */
	public List<Formatter> formatters(List<String> prefixes) {
		return prefixes.stream().map(Formatter::new).collect(Collectors.toList());
	}

	/** 배열 생성: IGNORED */
	public String[] toArray(List<String> values) {
		return values.stream().toArray(String[]::new);
	}

	private String upper(String value) {
		return value.toUpperCase();
	}

	private <T> String read(Claims claims, Function<Claims, T> reader) {
		return String.valueOf(reader.apply(claims));
	}
}

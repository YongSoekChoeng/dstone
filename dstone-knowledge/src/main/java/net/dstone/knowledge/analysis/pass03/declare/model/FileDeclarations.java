package net.dstone.knowledge.analysis.pass03.declare.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * 파일 하나에서 찾아낸 선언과 참조를 모아 둔 묶음입니다.
 * DECLARE 단계가 파일 하나를 처리할 때 만들어서 DB에 쓰고 바로 버립니다.
 * </pre>
 */
public class FileDeclarations {

	/** 파서가 읽은 패키지 이름. 기본 패키지면 빈 문자열 */
	private String packageName = "";

	/** 타입(클래스·인터페이스·enum …) → analysis_symbol */
	private final List<TypeRow> types = new ArrayList<TypeRow>();
	/** 메소드·생성자 → analysis_method */
	private final List<MethodRow> methods = new ArrayList<MethodRow>();
	/** 필드 → analysis_field */
	private final List<FieldRow> fields = new ArrayList<FieldRow>();
	/** 애노테이션 → analysis_annotation */
	private final List<AnnotationRow> annotations = new ArrayList<AnnotationRow>();
	/** 아직 풀지 않은 참조(호출·생성·타입 사용 …) → analysis_reference. RESOLVE 단계가 푼다 */
	private final List<ReferenceRow> references = new ArrayList<ReferenceRow>();
	/** Java 문자열로 적은 SQL(analysis_mapper 의 mapper_type JAVA 행). 키는 ResourceDao 의 insertMapper 와 같고 propertiesJson 이 더 있다 */
	private final List<Map<String, Object>> sqlStatements = new ArrayList<Map<String, Object>>();

	/** 저장하지 못하고 버린 것들의 설명(같은 ID가 겹친 경우 등). 분석 오류로 남깁니다. */
	private final List<String> warnings = new ArrayList<String>();

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public List<TypeRow> getTypes() {
		return types;
	}

	public List<MethodRow> getMethods() {
		return methods;
	}

	public List<FieldRow> getFields() {
		return fields;
	}

	public List<AnnotationRow> getAnnotations() {
		return annotations;
	}

	public List<ReferenceRow> getReferences() {
		return references;
	}

	public List<Map<String, Object>> getSqlStatements() {
		return sqlStatements;
	}

	public List<String> getWarnings() {
		return warnings;
	}

}

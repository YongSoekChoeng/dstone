package net.dstone.knowledge.parser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 파일 하나에서 찾아낸 선언과 참조를 모아 둔 묶음입니다.
 * DECLARE 단계가 파일 하나를 처리할 때 만들어서 DB에 쓰고 바로 버립니다.
 */
public class FileDeclarations {

	/** 파서가 읽은 패키지 이름. 기본 패키지면 빈 문자열 */
	private String packageName = "";

	private final List<TypeRow> types = new ArrayList<TypeRow>();
	private final List<MethodRow> methods = new ArrayList<MethodRow>();
	private final List<FieldRow> fields = new ArrayList<FieldRow>();
	private final List<AnnotationRow> annotations = new ArrayList<AnnotationRow>();
	private final List<ReferenceRow> references = new ArrayList<ReferenceRow>();

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

	public List<String> getWarnings() {
		return warnings;
	}

}

<!DOCTYPE html>
<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@ include file="/resources/common/jstl-tld.jsp"%>
<!--
#########################################################################################
	화면 설명 : 이미지 게시판 상세화면 (팝업, 편집/조회 모드)
	            항목: 제목, 등록자(로그인 사용자명 자동세팅), 파일업로드
	            버튼: 저장, 수정(조회모드에서 편집모드로 전환)
#########################################################################################
 -->
<html>
<head>
<script type="text/javascript">
var uploadWidget;

$(document).ready(function() {
	// 등록자 자동세팅 (로그인 사용자명)
	$("#writer_nm").val("${ImageBoardVO.writer_nm}");

	// 조회모드(기존 데이터가 있으면) 입력 비활성
	if ("${ImageBoardVO.seq}" != null && "${ImageBoardVO.seq}" != "") {
		setMode(false);
	}

	// 파일 업로드
	uploadWidget = $("#image_file").kendoUpload({
		multiple : false,
		async : {
			saveUrl : "/test/imageboard/saveImageBoard.ajax?${_csrf.parameterName}=${_csrf.token}",
			autoUpload : false
		},
		files : [<c:if test="${ImageBoardVO.image_url ne null and ImageBoardVO.image_url != ''}">{name:"이미지"}</c:if>],
		validation : {
			allowedExtensions : [".jpg", ".jpeg", ".png", ".gif"]
		}
	});

	// 저장
	$("#bt_save").bind("click", function(e) {
		save();
	});

	// 수정 (조회모드 → 편집모드)
	$("#bt_edit").bind("click", function(e) {
		setMode(true);
	});
});

// 편집(true)/조회(false) 모드 전환
	// 조회모드(editable=false)에서는 저장 버튼만 비활성화, 수정 버튼은 활성화(편집모드로 전환)
function setMode(editable) {
	$("#title").prop("readonly", !editable);
	$("#writer_nm").prop("readonly", true);	// 등록자는 항상 읽기전용
	if (uploadWidget != undefined) {
		uploadWidget.enable(editable);
	}
	$("#bt_save").prop("disabled", !editable);
	$("#bt_edit").prop("disabled", false);	// 수정 버튼은 항상 활성화(조회모드에서 편집모드로 전환)
}

// 저장
function save() {
	var title = $("#title").val();
	if (title == null || title == "") {
		genexon.alert("error", "저장전 확인", "제목을 입력하세요.");
		$("#title").focus();
		return;
	}
	if (confirm("저장하시겠습니까?") == true) {
		var formData = new FormData();
		formData.append("title", title);
		formData.append("writer_nm", $("#writer_nm").val());
		formData.append("seq", $("#seq").val());
		var fileInput = (uploadWidget != undefined) ? uploadWidget.element[0] : null;
		if (fileInput != null && fileInput.files.length > 0) {
			formData.append("image_file", fileInput.files[0]);
		}
		$.ajax({
			type : "POST",
			url : "/test/imageboard/saveImageBoard.ajax?${_csrf.parameterName}=${_csrf.token}",
			data : formData,
			processData : false,
			contentType : false,
			success : function(res) {
				genexon.PopWindowClose2("#imageBoardDetail");
				genexon.alert("success", "결과", "정상처리되었습니다.");
			},
			error : function() {
				alert("저장 중 오류가 발생했습니다.");
			}
		});
	}
}
</script>
</head>
<body>
	<div class="content" style="background: white;">
		<input type="hidden" id="seq" name="seq" value="${ImageBoardVO.seq}" />
		<table class="ta_sample2">
			<colgroup>
				<col width="20%" />
				<col width="80%" />
			</colgroup>
			<tbody>
				<tr>
					<th class="essential">제목</th>
					<td>
						<input type="text" class="k-textbox" name="title" id="title" style="width: 400px;" value="${ImageBoardVO.title}" />
					</td>
				</tr>
				<tr>
					<th>등록자</th>
					<td>
						<input type="text" class="k-textbox" name="writer_nm" id="writer_nm" style="width: 400px;" readonly="readonly" />
					</td>
				</tr>
				<tr>
					<th>이미지</th>
					<td>
						<input type="file" name="image_file" id="image_file" />
						<c:if test="${ImageBoardVO.image_url ne null and ImageBoardVO.image_url != ''}">
							<img src="${ImageBoardVO.image_url}" style="width:100px;height:100px;margin-left:10px;" />
						</c:if>
					</td>
				</tr>
			</tbody>
		</table>

		<div class="bt_right">
			<div class="bt_save" id="bt_save">저장</div>
			<div class="bt_save" id="bt_edit">수정</div>
		</div>
	</div>
</body>
</html>

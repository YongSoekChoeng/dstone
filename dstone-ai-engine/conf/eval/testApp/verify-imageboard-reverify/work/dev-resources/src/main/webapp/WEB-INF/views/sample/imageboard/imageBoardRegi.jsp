<!DOCTYPE html>
<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@ include file="/resources/common/jstl-tld.jsp"%>
<!--
#########################################################################################
	화면 설명 : 이미지게시판 등록/수정/조회 팝업
	            (제목, 등록자(로그인 사용자명 자동세팅), 파일업로드버튼, 저장/수정)
#########################################################################################
 -->
<html>
<head>
<script type="text/javascript">
$(document).ready(function() {
	// 폼전송
	$('#myForm').ajaxForm({
		beforeSubmit : function(data, frm, opt) {
			return true;
		},
		success : function(responseText, statusText) {
			genexon.PopWindowClose2("#imageBoardRegi");
			genexon.alert("success", "결과", "정상처리되었습니다.");
		},
		error : function() {
			genexon.alert("error", "결과", "에러가 발생했습니다.");
		}
	});

	// 조회모드(수정)이면 seq가 있고, 등록모드이면 seq가 없다
	var seq = $("#seq").val();
	if (seq != null && seq != "") {
		// 수정/조회
		$("#bt_save").text("수정");
		$("#myForm").attr("action", "/sample/imageboard/updateImageBoard.ajax?${_csrf.parameterName}=${_csrf.token}");
	} else {
		// 등록
		$("#bt_save").text("저장");
	}

	ImageUpload();
});

// 저장/수정
function save() {
	var title = $("#title").val();
	if (title == null || title == "") {
		genexon.alert("error", "저장전 확인", "제목을 입력하세요.");
		$("#title").focus();
		return;
	}
	if (confirm("내용을 저장하시겠습니까?") == true) {
		$('#myForm').submit();
	}
}

function ImageUpload() {
	$("#img_name").kendoUpload({
		multiple : false,
		async : {
			saveUrl : "/sample/imageboard/imageUrl.ajax?${_csrf.parameterName}=${_csrf.token}",
			autoUpload : true
		},
		files:[<c:if test="${ImageBoardVO.file_no ne null and ImageBoardVO.file_no != ''}">{name:"${ImageBoardVO.file_no}",size:"0"}</c:if>],
		validation : {
			allowedExtensions : [ ".jpg", ".png" ]
		},
		showFileList : true,
		localization : {
			select : "선택",
			remove : "삭제",
			dropFilesHere : "이미지 선택",
			uploadSelectedFiles : "파일선택",
			statusUploading : "전송중",
			statusUploaded : "전송 완료",
			statusFailed : "전송 실패",
			headerStatusUploading : "파일 처리중",
			headerStatusUploaded : "완료"
		},
		success : function(data) {
			if (data.response.FileVO != undefined) {
				$("#file_url").val(data.response.FileVO.file_url);
				$("#file_no").val(data.response.FileVO.file_no);
			}
		}
	});
}
</script>
</head>
<body>
<form id="myForm" name="myForm" method="post" action="/sample/imageboard/insertImageBoard.ajax?${_csrf.parameterName}=${_csrf.token}" enctype="multipart/form-data">
	<input type="hidden" name="seq" id="seq" value="${ImageBoardVO.seq}" />
	<div class="content" style="background: white;">
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
						<input type="text" class="k-textbox" name="writer_nm" id="writer_nm" style="width: 400px;" value="${ImageBoardVO.writer_nm}" readonly="readonly" />
					</td>
				</tr>
				<tr>
					<th>파일</th>
					<td>
						<input type="hidden" name="file_no" id="file_no" value="${ImageBoardVO.file_no}" />
						<input type="hidden" name="file_url" id="file_url" value="${ImageBoardVO.file_url}" />
						<input type="file" name="img_name" id="img_name" />
						<c:if test="${ImageBoardVO.file_url ne null and ImageBoardVO.file_url != ''}">
							<img src="${ImageBoardVO.file_url}" alt="이미지" style="width:200px; height:150px; margin-top:5px;" />
						</c:if>
					</td>
				</tr>
			</tbody>
		</table>

		<div class="bt_right">
			<div class="bt_save" id="bt_save" onclick="javascript:save();">저장</div>
		</div>

	</div>
	<!--end content-->
</form>
</body>
</html>

<!DOCTYPE html>
<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@ include file="/resources/common/jstl-tld.jsp"%>
<!--
#########################################################################################
	화면 설명 : 이미지 게시판 - 테스트 메뉴용 목록 화면
	            (체크박스/SEQ/썸네일/제목/등록자, 페이징, 제목 조회,
	             전체선택/등록/삭제, 제목 클릭→상세 팝업(조회), 썸네일 클릭→이미지 팝업)
#########################################################################################
 -->
<html>
<head>
<script type="text/javascript">
/**
 * 조회
 */
function srch() {
	var grid = $("#grid").data("kendoGrid");
	grid.dataSource.read({
		json_string : genexon.getSearchParameterToJsonString()
	});
}

/**
 * 전체선택
 */
function selectAll() {
	var checked = $("#chk_all").is(":checked");
	$("#grid tbody .row_chk").prop("checked", checked);
}

/**
 * 등록 - 상세화면 팝업(편집 모드)
 */
function regOpen() {
	genexon.PopWindowOpen({
		pID : "imageBoardDetail",
		pTitle : "이미지 등록",
		pURL : "/test/imageboard/imageBoardDetail.pop?${_csrf.parameterName}=${_csrf.token}",
		pWidth : "70%",
		pHeight : "70%",
		callbackFunc : srch,
		pModal : true,
		resizable : true
	});
}

/**
 * 삭제 - 체크된 게시물 삭제
 */
function del() {
	var seqList = [];
	$("#grid tbody .row_chk").each(function() {
		if ($(this).is(":checked")) {
			seqList.push(Number($(this).data("seq")));
		}
	});
	if (seqList.length == 0) {
		genexon.alert("error", "삭제", "삭제할 게시물을 선택하세요.");
		return;
	}
	if (confirm("선택한 게시물을 삭제하시겠습니까?") == true) {
		$.ajax({
			type : "POST",
			url : "/test/imageboard/deleteImageBoard.ajax?${_csrf.parameterName}=${_csrf.token}",
			contentType : "application/json",
			data : JSON.stringify({seqList: seqList}),
			success : function(res) {
				srch();
			},
			error : function() {
				alert("삭제 중 오류가 발생했습니다.");
			}
		});
	}
}

/**
 * 제목 클릭 - 상세화면 팝업(조회 모드)
 */
function titleClick(e) {
	var grid = $("#grid").data("kendoGrid");
	var item = grid.dataItem($(e.target).closest("tr"));
	genexon.PopWindowOpen({
		pID : "imageBoardDetail",
		pTitle : "이미지 조회",
		pURL : "/test/imageboard/imageBoardDetail.pop?${_csrf.parameterName}=${_csrf.token}",
		pWidth : "70%",
		pHeight : "70%",
		callbackFunc : srch,
		pModal : true,
		resizable : true,
		data : item
	});
}

/**
 * 썸네일 클릭 - 이미지 팝업
 */
function thumbClick(e) {
	var grid = $("#grid").data("kendoGrid");
	var item = grid.dataItem($(e.target).closest("tr"));
	if (item.image_url == null || item.image_url == "") {
		return;
	}
	genexon.PopWindowOpen({
		pID : "imageBoardImg",
		pTitle : "이미지",
		pURL : item.image_url,
		pWidth : "70%",
		pHeight : "70%",
		pModal : true,
		resizable : true
	});
}
</script>
</head>
<body>
	<div class="search">
		<table class="se">
			<tr>
				<td><input type="text" class="k-textbox" name="search_word" id="search_word" placeholder="제목" style="width: 300px" onKeyPress="if(event.keyCode==13)srch();" /></td>
				<td class="line">구분선</td>
				<td><button class="kbtn k-primary" onclick="javascript:srch();" id="srch" name="srch">검색</button></td>
				<td><input type="checkbox" id="chk_all" onclick="selectAll();" />&nbsp;전체선택</td>
				<td><button class="kbtn k-primary" onclick="javascript:regOpen();" id="reg" name="reg">등록</button></td>
				<td><button class="kbtn k-primary" onclick="javascript:del();" id="del" name="del">삭제</button></td>
			</tr>
		</table>
	</div>

	<hr style="display:block; height:20px;">

	<div id="grid" class="resizegrid"></div>

	<script type="text/javascript">
	/**
	 * 그리드 컬럼 정의
	 **/
	var gridpt = {
		selection : "multiple",
		columns: [
			{ field: "checked",   title: "",       width: 40, headerAttributes: { style: "text-align:center;" }, attributes: { style: "text-align:center;" }, template: function(e) { return "<input type='checkbox' class='row_chk' data-seq='" + e.seq + "' " + (e.checked ? "checked" : "") + " />"; } },
			{ field: "seq",       title: "SEQ",    width: 80, attributes: { style: "text-align:center;" } },
			{
				field: "thumb_url", title: "썸네일", width: 100,
				template: function(e) {
					var src = (e.thumb_url != null && e.thumb_url != "") ? e.thumb_url : e.image_url;
					if (src != null && src != "") {
						return "<img src=\"" + src + "\" style=\"width:60px;height:60px;cursor:pointer;\" onclick=\"thumbClick(event)\" />";
					}
					return "";
				},
				attributes: { style: "text-align:center;" }
			},
			{
				field: "title", title: "제목", width: 300,
				template: function(e) {
					return "<a href=\"javascript:void(0);\" onclick=\"titleClick(event)\" style=\"cursor:pointer;\">" + e.title + "</a>";
				}
			},
			{ field: "writer_nm", title: "등록자", width: 120, attributes: { style: "text-align:center;" } }
		]
	};

	/**
	 * 데이터 소스 (URL / 모델)
	 **/
	var ds = {
		transport: {
			read: {url: "/test/imageboard/getImageBoardList.ajax?${_csrf.parameterName}=${_csrf.token}" }
		},
		schema: {
			data : "results",
			model: {
				id: "seq",
				fields: {
					seq       : { editable: false },
					checked   : { type: "boolean" },
					title     : {},
					writer_nm : {},
					image_url : {},
					thumb_url : {},
					in_dtm    : { editable: false },
					up_dtm    : { editable: false }
				}
			}
		},
		pageSize: 20,
		serverPaging: true,
		serverSorting: true,
		serverFiltering: true
	};

	genexon.initKendoUI_grid_inlineEdit("#grid", gridpt, ds);
	srch();
	</script>
</body>
</html>

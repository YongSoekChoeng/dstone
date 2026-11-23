<!DOCTYPE html>
<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@ include file="/resources/common/jstl-tld.jsp"%>
<!--
#########################################################################################
	화면 설명 : 이미지게시판 - 목록화면
	            (체크박스/SEQ/썸네일/제목/등록자, 제목 조회, 페이징, 전체선택/등록/삭제,
	             제목 클릭→상세 팝업(조회), 썸네일 클릭→이미지 팝업)
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
	var chk = $("#grid").find("input.rowChk");
	var all = chk.length > 0 && chk.filter(":checked").length == chk.length;
	chk.prop("checked", !all);
}

/**
 * 등록 (상세 팝업 편집모드)
 */
function imageBoardRegi() {
	var wd = genexon.body.find("#imageBoardRegi");
	if (wd.length > 0) {
		var kwd = wd.data("kendoWindow");
		if (kwd != undefined) {
			kwd.close();
		}
	}
	genexon.PopWindowOpen({
		pID : "imageBoardRegi",
		pTitle : "이미지 등록",
		pURL : "/sample/imageboard/imageBoardRegi.pop?${_csrf.parameterName}=${_csrf.token}",
		pWidth : 700,
		pHeight : 450,
		callbackFunc : srch,
		pModal : true,
		resizable : true,
		position : {
			top : 200,
			left : 300
		}
	});
}

/**
 * 제목 클릭 → 상세 팝업(조회모드)
 */
function imageBoardView(e) {
	var grid = $("#grid").data("kendoGrid");
	var selectedItem = grid.dataItem($(e.target).closest("tr"));
	if (selectedItem != null) {
		genexon.PopWindowOpen({
			pID : "imageBoardRegi",
			pTitle : "이미지 조회",
			pURL : "/sample/imageboard/imageBoardRegi.pop?${_csrf.parameterName}=${_csrf.token}",
			pWidth : 700,
			pHeight : 450,
			callbackFunc : srch,
			pModal : true,
			resizable : true,
			position : {
				top : 200,
				left : 300
			},
			data : selectedItem
		});
	}
}

/**
 * 썸네일 클릭 → 이미지 팝업
 */
function imageThumbView(e) {
	var grid = $("#grid").data("kendoGrid");
	var item = grid.dataItem($(e.target).closest("tr"));
	if (item != null && item.file_url != null && item.file_url != "") {
		genexon.PopWindowOpen({
			pID : "imageBoardImg",
			pTitle : "이미지",
			pURL : item.file_url,
			pWidth : 600,
			pHeight : 500,
			pModal : true,
			resizable : true,
			position : {
				top : 200,
				left : 300
			}
		});
	}
}

/**
 * 삭제 (체크된 게시물)
 */
function del() {
	var seqs = [];
	$("#grid").find("input.rowChk:checked").each(function() {
		seqs.push($(this).val());
	});
	if (seqs.length == 0) {
		genexon.alert("info", "삭제", "삭제할 게시물을 선택하세요.");
		return;
	}
	if (confirm("선택한 게시물을 삭제하시겠습니까?") == true) {
		var models = [];
		for (var i = 0; i < seqs.length; i++) {
			models.push({ seq : seqs[i] });
		}
		$.ajax({
			url : "/sample/imageboard/deleteImageBoard.ajax?${_csrf.parameterName}=${_csrf.token}",
			type : "post",
			contentType : "application/json",
			data : JSON.stringify({ models : models }),
			success : function(res) {
				genexon.alert("success", "삭제", "삭제되었습니다.");
				srch();
			},
			error : function() {
				genexon.alert("error", "삭제", "삭제에 실패했습니다.");
			}
		});
	}
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
				<td><button class="kbtn" onclick="javascript:selectAll();" id="selAll" name="selAll">전체선택</button></td>
				<td><button class="kbtn k-primary" onclick="javascript:imageBoardRegi();" id="regi" name="regi">등록</button></td>
				<td><button class="kbtn" onclick="javascript:del();" id="del" name="del">삭제</button></td>
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
		toolbar: [],
		columns: [
			{
				field: "chk",
				title: "",
				width: 40,
				attributes: { style: "text-align:center;" },
				template: function(e) {
					return "<input type='checkbox' class='rowChk' value='" + e.seq + "'/>";
				}
			},
			{ field: "seq",       title: "SEQ", width: 80, attributes: { style: "text-align:center;" } },
			{
				field: "file_url",
				title: "썸네일",
				width: 100,
				attributes: { style: "text-align:center;" },
				template: function(e) {
					if (e.file_url != null && e.file_url != "") {
						return "<img src=\"" + e.file_url + "\" alt=\"썸네일\" style=\"width:80px; height:60px; cursor:pointer;\" onclick=\"imageThumbView.call(this, event);\"/>";
					} else {
						return "";
					}
				}
			},
			{
				field: "title",
				title: "제목",
				width: 300,
				template: function(e) {
					return "<a href=\"javascript:void(0);\" style=\"color:#000;\" onclick=\"imageBoardView.call(this, event);\">" + e.title + "</a>";
				}
			},
			{ field: "writer_nm", title: "등록자", width: 120 }
		],
		editable: false,
		pageable: true,
		autoBind: true
	};

	/**
	 * 데이터 소스 (URL / 모델)
	 **/
	var ds = {
		transport: {
			read : {url: "/sample/imageboard/getImageBoardList.ajax?${_csrf.parameterName}=${_csrf.token}" }
		},
		schema: {
			data : "results",
			total : "total",
			model: {
				id: "seq",
				fields: {
					seq       : { editable: false },
					title     : {},
					file_url  : {},
					writer_nm : {}
				}
			}
		},
		serverPaging: true
	};

	genexon.initKendoUI_grid_inlineEdit("#grid", gridpt, ds);
	srch();
	</script>
</body>
</html>

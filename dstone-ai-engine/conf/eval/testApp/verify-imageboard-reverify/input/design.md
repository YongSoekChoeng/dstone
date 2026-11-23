# 설계서 - 이미지 게시판 개발

- **프로젝트ID:** testApp
- **소스경로:** ${param.sourceDir}
- **DB:** testApp.testApp (jdbc:mysql://localhost:3306/testApp, schema=testApp)

## 설계 요약

- 신규 패키지 `kr.co.gnx.sample.imageboard`에 이미지게시판 모듈을 만든다. 샘플게시판(SampleBoard)의 Controller/Service/DAO/VO/매퍼 구조를 그대로 따른다.
- 메뉴 URL `/sample/imageboard/imageBoard.go`에 맞춰 화면·Controller 경로를 정한다(승인 지시 A-1).
- 기존 테이블 `tbts_image_board`를 그대로 사용한다. 새 테이블을 만들지 않는다(승인 지시 A-2/A-3).
- 삭제는 `use_yn='N'` 논리삭제다(승인 지시 B). 파일(tbsy_file_info)은 삭제하지 않는다.
- 목록화면은 Kendo 그리드(서버 페이징)로 체크박스·SEQ·썸네일·제목·등록자를 표시하고, 상세화면은 팝업(등록/수정/조회)으로 파일업로드(kendoUpload)를 처리한다.

## 변경 상세

신규 파일 7개. 기존 파일 수정 없음.

| 파일 절대경로 | 위치 | 변경 전 | 변경 후 | 이유 | TASK |
|---|---|---|---|---|---|
| ${param.sourceDir}/src/main/java/kr/co/gnx/sample/imageboard/ImageBoardVO.java | 신규 파일 | (없음) | 아래 [신규-1] | 이미지게시판 VO | TASK-01 |
| ${param.sourceDir}/src/main/java/kr/co/gnx/sample/imageboard/ImageBoardDAO.java | 신규 파일 | (없음) | 아래 [신규-2] | 이미지게시판 DAO | TASK-01 |
| ${param.sourceDir}/src/main/java/kr/co/gnx/sample/imageboard/ImageBoardService.java | 신규 파일 | (없음) | 아래 [신규-3] | 이미지게시판 서비스 | TASK-01 |
| ${param.sourceDir}/src/main/java/kr/co/gnx/sample/imageboard/ImageBoardController.java | 신규 파일 | (없음) | 아래 [신규-4] | 이미지게시판 Controller(목록/단건/등록/수정/삭제/업로드) | TASK-01~04 |
| ${param.sourceDir}/src/main/resources/sqlmap/mapper/sample/imageboard-mapper.xml | 신규 파일 | (없음) | 아래 [신규-5] | 이미지게시판 쿼리(namespace ImageBoard) | TASK-01~04 |
| ${param.sourceDir}/src/main/webapp/WEB-INF/views/sample/imageboard/imageBoard.jsp | 신규 파일 | (없음) | 아래 [신규-6] | 목록화면 | TASK-01~03 |
| ${param.sourceDir}/src/main/webapp/WEB-INF/views/sample/imageboard/imageBoardRegi.jsp | 신규 파일 | (없음) | 아래 [신규-7] | 상세화면(등록/수정/조회 팝업) | TASK-04 |

### [신규-1] ImageBoardVO.java

참고: `${param.sourceDir}/src/main/java/kr/co/gnx/sample/board/SampleBoardVO.java`

```java
package kr.co.gnx.sample.imageboard;

import kr.co.gnx.base.BaseVO;

/**
 * 이미지게시판 VO
 * seq / mb_id / use_yn / in_emp_cd / in_dtm / up_emp_cd / up_dtm / search_word / json_string 은 BaseVO에 있다.
 */
public class ImageBoardVO extends BaseVO {

	private String title;		/* 제목 */
	private String file_no;		/* 파일번호 */
	private String file_url;	/* 파일URL */
	private String writer_nm;	/* 등록자명 */

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getFile_no() {
		return file_no;
	}
	public void setFile_no(String file_no) {
		this.file_no = file_no;
	}
	public String getFile_url() {
		return file_url;
	}
	public void setFile_url(String file_url) {
		this.file_url = file_url;
	}
	public String getWriter_nm() {
		return writer_nm;
	}
	public void setWriter_nm(String writer_nm) {
		this.writer_nm = writer_nm;
	}
}
```

참고와 차이: 패키지 `sample.board`→`sample.imageboard`, 클래스명 `SampleBoardVO`→`ImageBoardVO`, 필드 `content`를 빼고 `file_no`/`file_url`을 추가(tbts_image_board 컬럼에 맞춤).

### [신규-2] ImageBoardDAO.java

참고: `${param.sourceDir}/src/main/java/kr/co/gnx/sample/board/SampleBoardDAO.java`

```java
package kr.co.gnx.sample.imageboard;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import kr.co.gnx.base.BaseDAO;

@Repository(value="ImageBoardDAO")
public class ImageBoardDAO extends BaseDAO {

	private static final String imageBoardMapper = "ImageBoard.";	//imageboard-mapper.xml

	public List<Map<String, Object>> selectImageBoardList(ImageBoardVO vo) {
		return getSqlSession().selectList(imageBoardMapper + "selectImageBoardList", vo);
	}

	public Map<String, Object> selectImageBoardView(ImageBoardVO vo) {
		return getSqlSession().selectOne(imageBoardMapper + "selectImageBoardView", vo);
	}

	public int insertImageBoard(ImageBoardVO vo) {
		return getSqlSession().insert(imageBoardMapper + "insertImageBoard", vo);
	}

	public int updateImageBoard(ImageBoardVO vo) {
		return getSqlSession().update(imageBoardMapper + "updateImageBoard", vo);
	}

	public int deleteImageBoard(ImageBoardVO vo) {
		return getSqlSession().update(imageBoardMapper + "deleteImageBoard", vo);
	}
}
```

참고와 차이: 패키지·클래스명·매퍼 namespace `SampleBoard.`→`ImageBoard.`, 메서드명 `SampleBoard`→`ImageBoard`. 삭제는 `use_yn='N'` UPDATE이므로 `getSqlSession().delete(...)` 대신 `update(...)` 사용.

### [신규-3] ImageBoardService.java

참고: `${param.sourceDir}/src/main/java/kr/co/gnx/sample/board/SampleBoardService.java`

```java
package kr.co.gnx.sample.imageboard;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kr.co.gnx.base.BaseService;

/**
 * 이미지게시판 서비스
 * 이름이 *Service 라서 context-transaction.xml 포인트컷에 걸린다 - 메서드 하나가 트랜잭션 하나.
 */
@Service
public class ImageBoardService extends BaseService {

	@Autowired
	private ImageBoardDAO imageBoardDAO;

	public List<Map<String, Object>> getImageBoardList(ImageBoardVO vo) {
		return imageBoardDAO.selectImageBoardList(vo);
	}

	public Map<String, Object> getImageBoardView(ImageBoardVO vo) {
		return imageBoardDAO.selectImageBoardView(vo);
	}

	public int insertImageBoard(ImageBoardVO vo) {
		return imageBoardDAO.insertImageBoard(vo);
	}

	public int updateImageBoard(ImageBoardVO vo) {
		return imageBoardDAO.updateImageBoard(vo);
	}

	public int deleteImageBoard(List<ImageBoardVO> models) {
		int cnt = 0;
		for(ImageBoardVO vo : models) {
			cnt += imageBoardDAO.deleteImageBoard(vo);
		}
		return cnt;
	}
}
```

참고와 차이: 패키지·클래스명·DAO/메서드명 `SampleBoard`→`ImageBoard`. 등록/수정은 단일 VO(팝업 1건 저장)로 단순화. 삭제는 체크된 여러 건을 받아 순회.

### [신규-4] ImageBoardController.java

참고: `${param.sourceDir}/src/main/java/kr/co/gnx/sample/board/SampleBoardController.java`, 업로드는 `${param.sourceDir}/src/main/java/kr/co/gnx/system/member/MemberController.java`(infoUrl.ajax 169-185줄)

```java
package kr.co.gnx.sample.imageboard;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import kr.co.gnx.base.BaseController;
import kr.co.gnx.comm.util.CommUtil;
import kr.co.gnx.comm.util.session.SessionUtil;
import kr.co.gnx.config.Constants.UPLOADS;
import kr.co.gnx.security.model.User;
import kr.co.gnx.system.file.FileVO;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * 이미지게시판 (테스트 메뉴용 CRUD 모듈)
 * 화면(JSP) → Controller → Service(트랜잭션) → DAO → MyBatis 매퍼 → MySQL 까지 한 바퀴 도는 걸
 * 확인하려고 만든 모듈이다. 등록/수정은 팝업 폼(단건), 삭제는 체크된 여러 건을 논리삭제(use_yn='N')한다.
 */
@Controller(value="ImageBoardController")
public class ImageBoardController extends BaseController {
	private static final Logger logger = LoggerFactory.getLogger(ImageBoardController.class);

	@Autowired
	private ImageBoardService imageBoardService;

	/**
	 * @brief 이미지게시판 목록화면 이동
	 */
	@RequestMapping(value = "/sample/imageboard/imageBoard.go")
	public ModelAndView imageBoard(HttpServletRequest request, HttpServletResponse response) throws Exception {
		return new ModelAndView("/sample/imageboard/imageBoard");
	}

	/**
	 * @brief 상세화면(등록/수정/조회) 팝업 이동
	 */
	@RequestMapping(value = "/sample/imageboard/imageBoardRegi.pop")
	public ModelAndView imageBoardRegi(HttpServletRequest request, HttpServletResponse response, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("/sample/imageboard/imageBoardRegi");
		User user = getLoginUser(request);
		if(CommUtil.isEmpty(vo.getWriter_nm())) {
			vo.setWriter_nm(user.getEmp_nm());
		}
		mv.addObject("ImageBoardVO", vo);
		return mv;
	}

	/**
	 * @brief 목록 조회 (search_word: 제목 검색어, 페이징)
	 */
	@RequestMapping(value = "/sample/imageboard/getImageBoardList.ajax")
	public ModelAndView getImageBoardList(HttpServletRequest request, HttpServletResponse response, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");

		if(CommUtil.isNotEmpty(vo.getJson_string())) {
			JSONObject paramJObj = JSONObject.fromObject(vo.getJson_string());
			vo = (ImageBoardVO) JSONObject.toBean(paramJObj, ImageBoardVO.class);
		}
		vo.setMb_id(getLoginUser(request).getMb_id());

		mv.addObject("results", imageBoardService.getImageBoardList(vo));
		return mv;
	}

	/**
	 * @brief 단건 조회 (seq 필수)
	 */
	@RequestMapping(value = "/sample/imageboard/getImageBoardView.ajax")
	public ModelAndView getImageBoardView(HttpServletRequest request, HttpServletResponse response, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");
		vo.setMb_id(getLoginUser(request).getMb_id());

		mv.addObject("result", imageBoardService.getImageBoardView(vo));
		return mv;
	}

	/**
	 * @brief 등록
	 */
	@RequestMapping(value = "/sample/imageboard/insertImageBoard.ajax")
	public ModelAndView insertImageBoard(HttpServletRequest request, HttpServletResponse response, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");
		User user = getLoginUser(request);
		vo.setMb_id(user.getMb_id());
		vo.setIn_emp_cd(user.getEmp_cd());
		if(CommUtil.isEmpty(vo.getWriter_nm())) {
			vo.setWriter_nm(user.getEmp_nm());
		}

		mv.addObject("count", imageBoardService.insertImageBoard(vo));
		return mv;
	}

	/**
	 * @brief 수정
	 */
	@RequestMapping(value = "/sample/imageboard/updateImageBoard.ajax")
	public ModelAndView updateImageBoard(HttpServletRequest request, HttpServletResponse response, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");
		User user = getLoginUser(request);
		vo.setMb_id(user.getMb_id());
		vo.setUp_emp_cd(user.getEmp_cd());

		mv.addObject("count", imageBoardService.updateImageBoard(vo));
		return mv;
	}

	/**
	 * @brief 삭제 (체크된 여러 건, 논리삭제 use_yn='N')
	 */
	@RequestMapping(value = "/sample/imageboard/deleteImageBoard.ajax")
	public ModelAndView deleteImageBoard(HttpServletRequest request, HttpServletResponse response, @RequestBody JSONObject paramJObj) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");
		User user = getLoginUser(request);
		List<ImageBoardVO> models = new ArrayList<ImageBoardVO>(
				(List<ImageBoardVO>) JSONArray.toCollection(paramJObj.getJSONArray("models"), ImageBoardVO.class));

		for(ImageBoardVO vo : models) {
			vo.setMb_id(user.getMb_id());
			vo.setUp_emp_cd(user.getEmp_cd());
		}

		mv.addObject("count", imageBoardService.deleteImageBoard(models));
		return mv;
	}

	/**
	 * @brief 이미지 파일 업로드 (kendoUpload saveUrl)
	 */
	@RequestMapping(value = "/sample/imageboard/imageUrl.ajax")
	public ModelAndView imageUrl(MultipartHttpServletRequest request, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");
		User user = getLoginUser(request);
		java.util.Iterator<String> itr = request.getFileNames();
		if (itr.hasNext()) {
			MultipartFile file = request.getFile(itr.next());
			FileVO fileVO = new FileVO();
			fileVO.setIn_emp_cd(user.getEmp_cd());
			fileVO.setMb_id(user.getMb_id());
			getFileService().setUserSession(user);
			fileVO = getFileService().imageSave(file, UPLOADS.UPIMAGE);
			mv.addObject("FileVO", fileVO);
		}
		return mv;
	}

	private User getLoginUser(HttpServletRequest request) {
		return SessionUtil.getSessionVO(request.getSession()).getUser();
	}
}
```

참고와 차이:
- 패키지·클래스명·Service/VO `SampleBoard`→`ImageBoard`.
- URL 전부 `/sample/imageboard/...`로 변경(승인 지시 A-1).
- 등록/수정은 `@RequestBody JSONObject`+`toModels` 대신 폼 바인딩 `ImageBoardVO`(단건, 팝업 폼)로 변경.
- `imageBoardRegi.pop` 진입점 추가(상세화면 팝업, 등록자 자동세팅).
- `imageUrl.ajax` 업로드 엔드포인트 추가(MemberController.infoUrl.ajax 패턴, `getFileService().imageSave(file, UPLOADS.UPIMAGE)`).
- 삭제는 체크된 여러 건 `{"models":[...]}`를 받아 논리삭제.

### [신규-5] imageboard-mapper.xml

참고: `${param.sourceDir}/src/main/resources/sqlmap/mapper/sample/sampleboard-mapper.xml`, 페이징 include는 `${param.sourceDir}/src/main/resources/sqlmap/mapper/comm/comm-mapper.xml`(Comm.PagingStart/PagingEnd)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<!--
	이미지게시판(tbts_image_board) - 테스트 메뉴용 CRUD.
	삭제는 실제 DELETE 대신 use_yn='N'으로만 바꾼다(삭제 후에도 DB에서 확인할 수 있도록).
-->
<mapper namespace="ImageBoard">
	<sql id="selectImageBoardSql">
		SELECT seq
		     , mb_id
		     , title
		     , file_no
		     , file_url
		     , writer_nm
		     , DATE_FORMAT(in_dtm, '%Y-%m-%d %H:%i:%s') AS in_dtm
		     , DATE_FORMAT(up_dtm, '%Y-%m-%d %H:%i:%s') AS up_dtm
		  FROM tbts_image_board
		 WHERE mb_id = #{mb_id}
		   AND use_yn = 'Y'
		<if test="@kr.co.gnx.comm.util.CommUtil@isNotEmpty(search_word)">
		   AND title LIKE CONCAT('%', #{search_word}, '%')
		</if>
	</sql>

	<select id="selectImageBoardList" parameterType="kr.co.gnx.sample.imageboard.ImageBoardVO" resultType="bmap">
		/* imageboard-mapper.xml id="selectImageBoardList" 이미지게시판 목록 조회(페이징) */
		<include refid="Comm.PagingStart"/>
		<include refid="selectImageBoardSql"/>
		ORDER BY seq DESC
		<include refid="Comm.PagingEnd"/>
	</select>

	<select id="selectImageBoardView" parameterType="kr.co.gnx.sample.imageboard.ImageBoardVO" resultType="bmap">
		/* imageboard-mapper.xml id="selectImageBoardView" 이미지게시판 단건 조회 */
		SELECT seq
		     , mb_id
		     , title
		     , file_no
		     , file_url
		     , writer_nm
		     , DATE_FORMAT(in_dtm, '%Y-%m-%d %H:%i:%s') AS in_dtm
		     , DATE_FORMAT(up_dtm, '%Y-%m-%d %H:%i:%s') AS up_dtm
		  FROM tbts_image_board
		 WHERE mb_id = #{mb_id}
		   AND seq   = #{seq}
		   AND use_yn = 'Y'
	</select>

	<insert id="insertImageBoard" parameterType="kr.co.gnx.sample.imageboard.ImageBoardVO">
		/* imageboard-mapper.xml id="insertImageBoard" 이미지게시판 등록 */
		INSERT
		  INTO tbts_image_board
		     ( mb_id
		     , title
		     , file_no
		     , file_url
		     , writer_nm
		     , use_yn
		     , in_emp_cd
		     , in_dtm
		     )
		VALUES
		     ( #{mb_id}
		     , #{title}
		     , #{file_no}
		     , #{file_url}
		     , #{writer_nm}
		     , 'Y'
		     , #{in_emp_cd}
		     , NOW()
		     )
	</insert>

	<update id="updateImageBoard" parameterType="kr.co.gnx.sample.imageboard.ImageBoardVO">
		/* imageboard-mapper.xml id="updateImageBoard" 이미지게시판 수정 */
		UPDATE tbts_image_board
		   SET title     = #{title}
		     , file_no   = #{file_no}
		     , file_url  = #{file_url}
		     , writer_nm = #{writer_nm}
		     , up_emp_cd = #{up_emp_cd}
		     , up_dtm    = NOW()
		 WHERE mb_id = #{mb_id}
		   AND seq   = #{seq}
	</update>

	<update id="deleteImageBoard" parameterType="kr.co.gnx.sample.imageboard.ImageBoardVO">
		/* imageboard-mapper.xml id="deleteImageBoard" 이미지게시판 삭제(use_yn='N') */
		UPDATE tbts_image_board
		   SET use_yn    = 'N'
		     , up_emp_cd = #{up_emp_cd}
		     , up_dtm    = NOW()
		 WHERE mb_id = #{mb_id}
		   AND seq   = #{seq}
	</update>
</mapper>
```

참고와 차이: namespace `SampleBoard`→`ImageBoard`, 테이블 `tbts_sample_board`→`tbts_image_board`, 컬럼 `content`를 `file_no`/`file_url`로 대체, 검색은 제목(title)만 LIKE. 목록은 `Comm.PagingStart/PagingEnd` include로 서버 페이징. 삭제는 `use_yn='N'` UPDATE(논리삭제, 승인 지시 B).

### [신규-6] imageBoard.jsp (목록화면)

참고: `${param.sourceDir}/src/main/webapp/WEB-INF/views/sample/board/sampleBoard.jsp`(그리드), `${param.sourceDir}/src/main/webapp/WEB-INF/views/system/member/member.jsp`(팝업 genexon.PopWindowOpen 29-58줄, serverPaging 165줄, 이미지 컬럼 template 253-269줄)

```jsp
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
	var selectedItem = this.dataItem($(e.target).closest("tr"));
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
	var item = this.dataItem($(e.target).closest("tr"));
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
```

참고와 차이:
- 검색조건은 제목(`search_word`)만.
- 기능버튼: 전체선택/등록/삭제 추가(샘플게시판의 인라인 추가/수정/삭제 대신).
- 그리드 컬럼: 체크박스(rowChk), SEQ, 썸네일(file_url img), 제목(클릭→조회 팝업), 등록자.
- 인라인 편집 대신 `editable:false`, `pageable:true`+`serverPaging:true`(서버 페이징, member.jsp 패턴).
- 썸네일 클릭→`imageThumbView`(이미지 팝업), 제목 클릭→`imageBoardView`(상세 팝업 조회모드).
- 삭제는 체크된 seq를 모아 `deleteImageBoard.ajax`로 전송.

### [신규-7] imageBoardRegi.jsp (상세화면 팝업)

참고: `${param.sourceDir}/src/main/webapp/WEB-INF/views/system/member/memberRegi.jsp`(폼+ajaxForm 123줄, kendoUpload 276-322줄, genexon.PopWindowClose2 26줄)

```jsp
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
```

참고와 차이:
- 폼 항목은 제목/등록자(readonly, 로그인 사용자명 자동세팅)/파일업로드버튼만.
- 파일업로드는 kendoUpload 1개(`img_name`, saveUrl `/sample/imageboard/imageUrl.ajax`, .jpg/.png 제한, success 시 file_no/file_url hidden 세팅).
- 저장/수정 버튼은 seq 유무로 동작 전환(등록→insertImageBoard.ajax, 수정→updateImageBoard.ajax).
- 닫기는 `genexon.PopWindowClose2("#imageBoardRegi")`.

## API 스펙 변경

신규 엔드포인트(모두 `ImageBoardController`):

| URL | Method | 설명 |
|---|---|---|
| /sample/imageboard/imageBoard.go | GET | 목록화면 이동 |
| /sample/imageboard/imageBoardRegi.pop | GET | 상세화면 팝업 이동(등록자 자동세팅) |
| /sample/imageboard/getImageBoardList.ajax | GET | 목록 조회(제목 검색, 서버 페이징) |
| /sample/imageboard/getImageBoardView.ajax | GET | 단건 조회 |
| /sample/imageboard/insertImageBoard.ajax | POST | 등록 |
| /sample/imageboard/updateImageBoard.ajax | POST | 수정 |
| /sample/imageboard/deleteImageBoard.ajax | POST | 삭제(체크된 여러 건, `{"models":[{seq}]}`, 논리삭제) |
| /sample/imageboard/imageUrl.ajax | POST | 이미지 업로드(kendoUpload, Multipart) |

## DB 변경(DDL)

해당 없음. 기존 테이블 `tbts_image_board`를 그대로 사용한다(승인 지시 A-2/A-3). 새 테이블·컬럼·메뉴 등록 없음.

## 예외 처리 방침

- 등록/수정: 제목 필수(화면에서 빈값 확인 후 저장 차단). 파일 미선택 시 file_no/file_url은 빈값으로 저장.
- 삭제: 체크된 항목 없으면 "삭제할 게시물을 선택하세요" 안내. 확인 다이얼로그 후 논리삭제.
- 업로드: 확장자 .jpg/.png 제한(kendoUpload validation). 실패 시 "전송 실패" 표시.
- 서버 오류: ajax error 시 "에러가 발생했습니다" 안내.

## TASK별 확인 방법

- **TASK-01 (목록화면):** 테스트>이미지게시판 메뉴 진입 → 목록화면 표시. 게시물 20건 초과 시 페이지 넘김(서버 페이징). 테이블에 체크박스/SEQ/썸네일/제목/등록자 표시. 제목 입력 후 검색 → 해당 제목만 조회.
- **TASK-02 (기능버튼):** 전체선택 클릭 → 모든 체크박스 체크(다시 클릭 시 해제). 등록 클릭 → 상세 팝업 편집모드 노출. 체크 후 삭제 클릭 → 확인 → 체크된 게시물 목록에서 사라짐(use_yn='N').
- **TASK-03 (이벤트):** 제목 클릭 → 상세 팝업 조회모드 노출(내용 표시). 썸네일 클릭 → 이미지 팝업 노출.
- **TASK-04 (상세화면):** 팝업에 제목/등록자/파일업로드버튼 표시. 등록자가 로그인 사용자명으로 자동 세팅(readonly). 파일업로드버튼 클릭 → 로컬에서 .jpg/.png 선택 가능. 저장 클릭 → 등록 내용 저장(목록에 신규 표시). 수정(제목 클릭 후) → 내용 변경 후 수정 클릭 → 변경 내용 저장.

## 회귀 테스트 대상

- 영향도분석서상 기존 소스·SQL·화면 변경 없음(신규 파일만 추가). 기존 샘플게시판·회원사관리 동작에 영향 없음.
- `mapperLocations=classpath*:/sqlmap/mapper/**/*-mapper.xml`에 신규 `imageboard-mapper.xml`이 추가되므로, 다른 namespace와 충돌 없는지(빌드/시작 시 MyBatis 매퍼 등록 정상) 확인.
- `getFileService().imageSave`는 기존 공용 서비스이므로 회원사관리 이미지 업로드와 동일 경로(UPLOADS.UPIMAGE) 사용 — 회원사관리 이미지 업로드 회귀 확인.

## 결정 필요 사항

- 없음. (승인 지시 A-1/A-2/A-3/B 반영: 메뉴 URL에 맞춰 경로 설정, 기존 테이블 사용, 논리삭제)

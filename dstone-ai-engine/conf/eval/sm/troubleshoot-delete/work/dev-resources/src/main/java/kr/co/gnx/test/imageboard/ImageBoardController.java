package kr.co.gnx.test.imageboard;

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
import kr.co.gnx.security.model.User;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * 이미지 게시판 (테스트 메뉴)
 * 목록화면 + 상세화면(팝업, 편집/조회 모드).
 * 등록/수정은 multipart 폼(제목 + 이미지 파일), 삭제는 seq 배열 JSON.
 */
@Controller(value="ImageBoardController")
public class ImageBoardController extends BaseController {
	private static final Logger logger = LoggerFactory.getLogger(ImageBoardController.class);

	@Autowired
	private ImageBoardService imageBoardService;

	/**
	 * @brief 이미지 게시판 목록화면 이동
	 */
	@RequestMapping(value = "/test/imageboard/imageBoard.go")
	public ModelAndView imageBoard(HttpServletRequest request, HttpServletResponse response) throws Exception {
		return new ModelAndView("/test/imageboard/imageBoard");
	}

	/**
	 * @brief 상세화면 팝업 이동 (seq 있으면 조회/수정 모드, 없으면 편집 모드)
	 */
	@RequestMapping(value = "/test/imageboard/imageBoardDetail.pop")
	public ModelAndView imageBoardDetail(HttpServletRequest request, HttpServletResponse response, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("/test/imageboard/imageBoardDetail");
		if (CommUtil.isNotEmpty(vo.getJson_string())) {
			JSONObject paramJObj = JSONObject.fromObject(vo.getJson_string());
			vo = (ImageBoardVO) JSONObject.toBean(paramJObj, ImageBoardVO.class);
		}
		if (vo.getSeq() != null) {
			vo.setMb_id(getLoginUser(request).getMb_id());
			mv.addObject("ImageBoardVO", imageBoardService.getImageBoardView(vo));
		} else {
			mv.addObject("ImageBoardVO", vo);
		}
		return mv;
	}

	/**
	 * @brief 목록 조회 (search_word: 제목 검색어)
	 */
	@RequestMapping(value = "/test/imageboard/getImageBoardList.ajax")
	public ModelAndView getImageBoardList(HttpServletRequest request, HttpServletResponse response, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");

		// 그리드는 검색조건을 json_string 하나로 묶어 보낸다
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
	@RequestMapping(value = "/test/imageboard/getImageBoardView.ajax")
	public ModelAndView getImageBoardView(HttpServletRequest request, HttpServletResponse response, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");
		vo.setMb_id(getLoginUser(request).getMb_id());

		mv.addObject("result", imageBoardService.getImageBoardView(vo));
		return mv;
	}

	/**
	 * @brief 등록/수정 (multipart 폼: title, image_file. seq 있으면 수정)
	 */
	@RequestMapping(value = "/test/imageboard/saveImageBoard.ajax")
	public ModelAndView saveImageBoard(MultipartHttpServletRequest request, ImageBoardVO vo) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");
		User user = getLoginUser(request);

		MultipartFile file = request.getFile("image_file");
		vo.setMb_id(user.getMb_id());
		vo.setIn_emp_cd(user.getEmp_cd());
		vo.setUp_emp_cd(user.getEmp_cd());
		if(CommUtil.isEmpty(vo.getWriter_nm())) {
			vo.setWriter_nm(user.getEmp_nm());
		}

		mv.addObject("count", imageBoardService.saveImageBoard(file, vo));
		return mv;
	}

	/**
	 * @brief 삭제 (JSON 본문: {"seqList":[seq,...]}, 물리 삭제)
	 */
	@RequestMapping(value = "/test/imageboard/deleteImageBoard.ajax")
	public ModelAndView deleteImageBoard(HttpServletRequest request, HttpServletResponse response, @RequestBody JSONObject paramJObj) throws Exception {
		ModelAndView mv = new ModelAndView("jsonView");
		User user = getLoginUser(request);

		JSONArray seqArr = paramJObj.getJSONArray("seqList");
		List<Long> seqList = new ArrayList<Long>();
		for (int i = 0; i < seqArr.size(); i++) {
			seqList.add(seqArr.getLong(i));
		}

		mv.addObject("count", imageBoardService.deleteImageBoard(seqList));
		return mv;
	}

	private User getLoginUser(HttpServletRequest request) {
		return SessionUtil.getSessionVO(request.getSession()).getUser();
	}
}

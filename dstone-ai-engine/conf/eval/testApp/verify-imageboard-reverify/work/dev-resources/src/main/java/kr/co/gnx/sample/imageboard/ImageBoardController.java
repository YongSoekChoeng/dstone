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

		// 조회/수정 팝업은 genexon.PopWindowOpen의 data를 json_string으로 보낸다 - 그 값을 VO로 풀어 채운다.
		if(CommUtil.isNotEmpty(vo.getJson_string())) {
			JSONObject paramJObj = JSONObject.fromObject(vo.getJson_string());
			vo = (ImageBoardVO) JSONObject.toBean(paramJObj, ImageBoardVO.class);
		}
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

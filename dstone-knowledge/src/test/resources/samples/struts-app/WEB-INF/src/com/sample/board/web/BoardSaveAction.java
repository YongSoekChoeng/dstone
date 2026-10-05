package com.sample.board.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.sample.board.dao.BoardDAO;
import com.sample.board.vo.BoardVO;

/**
 * 게시글 저장
 */
public class BoardSaveAction extends Action {

	private BoardDAO boardDAO = new BoardDAO();

	public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
		String title = request.getParameter("title");
		if (title == null || title.length() == 0) {
			return mapping.findForward("fail");
		}
		BoardVO board = new BoardVO();
		board.setTitle(title);
		board.setWriterId(request.getParameter("writerId"));
		boardDAO.insertBoard(board);
		return mapping.findForward("success");
	}

}

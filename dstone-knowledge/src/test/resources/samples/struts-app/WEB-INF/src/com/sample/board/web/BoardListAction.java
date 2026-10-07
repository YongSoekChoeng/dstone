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
 * 게시글 목록
 */
public class BoardListAction extends Action {

	private BoardDAO boardDAO = new BoardDAO();

	public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
		BoardVO condition = new BoardVO();
		condition.setTitle(request.getParameter("title"));
		request.setAttribute("boards", boardDAO.selectBoardList(condition));
		request.setAttribute("total", new Integer(boardDAO.countBoard()));
		return mapping.findForward("success");
	}

}

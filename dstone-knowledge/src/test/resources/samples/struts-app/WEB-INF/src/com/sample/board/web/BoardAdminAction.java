package com.sample.board.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.actions.DispatchAction;

import com.sample.board.dao.BoardDAO;

/**
 * 관리자 기능. /board/admin.do?cmd=delete 처럼 요청 파라미터의 값이 곧 메소드 이름이다.
 * execute 가 없으므로 "어느 메소드가 불리는지" 는 설정만 봐서는 알 수 없다.
 */
public class BoardAdminAction extends DispatchAction {

	private BoardDAO boardDAO = new BoardDAO();

	public ActionForward delete(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
		boardDAO.deleteBoard(request.getParameter("boardId"));
		return null;
	}

}

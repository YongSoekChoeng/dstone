package com.sample.board.dao;

import java.util.List;

import org.springframework.orm.ibatis.support.SqlMapClientDaoSupport;

import com.sample.board.vo.BoardVO;

/**
 * 게시글 DAO. iBATIS 의 SQL 을 이름(문자열)으로 부른다.
 */
public class BoardDAO extends SqlMapClientDaoSupport {

	/** SQL 이름 앞에 붙는 네임스페이스. 상수로 빼 두고 이어 붙여 쓴다 */
	private static final String NS = "Board.";

	public List selectBoardList(BoardVO condition) {
		return getSqlMapClientTemplate().queryForList(NS + "selectBoardList", condition);
	}

	/** 네임스페이스 없이 id 만 넘기는 방식 */
	public int countBoard() {
		return ((Integer) getSqlMapClientTemplate().queryForObject("countBoard")).intValue();
	}

	public void insertBoard(BoardVO board) {
		getSqlMapClientTemplate().insert(NS + "insertBoard", board);
	}

	public void deleteBoard(String boardId) {
		getSqlMapClientTemplate().delete("Board.deleteBoard", boardId);
	}

}

package shop.dao;

import jef.application.dao.jdbc.JdbcDAO;
import shop.framework.QueryProperty;

/** SQL의 이름을 객체(QueryProperty)에 담아 넘기는 DAO. SQL은 같은 폴더의 OrderD.xml 에 있다 */
public class OrderD extends JdbcDAO {

	public Object selectOrderList(Object orderVO) {
		QueryProperty qp = new QueryProperty("OrderD.selectOrderList");
		return execute(qp, orderVO);
	}

	public Object deleteOrder(Object orderVO) {
		QueryProperty qp = new QueryProperty("OrderD.deleteOrder");
		return execute(qp, orderVO);
	}

	/** 쿼리 파일에 없는 이름: 사실 그대로 "못 찾음"으로 남아야 한다 */
	public Object selectGone(Object orderVO) {
		return execute(new QueryProperty("OrderD.selectGone"), orderVO);
	}

	/** 이름을 변수로 넘김: 잇지 않는다 */
	public Object selectByName(String queryName, Object orderVO) {
		return execute(new QueryProperty(queryName), orderVO);
	}

	private Object execute(QueryProperty qp, Object param) {
		return null;
	}
}

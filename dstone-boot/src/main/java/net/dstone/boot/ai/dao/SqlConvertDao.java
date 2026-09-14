package net.dstone.boot.ai.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import net.dstone.boot.ai.vo.SqlConvertVo;

@Repository
public class SqlConvertDao extends net.dstone.boot.common.biz.BaseDao {

	public void insertHistory(SqlConvertVo sqlConvertVo) throws Exception {
		sqlSessionCommon.insert("net.dstone.boot.ai.dao.SqlConvertDao.insertHistory", sqlConvertVo);
	}

	public List<SqlConvertVo> listHistory() throws Exception {
		return sqlSessionCommon.selectList("net.dstone.boot.ai.dao.SqlConvertDao.listHistory");
	}

}

package net.dstone.knowledge.api.dao;

import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * 모듈 자체의 상태(DB 연결, 스키마 준비 여부)를 확인하는 Dao입니다.
 */
@Repository("systemDao")
public class SystemDao extends BaseDao {

	/**
	 * DB 이름, pgvector 버전, 준비된 테이블 수를 한 번에 조회합니다.
	 */
	public Map<String, Object> selectDbStatus() throws Exception {
		return sqlSessionCommon.selectOne("net.dstone.knowledge.api.dao.SystemDao.selectDbStatus");
	}

}

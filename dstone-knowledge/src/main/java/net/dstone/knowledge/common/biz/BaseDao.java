package net.dstone.knowledge.common.biz;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/**
 * <pre>
 * 이 모듈의 모든 Dao가 상속하는 부모 클래스입니다.
 * </pre>
 */
@Repository
public class BaseDao extends net.dstone.common.biz.BaseDao {

	/** 일반 조회/단건 변경용 */
	@Autowired
	@Qualifier("sqlSessionCommon")
	protected SqlSessionTemplate sqlSessionCommon;

	/** 대량 저장용(같은 SQL을 모아서 한 번에 보냄). 트랜잭션 안에서만 사용합니다. */
	@Autowired
	@Qualifier("sqlSessionBatch")
	protected SqlSessionTemplate sqlSessionBatch;

}

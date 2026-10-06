package net.dstone.ai.common.biz;

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
public abstract class BaseDao extends net.dstone.common.biz.BaseDao {

	/** MyBatis 세션(common.config.ConfigMapper가 만듭니다) */
	@Autowired
	@Qualifier("sqlSessionCommon")
	protected SqlSessionTemplate sqlSessionCommon;

}

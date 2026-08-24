package net.dstone.batchadmin.common.biz;

import java.util.function.Supplier;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import net.dstone.batchadmin.common.datasource.RoutingDataSourceContextHolder;

@Repository
public class BaseDao extends net.dstone.common.biz.BaseDao {

	/** batchadmin 자체 스키마(로그인사용자/배치서버레지스트리/Job메타데이터) 조회용 */
	@Autowired
	@Qualifier("sqlSessionCommon")
	protected SqlSessionTemplate sqlSessionCommon;

	/** 관리대상 dstone-batch 서버의 배치 메타데이터DB(BATCH_JOB_*) 조회용. 반드시 executeOnServer()로 감싸서 사용. */
	@Autowired
	@Qualifier("sqlSessionBatch")
	protected SqlSessionTemplate sqlSessionBatch;

	/**
	 * 지정한 배치서버(SERVER_ID)의 데이터소스로 라우팅한 상태에서 콜백을 수행한다.
	 * @param serverId TB_BATCH_SERVER.SERVER_ID
	 * @param callback sqlSessionBatch를 사용하는 조회/변경 로직
	 */
	protected <T> T executeOnServer(Long serverId, Supplier<T> callback) {
		try {
			RoutingDataSourceContextHolder.setServerId(serverId);
			return callback.get();
		} finally {
			RoutingDataSourceContextHolder.clear();
		}
	}

}

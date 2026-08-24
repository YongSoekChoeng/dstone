package net.dstone.batchadmin.common.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 관리 대상 dstone-batch 서버(TB_BATCH_SERVER)별 배치 메타데이터DB로 라우팅하는 DataSource.
 * 실제 대상 DataSource 맵은 BatchServerDataSourceRegistry 가 구성/갱신한다.
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

	@Override
	protected Object determineCurrentLookupKey() {
		return RoutingDataSourceContextHolder.getServerId();
	}

}

package net.dstone.batchadmin.common.datasource;

/**
 * 현재 스레드가 대상으로 하는 배치서버(TB_BATCH_SERVER.SERVER_ID)를 보관하는 ThreadLocal.
 * RoutingDataSource.determineCurrentLookupKey() 에서 이 값을 읽어 실제 커넥션을 라우팅한다.
 */
public class RoutingDataSourceContextHolder {

	private static final ThreadLocal<Long> CONTEXT_HOLDER = new ThreadLocal<Long>();

	public static void setServerId(Long serverId) {
		CONTEXT_HOLDER.set(serverId);
	}

	public static Long getServerId() {
		return CONTEXT_HOLDER.get();
	}

	public static void clear() {
		CONTEXT_HOLDER.remove();
	}

}

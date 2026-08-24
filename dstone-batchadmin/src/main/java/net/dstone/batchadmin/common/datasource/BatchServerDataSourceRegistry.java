package net.dstone.batchadmin.common.datasource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PostConstruct;
import net.dstone.batchadmin.server.BatchServerDao;
import net.dstone.batchadmin.server.vo.BatchServerVo;
import net.dstone.common.core.BaseObject;

/**
 * TB_BATCH_SERVER 에 등록된 배치서버별로 배치 메타데이터DB(HikariDataSource)를 런타임에 구성/캐싱하여
 * RoutingDataSource(common/config/ConfigDatasource.java 에서 빈으로 등록됨)의 대상 DataSource맵을 채운다.
 * 서버 등록/수정/삭제/사용여부 변경 후에는 반드시 refresh()를 호출해야 한다.
 */
@Component
public class BatchServerDataSourceRegistry extends BaseObject {

	@Autowired
	private BatchServerDao batchServerDao;

	@Autowired
	@Qualifier("routingDataSourceBatch")
	private RoutingDataSource routingDataSource;

	@Autowired
	@Qualifier("jasyptStringEncryptor")
	private StringEncryptor stringEncryptor;

	private final Map<Long, HikariDataSource> dataSourceMap = new HashMap<Long, HikariDataSource>();

	@PostConstruct
	public synchronized void refresh() throws Exception {
		this.info(this.getClass().getName() + ".refresh() has been called !!!");

		Map<Long, HikariDataSource> oldDataSourceMap = new HashMap<Long, HikariDataSource>(dataSourceMap);
		Map<Object, Object> targetDataSources = new HashMap<Object, Object>();

		List<BatchServerVo> serverList = batchServerDao.listActiveServerWithPassword();

		dataSourceMap.clear();
		if (serverList != null) {
			for (BatchServerVo server : serverList) {
				HikariDataSource ds = buildDataSource(server);
				dataSourceMap.put(server.getSERVER_ID(), ds);
				targetDataSources.put(server.getSERVER_ID(), ds);
			}
		}

		routingDataSource.setTargetDataSources(targetDataSources);
		routingDataSource.setDefaultTargetDataSource(null);
		routingDataSource.afterPropertiesSet();

		// 이전 커넥션풀은 새 라우팅맵으로 교체가 끝난 뒤 종료
		for (HikariDataSource oldDs : oldDataSourceMap.values()) {
			try {
				oldDs.close();
			} catch (Exception e) {
				// ignore
			}
		}
	}

	private HikariDataSource buildDataSource(BatchServerVo server) {
		String dbmsType = server.getDBMS_TYPE() == null ? "MYSQL" : server.getDBMS_TYPE().trim().toUpperCase();
		HikariConfig config = new HikariConfig();
		String password = server.getDB_PASSWORD();
		if (password != null && password.startsWith("ENC(") && password.endsWith(")")) {
			password = stringEncryptor.decrypt(password.substring(4, password.length() - 1));
		}
		if ("POSTGRES".equals(dbmsType)) {
			config.setDriverClassName("org.postgresql.Driver");
			config.setJdbcUrl("jdbc:postgresql://" + server.getDB_HOST() + ":" + server.getDB_PORT() + "/" + server.getDB_NAME());
		} else {
			config.setDriverClassName("net.sf.log4jdbc.sql.jdbcapi.DriverSpy");
			config.setJdbcUrl("jdbc:log4jdbc:mysql://" + server.getDB_HOST() + ":" + server.getDB_PORT() + "/" + server.getDB_NAME()
					+ "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul");
		}
		config.setUsername(server.getDB_USER());
		config.setPassword(password);
		config.setMinimumIdle(1);
		config.setMaximumPoolSize(10);
		config.setConnectionTimeout(30000);
		config.setPoolName("batch-server-" + server.getSERVER_ID());
		return new HikariDataSource(config);
	}

	public DataSource get(Long serverId) {
		return dataSourceMap.get(serverId);
	}

}

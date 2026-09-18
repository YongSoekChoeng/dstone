package net.dstone.batch.common.config;

import java.lang.reflect.Field;
import java.sql.Connection;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.batch.common.consts.Constants;
import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.batch.common.partitioner.QueryPartitioner;
import net.dstone.common.utils.StringUtil;


@Configuration
public class ConfigInterceptor extends BaseBatchObject{

    @Bean
    public Interceptor myBatisPartitionSqlInterceptor() {
        return new MyBatisPartitionSqlInterceptor();
    }
    
	/**
	 * MyBatis Partition용 쿼리수행 시 적용될 Interceptor
	 */
	@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
	})
	public class MyBatisPartitionSqlInterceptor implements Interceptor {
		@SuppressWarnings("rawtypes")
		@Override
		public Object intercept(Invocation invocation) throws Throwable {
			
	    	log(this.getClass().getName() + ".intercept() has been called !!! - 쓰레드명[" + Thread.currentThread().getName() + "]" );
	        
        	Object result = null;
        	org.apache.ibatis.executor.statement.RoutingStatementHandler target = (org.apache.ibatis.executor.statement.RoutingStatementHandler)invocation.getTarget();
        	org.apache.ibatis.mapping.BoundSql boundSql = target.getBoundSql();
        	
        	boolean isPartitionApplied = false;
        	String originalSql = ""; 
        	String keyColumn = ""; 
        	int gridSize = 0;
        	
        	Object paramObj = boundSql.getParameterObject();
        	if( paramObj != null && paramObj instanceof java.util.Map ) {
        		java.util.Map paramObjMap = (java.util.Map)paramObj;
        		if( paramObjMap.containsKey(Constants.Partition.PARTITION_YN) && "Y".equals(paramObjMap.get(Constants.Partition.PARTITION_YN)) ) {
        			isPartitionApplied = true;
            		if( !paramObjMap.containsKey(Constants.Partition.KEY_COLUMN) || StringUtil.isEmpty(paramObjMap.get(Constants.Partition.KEY_COLUMN)) ) {
            			throw new Exception("Partition 을 위한 KEY_COLUMN 이 정의되어 있지 않습니다");
            		}
            		if( !paramObjMap.containsKey(Constants.Partition.GRID_SIZE) || !StringUtil.isNumber(paramObjMap.get(Constants.Partition.GRID_SIZE).toString()) ) {
            			throw new Exception("Partition 을 위한 GRID_SIZE 가 정의되어 있지 않습니다");
            		}
        			originalSql = boundSql.getSql();
        			keyColumn = paramObjMap.get(Constants.Partition.KEY_COLUMN).toString(); 
        			gridSize = Integer.parseInt(paramObjMap.get(Constants.Partition.GRID_SIZE).toString());
        		}
        	}
        	if( isPartitionApplied ) {
                // SQL을 재정의
                String partitionQuery = QueryPartitioner.getPartitionQuery(originalSql, keyColumn, gridSize);
                Field sqlField = boundSql.getClass().getDeclaredField("sql");
                sqlField.setAccessible(true);
                sqlField.set(boundSql, partitionQuery);
        	}
        	result = invocation.proceed();
            return result;
		}
	}
	
	
	
	
	
}

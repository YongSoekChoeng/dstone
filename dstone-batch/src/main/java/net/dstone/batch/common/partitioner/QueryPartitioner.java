package net.dstone.batch.common.partitioner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.batch.item.ExecutionContext;

import net.dstone.batch.common.consts.Constants;
import net.dstone.batch.common.core.BasePartitioner;

/**
 * 범용 쿼리 기반 Partitioner
 */
public class QueryPartitioner extends BasePartitioner {

    private final SqlSessionTemplate sqlSessionTemplate;
    private final String queryId;
    private final String keyColumn;
    private int gridSize = 0;
    private final Map<String, Object> params = new HashMap<String, Object>();

    public QueryPartitioner(
    		SqlSessionTemplate sqlSessionTemplate,
            String queryId,
            String keyColumn,
            int gridSize
    ) {
        this(sqlSessionTemplate, queryId, keyColumn, gridSize, new HashMap<>());
    }

    public QueryPartitioner(
    		SqlSessionTemplate sqlSessionTemplate,
            String queryId,
            String keyColumn,
            int gridSize,
            Map<String, Object> params
     ) {
    	
        this.sqlSessionTemplate = sqlSessionTemplate;
        this.queryId = queryId;
        this.keyColumn = keyColumn;
        this.gridSize = gridSize;
        if( params!= null && params.size()>0) {
        	this.params.putAll(params);
        }
        
        this.params.put(Constants.Partition.PARTITION_YN, "Y");
        this.params.put(Constants.Partition.KEY_COLUMN, this.keyColumn);
        this.params.put(Constants.Partition.GRID_SIZE, this.gridSize);
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {

    	callLog(this, "doPartition", String.valueOf(gridSize));
        
    	int actualGridSize = this.gridSize > 0 ? this.gridSize : gridSize;
        this.params.put(Constants.Partition.GRID_SIZE, actualGridSize);
    	Map<String, ExecutionContext> result = new HashMap<String, ExecutionContext>();
        try {
            List<Map<String, Object>> partitionList = this.sqlSessionTemplate.selectList(this.queryId, this.params);
            if( partitionList != null && partitionList.size() > 0 ) {
            	for(Map<String, Object> row : partitionList) {
            		ExecutionContext context = new ExecutionContext();
            		context.put("MIN_ID", row.get("MIN_ID"));
            		context.put("MAX_ID", row.get("MAX_ID"));
            		result.put("partition"+row.get("PARTITION_NO").toString(), context);
            	}
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create query-based partitions", e);
        } 
    	log(this.getClass().getName() + ".partition() return ["+result+"]" );
        return result;
    }
    
    public static String getPartitionQuery(String originalQuery, String keyColumn, int gridSize) {
    	StringBuffer query = new StringBuffer();
    	StringBuffer upperSql = new StringBuffer();
    	StringBuffer lowerSql = new StringBuffer();
    	
    	upperSql.append("SELECT ").append("\n");
    	upperSql.append("    PARTITION_NO, ").append("\n");
    	upperSql.append("    MIN("+keyColumn+") AS MIN_ID, ").append("\n");
    	upperSql.append("    MAX("+keyColumn+") AS MAX_ID ").append("\n");
    	upperSql.append("FROM ( ").append("\n");
    	upperSql.append("    SELECT ").append("\n");
    	upperSql.append("        Original."+keyColumn+", ").append("\n");
    	upperSql.append("        (CEIL(ROW_NUMBER() OVER (ORDER BY Original."+keyColumn+") / (COUNT(0) OVER ()/"+ gridSize +"))-1) AS PARTITION_NO ").append("\n");
    	upperSql.append("    FROM  ").append("\n");
    	upperSql.append("		( ").append("\n");
    	upperSql.append("		/*** Original SQL Start ***/ ").append("\n");
    			
    	lowerSql.append("		/*** Original SQL End ***/ ").append("\n");
    	lowerSql.append("		) Original ").append("\n");
    	lowerSql.append(") A ").append("\n");
    	lowerSql.append("GROUP BY PARTITION_NO ").append("\n");
    	lowerSql.append("ORDER BY PARTITION_NO ").append("\n");
    	
    	query.append(upperSql).append("\n");
    	query.append(originalQuery).append("\n");
    	query.append(lowerSql).append("\n");
    	
    	return query.toString();
    }
}

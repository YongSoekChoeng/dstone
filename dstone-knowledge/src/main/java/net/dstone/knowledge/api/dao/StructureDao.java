package net.dstone.knowledge.api.dao;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 리비전의 "현행 구조"(계층별 개수, 업무 영역별 규모)를 읽는 Dao입니다. 읽기만 합니다.
 * 패키지 / 폴더 단위로 센 행을 돌려주고, 업무 영역으로 묶는 일은 StructureService 가 합니다.
 * </pre>
 */
@Repository("structureDao")
public class StructureDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.StructureDao.";

	/** 계층별 개수(screens, controllers, services, daos, mappers, sqlStatements, tables). 노드 맵의 계층 칸과 같은 기준 */
	public Map<String, Object> selectLayerCounts(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectLayerCounts", revisionId);
	}

	/** {key: 패키지, layer, count} */
	public List<Map<String, Object>> selectClassesByPackage(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectClassesByPackage", revisionId);
	}

	/** {key: 폴더, count} */
	public List<Map<String, Object>> selectScreensByFolder(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectScreensByFolder", revisionId);
	}

	/** {key: 패키지, count} */
	public List<Map<String, Object>> selectEndpointsByPackage(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEndpointsByPackage", revisionId);
	}

	/** {key: 매퍼 폴더, count} */
	public List<Map<String, Object>> selectStatementsByFolder(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectStatementsByFolder", revisionId);
	}

	/** {key: 매퍼 폴더, table} */
	public List<Map<String, Object>> selectTablesByFolder(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectTablesByFolder", revisionId);
	}

	/** {type: JSP / WEBSQUARE / NEXACRO / VUE / REACT, count} */
	public List<Map<String, Object>> selectScreenTypes(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectScreenTypes", revisionId);
	}

}

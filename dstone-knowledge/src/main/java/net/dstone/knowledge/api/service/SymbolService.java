package net.dstone.knowledge.api.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.DeclarationDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 분석으로 찾아낸 타입(심볼)을 조회합니다.
 * </pre>
 */
@Service
public class SymbolService extends BaseObject {

	private static final int MAX_PAGE_SIZE = 500;

	@Autowired
	private DeclarationDao declarationDao;

	/**
	 * <pre>
	 * 타입 목록.
	 * </pre>
	 *
	 * @param kind CLASS / INTERFACE / ENUM / RECORD / ANNOTATION / ANONYMOUS (없으면 전부)
	 * @param layer CONTROLLER / SERVICE / REPOSITORY / MODEL ... (없으면 전부)
	 * @param name 전체 이름에 이 글자가 들어간 것만 (없으면 전부)
	 * @param page 1부터 시작
	 */
	public Map<String, Object> getTypeList(long revisionId, String kind, String layer, String name, int page, int size) {
		int pageNo = page < 1 ? 1 : page;
		int pageSize = size < 1 ? 50 : Math.min(size, MAX_PAGE_SIZE);

		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("kind", kind);
		condition.put("layer", layer);
		condition.put("name", name);
		condition.put("size", pageSize);
		condition.put("offset", (pageNo - 1) * pageSize);

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("total", declarationDao.countType(condition));
		result.put("page", pageNo);
		result.put("size", pageSize);
		result.put("types", declarationDao.selectTypeList(condition));
		return result;
	}

	/** 타입 하나와 그 메소드, 필드, 애노테이션, 나가는 참조를 함께 돌려줍니다. */
	public Map<String, Object> getType(long revisionId, String symbolId) {
		Map<String, Object> type = declarationDao.selectType(revisionId, symbolId);
		if (type == null) {
			throw ApiException.notFound("없는 타입입니다: revisionId=" + revisionId + ", symbolId=" + symbolId);
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>(type);
		result.put("methods", declarationDao.selectMethodListByOwner(revisionId, symbolId));
		result.put("fields", declarationDao.selectFieldListByOwner(revisionId, symbolId));
		result.put("annotations", declarationDao.selectAnnotationListByType(revisionId, symbolId));
		result.put("references", declarationDao.selectReferenceListByType(revisionId, symbolId));
		return result;
	}

}

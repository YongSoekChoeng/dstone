package net.dstone.boot.ai.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import net.dstone.boot.ai.vo.DocumentVo;

@Repository
public class DocumentDao extends net.dstone.boot.common.biz.BaseDao {

	public void insertDocument(DocumentVo documentVo) throws Exception {
		sqlSessionCommon.insert("net.dstone.boot.ai.dao.DocumentDao.insertDocument", documentVo);
	}

	public List<DocumentVo> listDocument() throws Exception {
		return sqlSessionCommon.selectList("net.dstone.boot.ai.dao.DocumentDao.listDocument");
	}

	public void deleteDocument(String sourceId) throws Exception {
		sqlSessionCommon.delete("net.dstone.boot.ai.dao.DocumentDao.deleteDocument", sourceId);
	}

}

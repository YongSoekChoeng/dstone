package net.dstone.batchadmin.server;

import java.util.List;

import org.springframework.stereotype.Repository;

import net.dstone.batchadmin.server.vo.BatchServerVo;

@Repository("batchServerDao")
public class BatchServerDao extends net.dstone.batchadmin.common.biz.BaseDao {

	public List<BatchServerVo> listServer(BatchServerVo vo) throws Exception {
		return sqlSessionCommon.selectList("net.dstone.batchadmin.server.BatchServerDao.listServer", vo);
	}

	/**
	 * DB_PASSWORD(ENC 암호문)까지 포함하여 조회. 화면에는 절대 노출하지 않고,
	 * BatchServerDataSourceRegistry가 커넥션을 구성할 때만 사용한다.
	 */
	public List<BatchServerVo> listActiveServerWithPassword() throws Exception {
		return sqlSessionCommon.selectList("net.dstone.batchadmin.server.BatchServerDao.listActiveServerWithPassword");
	}

	public BatchServerVo selectServer(Long serverId) throws Exception {
		return sqlSessionCommon.selectOne("net.dstone.batchadmin.server.BatchServerDao.selectServer", serverId);
	}

	public int insertServer(BatchServerVo vo) throws Exception {
		return sqlSessionCommon.insert("net.dstone.batchadmin.server.BatchServerDao.insertServer", vo);
	}

	public int updateServer(BatchServerVo vo) throws Exception {
		return sqlSessionCommon.update("net.dstone.batchadmin.server.BatchServerDao.updateServer", vo);
	}

	public int deleteServer(Long serverId) throws Exception {
		return sqlSessionCommon.delete("net.dstone.batchadmin.server.BatchServerDao.deleteServer", serverId);
	}

}

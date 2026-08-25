package net.dstone.batchadmin.server;

import java.util.List;
import java.util.Map;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import net.dstone.batchadmin.common.datasource.BatchServerDataSourceRegistry;
import net.dstone.batchadmin.common.rest.BatchRestClient;
import net.dstone.batchadmin.server.vo.BatchServerVo;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.exception.BizException;
import net.dstone.common.utils.StringUtil;

@org.springframework.stereotype.Service
public class BatchServerService extends net.dstone.batchadmin.common.biz.BaseService {

	@Autowired
	private BatchServerDao batchServerDao;

	@Autowired
	private BatchServerDataSourceRegistry dataSourceRegistry;

	@Autowired
	private BatchRestClient batchRestClient;

	@Autowired
	@Qualifier("jasyptStringEncryptor")
	private StringEncryptor stringEncryptor;

	public List<BatchServerVo> listServer(BatchServerVo paramVo) throws BizException {
		try {
			return batchServerDao.listServer(paramVo);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".listServer 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public BatchServerVo selectServer(Long serverId) throws BizException {
		try {
			return batchServerDao.selectServer(serverId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".selectServer 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public void insertServer(BatchServerVo vo) throws BizException {
		try {
			if (StringUtil.isEmpty(vo.getDB_PASSWORD())) {
				throw new Exception("배치서버 등록시 DB_PASSWORD는 필수입니다.");
			}

			String password = net.dstone.common.utils.EncUtil.encrypt(vo.getDB_PASSWORD());
			vo.setDB_PASSWORD(password);
			
			batchServerDao.insertServer(vo);
			dataSourceRegistry.refresh();
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".insertServer 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	/**
	 * DB_PASSWORD가 비어있으면 기존 비밀번호를 유지(수정폼에서는 비밀번호를 변경할 때만 입력).
	 */
	public void updateServer(BatchServerVo vo) throws BizException {
		try {
			if (!StringUtil.isEmpty(vo.getDB_PASSWORD())) {
				String password = net.dstone.common.utils.EncUtil.encrypt(vo.getDB_PASSWORD());
				vo.setDB_PASSWORD(password);
			} else {
				vo.setDB_PASSWORD(null);
			}
			
			batchServerDao.updateServer(vo);
			dataSourceRegistry.refresh();
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".updateServer 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public void deleteServer(Long serverId) throws BizException {
		try {
			batchServerDao.deleteServer(serverId);
			dataSourceRegistry.refresh();
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".deleteServer 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	/**
	 * dstone-batch RestApiRunner의 healthCheck 호출.
	 */
	public Map<String, Object> healthCheck(Long serverId) throws BizException {
		try {
			BatchServerVo server = batchServerDao.selectServer(serverId);
			if (server == null) {
				throw new Exception("등록되지 않은 배치서버입니다. serverId[" + serverId + "]");
			}
			return batchRestClient.healthCheck(server.getREST_BASE_URL());
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".healthCheck 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

}

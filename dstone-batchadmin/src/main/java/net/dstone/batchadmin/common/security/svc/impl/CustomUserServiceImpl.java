package net.dstone.batchadmin.common.security.svc.impl;

import java.util.Map;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import net.dstone.batchadmin.common.biz.BaseService;
import net.dstone.batchadmin.common.security.dao.CustomUserDao;
import net.dstone.batchadmin.common.security.svc.CustomUserService;

@Service("customUserService")
public class CustomUserServiceImpl extends BaseService implements CustomUserService {

	@Resource(name = "customUserDao")
	private CustomUserDao customUserDao;

	@Override
	public Map<String, Object> loginProcess(Map<String, String> param) throws Exception {
		return customUserDao.selectUser(param);
	}

	@Override
	public void updateUserLoginTime(Map<String, String> param) throws Exception {
		customUserDao.updateUserLoginTime(param);
	}

}

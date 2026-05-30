package net.dstone.boot.sample; 
 
import java.util.List;

import org.springframework.stereotype.Repository; 
 
@Repository("userDao")
public class UserDao extends net.dstone.boot.common.biz.BaseDao { 

    /* 
     * 샘플사용자정보 리스트조회(카운트) 
     */ 
    public int listUserCount(net.dstone.boot.sample.vo.UserVo userVo) throws Exception { 
        Object returnObj = sqlSessionSample.selectOne("net.dstone.boot.sample.UserDao.listUserCount", userVo); 
        if (returnObj == null) {
            return 0;
        } else {
            return ((Integer) returnObj).intValue();
        }
    } 
    /* 
     * 샘플사용자정보 리스트조회 
     */ 
    public List<net.dstone.boot.sample.vo.UserVo> listUser(net.dstone.boot.sample.vo.UserVo userVo) throws Exception { 
        List<net.dstone.boot.sample.vo.UserVo> list = sqlSessionSample.selectList("net.dstone.boot.sample.UserDao.listUser", userVo); 
        return list; 
    } 

} 

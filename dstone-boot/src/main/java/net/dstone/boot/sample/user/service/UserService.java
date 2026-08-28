package net.dstone.boot.sample.user.service; 
 
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.exception.BizException;
import net.dstone.common.utils.LogUtil; 
 
@Service 
public class UserService extends BaseService { 
     
    LogUtil logger = getLogger(); 
     

    /********* 공통 입력/수정/삭제 DAO 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.sample.cud.SampleCudDao sampleCudDao; 
    /********* 공통 입력/수정/삭제 DAO 정의부분 끝 *********/
    /********* DAO 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.sample.user.dao.UserDao userDao; 
    /********* DAO 정의부분 끝 *********/
    /** 
     * 샘플사용자정보 리스트조회 
     * @param paramVo 
     * @return 
     * @throws Exception 
     */ 
    public Map listUser(net.dstone.boot.sample.vo.UserVo paramVo) throws BizException{ 
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다.
        /************************ 변수 선언 시작 ************************/ 
        HashMap returnMap;                                        // 반환대상 맵 
        List<net.dstone.boot.sample.vo.UserVo> list;            // 리스트 
        /*** 페이징파라메터 세팅 시작 ***/
        net.dstone.common.utils.PageUtil pageUtil; 						// 페이징 유틸 
        int INT_TOTAL_CNT = 0;
        int INT_FROM = 0;
        int INT_TO = 0;
        /*** 페이징파라메터 세팅 끝 ***/
        /************************ 변수 선언 끝 **************************/ 
        try { 
            /************************ 변수 정의 시작 ************************/ 
            returnMap = new HashMap(); 
            list = null; 
            /************************ 변수 정의 끝 **************************/ 
            
            /************************ 비즈니스로직 시작 ************************/ 
            /*** 페이징파라메터 세팅 시작 ***/
            INT_TOTAL_CNT = userDao.listUserCount(paramVo);
            if ( 1>paramVo.getPAGE_NUM() ) { paramVo.setPAGE_NUM(1); } 
            if ( 1>paramVo.getPAGE_SIZE() ) { paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE); } 
            INT_FROM = (paramVo.getPAGE_NUM() - 1) * paramVo.getPAGE_SIZE(); 
            INT_TO = paramVo.getPAGE_SIZE(); 
            paramVo.setINT_FROM(INT_FROM);
            paramVo.setINT_TO(INT_TO);

            /*** 페이징파라메터 세팅 끝 ***/
            
            //DAO 호출부분 구현
            list = userDao.listUser(paramVo); 
            returnMap.put("returnObj", list); 
            
            /*** 페이징유틸 생성 시작 ***/ 
            pageUtil = new net.dstone.common.utils.PageUtil(paramVo.getPAGE_NUM(), paramVo.getPAGE_SIZE(), INT_TOTAL_CNT);
            returnMap.put("pageUtil", pageUtil);
            /*** 페이징유틸 생성 끝 ***/ 
            /************************ 비즈니스로직 끝 **************************/ 
        } catch (Exception e) { 
            logger.error(this.getClass().getName() + ".listUser 수행중 예외발생. 상세사항:" + e.toString()); 
            throw new BizException(ErrCd.SYS_ERR, e.toString());
        } 
        return returnMap; 
    } 

} 

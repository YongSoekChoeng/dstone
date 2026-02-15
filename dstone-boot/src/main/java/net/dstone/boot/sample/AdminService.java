package net.dstone.boot.sample; 
 
import java.util.Map; 
import java.util.HashMap; 
import java.util.List; 
 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.stereotype.Service; 
import org.springframework.transaction.annotation.Transactional; 
 
import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.exception.BizException;
import net.dstone.common.utils.LogUtil; 
 
@Service 
public class AdminService extends BaseService { 
     
    LogUtil logger = getLogger(); 
     

    /********* 공통 입력/수정/삭제 DAO 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.sample.cud.SampleCudDao sampleCudDao; 
    /********* 공통 입력/수정/삭제 DAO 정의부분 끝 *********/
    /**  
     * 샘플사용자정보 입력 
     * @param paramVo  
     * @return boolean 
     * @throws Exception  
    */  
    public boolean insertUser(net.dstone.boot.sample.cud.vo.SampleMemberCudVo paramVo) throws BizException{  
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. 
        /************************ 변수 선언 시작 ************************/  
        boolean isSuccess = false; 
        net.dstone.boot.sample.cud.vo.SampleMemberCudVo newKeyVo; 
        /************************ 변수 선언 끝 **************************/  
        try {  
            /************************ 변수 정의 시작 ************************/  
            newKeyVo = new net.dstone.boot.sample.cud.vo.SampleMemberCudVo();
            /************************ 변수 정의 끝 **************************/  
             
            /************************ 비즈니스로직 시작 ************************/  
            //NEW KEY 생성 부분 구현 
            newKeyVo.setGROUP_ID( paramVo.getGROUP_ID() ); 
            //paramVo.setUSER_ID( sampleCudDao.selectSampleMemberNewKey(newKeyVo).getUSER_ID() ); 
            //DAO 호출부분 구현 
            sampleCudDao.insertSampleMember(paramVo);  
            
            isSuccess = true; 
            /************************ 비즈니스로직 끝 **************************/  
        } catch (Exception e) { 
            String errDetailMsg = this.getClass().getName() + ".insertUser("+paramVo+") 수행중 예외발생. 상세사항:" + e.toString(); 
            logger.error(errDetailMsg); 
            throw new BizException(ErrCd.SYS_ERR, errDetailMsg);
        }  
        return isSuccess;  
    } 

    /**  
     * 샘플사용자정보 수정 
     * @param paramVo  
     * @return boolean 
     * @throws Exception  
    */  
    public boolean updateUser(net.dstone.boot.sample.cud.vo.SampleMemberCudVo paramVo) throws BizException{  
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. 
        /************************ 변수 선언 시작 ************************/  
        boolean isSuccess = false; 
        /************************ 변수 선언 끝 **************************/  
        try {  
            /************************ 변수 정의 시작 ************************/  
            
            /************************ 변수 정의 끝 **************************/  
             
            /************************ 비즈니스로직 시작 ************************/  
            //DAO 호출부분 구현 
            sampleCudDao.updateSampleMember(paramVo);  
            isSuccess = true; 
            /************************ 비즈니스로직 끝 **************************/  
        } catch (Exception e) { 
            String errDetailMsg = this.getClass().getName() + ".updateUser("+paramVo+") 수행중 예외발생. 상세사항:" + e.toString(); 
            logger.error(errDetailMsg); 
            throw new BizException(ErrCd.SYS_ERR, errDetailMsg);
        }  
        return isSuccess;  
    } 
    /**  
     * 샘플사용자정보 삭제 
     * @param paramVo  
     * @return boolean 
     * @throws Exception  
    */  
    public boolean deleteUser(net.dstone.boot.sample.cud.vo.SampleMemberCudVo paramVo) throws BizException{  
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. 
        /************************ 변수 선언 시작 ************************/  
        boolean isSuccess = false; 
        /************************ 변수 선언 끝 **************************/  
        try {  
            /************************ 변수 정의 시작 ************************/  
            
            /************************ 변수 정의 끝 **************************/  
             
            /************************ 비즈니스로직 시작 ************************/  
            //DAO 호출부분 구현 
            sampleCudDao.deleteSampleMember(paramVo);  
            isSuccess = true; 
            /************************ 비즈니스로직 끝 **************************/  
        } catch (Exception e) { 
            String errDetailMsg = this.getClass().getName() + ".deleteUser("+paramVo+") 수행중 예외발생. 상세사항:" + e.toString(); 
            logger.error(errDetailMsg); 
            throw new BizException(ErrCd.SYS_ERR, errDetailMsg);
        }  
        return isSuccess;  
    } 

    /********* DAO 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.sample.AdminDao adminDao; 
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
            INT_TOTAL_CNT = adminDao.listUserCount(paramVo);
            if ( 1>paramVo.getPAGE_NUM() ) { paramVo.setPAGE_NUM(1); } 
            if ( 1>paramVo.getPAGE_SIZE() ) { paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE); } 
            INT_FROM = (paramVo.getPAGE_NUM() - 1) * paramVo.getPAGE_SIZE(); 
            INT_TO = paramVo.getPAGE_SIZE(); 
            paramVo.setINT_FROM(INT_FROM);
            paramVo.setINT_TO(INT_TO);

            /*** 페이징파라메터 세팅 끝 ***/
            
            //DAO 호출부분 구현
            list = adminDao.listUser(paramVo); 
            returnMap.put("returnObj", list); 
            
            /*** 페이징유틸 생성 시작 ***/ 
            pageUtil = new net.dstone.common.utils.PageUtil(paramVo.getPAGE_NUM(), paramVo.getPAGE_SIZE(), INT_TOTAL_CNT);
            returnMap.put("pageUtil", pageUtil);
            /*** 페이징유틸 생성 끝 ***/ 
            /************************ 비즈니스로직 끝 **************************/ 
        } catch (Exception e) { 
            String errDetailMsg = this.getClass().getName() + ".listUser 수행중 예외발생. 상세사항:" + e.toString(); 
            logger.error(errDetailMsg); 
            throw new BizException(ErrCd.SYS_ERR, errDetailMsg);
        } 
        return returnMap; 
    } 

} 

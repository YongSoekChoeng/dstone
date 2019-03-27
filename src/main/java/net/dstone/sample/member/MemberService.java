package net.dstone.sample.member; 
 
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.biz.BaseService; 
 
@Service 
public class MemberService extends BaseService { 
     
    private Logger logger = LoggerFactory.getLogger(getClass()); 
     

    /********* 공통 입력/수정/삭제 DAO 정의부분 시작 *********/
    @Autowired 
    private net.dstone.sample.member.cud.MemberCudDao memberCudDao; 
    /********* 공통 입력/수정/삭제 DAO 정의부분 끝 *********/
    /********* DAO 정의부분 시작 *********/
    @Autowired 
    private net.dstone.sample.member.MemberDao memberDao; 
    /********* DAO 정의부분 끝 *********/
    
    /** 
     * 샘플멤버정보 리스트조회 
     * @param paramVo 
     * @return 
     * @throws Exception 
     */ 
    public Map listSampleMember(net.dstone.sample.member.vo.SampleMemberVo paramVo) throws Exception{ 
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다.
        /************************ 변수 선언 시작 ************************/ 
        HashMap returnMap;                                        // 반환대상 맵 
        List<net.dstone.sample.member.vo.SampleMemberVo> list;            // 리스트 
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
            INT_TOTAL_CNT = memberDao.listSampleMemberCount(paramVo);
            if ( 1>paramVo.getPAGE_NUM() ) { paramVo.setPAGE_NUM(1); } 
            if ( 1>paramVo.getPAGE_SIZE() ) { paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE); } 
            INT_FROM = (paramVo.getPAGE_NUM() - 1) * paramVo.getPAGE_SIZE(); 
            INT_TO = paramVo.getPAGE_SIZE(); 
            paramVo.setINT_FROM(INT_FROM);
            paramVo.setINT_TO(INT_TO);

            /*** 페이징파라메터 세팅 끝 ***/
            
            //DAO 호출부분 구현
            list = memberDao.listSampleMember(paramVo); 
            returnMap.put("returnObj", list); 
            
            /*** 페이징유틸 생성 시작 ***/ 
            pageUtil = new net.dstone.common.utils.PageUtil(paramVo.getPAGE_NUM(), paramVo.getPAGE_SIZE(), INT_TOTAL_CNT);
            returnMap.put("pageUtil", pageUtil);
            /*** 페이징유틸 생성 끝 ***/ 
            /************************ 비즈니스로직 끝 **************************/ 
        } catch (Exception e) { 
            logger.error(this.getClass().getName() + ".listSampleMember 수행중 예외발생. 상세사항:" + e.toString()); 
            throw new Exception( "샘플멤버정보 리스트조회 수행중 예외발생" );
        } 
        return returnMap; 
    } 


    /** 
     * 샘플멤버정보 상세조회 
     * @param paramVo 
     * @return 
     * @throws Exception 
     */ 
    public net.dstone.sample.member.vo.SampleMemberVo getSampleMember(net.dstone.sample.member.vo.SampleMemberVo paramVo) throws Exception{ 
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다.
        /************************ 변수 선언 시작 ************************/ 
        net.dstone.sample.member.vo.SampleMemberVo returnObj;            // 반환객체 
        /************************ 변수 선언 끝 **************************/ 
        try { 
            /************************ 변수 정의 시작 ************************/ 
            returnObj = null;
            /************************ 변수 정의 끝 **************************/ 
            
            /************************ 비즈니스로직 시작 ************************/ 
            
            //DAO 호출부분 구현
            returnObj = memberDao.getSampleMember(paramVo); 
            
            /************************ 비즈니스로직 끝 **************************/ 
        } catch (Exception e) { 
            logger.error(this.getClass().getName() + ".getSampleMember 수행중 예외발생. 상세사항:" + e.toString()); 
            throw new Exception( "샘플멤버정보 상세조회 수행중 예외발생" );
        } 
        return returnObj; 
    } 


    /**  
     * 샘플멤버정보 입력 
     * @param paramVo  
     * @return boolean 
     * @throws Exception  
    */  
    public boolean insertSampleMember(net.dstone.sample.member.cud.vo.SampleMemberCudVo paramVo) throws Exception{  
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. 
        /************************ 변수 선언 시작 ************************/  
        boolean isSuccess = false; 
        net.dstone.sample.member.cud.vo.SampleMemberCudVo newKeyVo; 
        /************************ 변수 선언 끝 **************************/  
        try {  
            /************************ 변수 정의 시작 ************************/  
            newKeyVo = new net.dstone.sample.member.cud.vo.SampleMemberCudVo();
            /************************ 변수 정의 끝 **************************/  
             
            /************************ 비즈니스로직 시작 ************************/  
            //NEW KEY 생성 부분 구현 
            newKeyVo.setGROUP_ID( paramVo.getGROUP_ID() ); 
            //paramVo.setUSER_ID( memberCudDao.selectSampleMemberNewKey(newKeyVo).getUSER_ID() ); 
            paramVo.setUSER_ID( paramVo.getUSER_ID()); 
            //DAO 호출부분 구현 
            memberCudDao.insertSampleMember(paramVo);  
            isSuccess = true; 
            /************************ 비즈니스로직 끝 **************************/  
        } catch (Exception e) { 
            logger.error(this.getClass().getName() + ".insertSampleMember("+paramVo+") 수행중 예외발생. 상세사항:" + e.toString());  
            throw new Exception( "샘플멤버정보 입력 수행중 예외발생" ); 
        }  
        return isSuccess;  
    } 


    /**  
     * 샘플멤버정보 수정 
     * @param paramVo  
     * @return boolean 
     * @throws Exception  
    */  
    public boolean updateSampleMember(net.dstone.sample.member.cud.vo.SampleMemberCudVo paramVo) throws Exception{  
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. 
        /************************ 변수 선언 시작 ************************/  
        boolean isSuccess = false; 
        /************************ 변수 선언 끝 **************************/  
        try {  
            /************************ 변수 정의 시작 ************************/  
            
            /************************ 변수 정의 끝 **************************/  
             
            /************************ 비즈니스로직 시작 ************************/  
            //DAO 호출부분 구현 
            memberCudDao.updateSampleMember(paramVo);  
            isSuccess = true; 
            /************************ 비즈니스로직 끝 **************************/  
        } catch (Exception e) { 
            logger.error(this.getClass().getName() + ".updateSampleMember("+paramVo+") 수행중 예외발생. 상세사항:" + e.toString());  
            throw new Exception( "샘플멤버정보 수정 수행중 예외발생" ); 
        }  
        return isSuccess;  
    } 


    /**  
     * 샘플멤버정보 삭제 
     * @param paramVo  
     * @return boolean 
     * @throws Exception  
    */  
    public boolean deleteSampleMember(net.dstone.sample.member.cud.vo.SampleMemberCudVo paramVo) throws Exception{  
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. 
        /************************ 변수 선언 시작 ************************/  
        boolean isSuccess = false; 
        /************************ 변수 선언 끝 **************************/  
        try {  
            /************************ 변수 정의 시작 ************************/  
            
            /************************ 변수 정의 끝 **************************/  
             
            /************************ 비즈니스로직 시작 ************************/  
            //DAO 호출부분 구현 
            memberCudDao.deleteSampleMember(paramVo);  
            isSuccess = true; 
            /************************ 비즈니스로직 끝 **************************/  
        } catch (Exception e) { 
            logger.error(this.getClass().getName() + ".deleteSampleMember("+paramVo+") 수행중 예외발생. 상세사항:" + e.toString());  
            throw new Exception( "샘플멤버정보 삭제 수행중 예외발생" ); 
        }  
        return isSuccess;  
    } 

} 

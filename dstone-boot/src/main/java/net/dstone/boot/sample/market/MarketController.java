package net.dstone.boot.sample.market; 
 
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import net.dstone.boot.sample.market.mart.FruitMarket;
import net.dstone.boot.sample.market.mart.Market;
import net.dstone.common.utils.RequestUtil;
@Controller
@RequestMapping(value = "/sample/market/*")
public class MarketController extends net.dstone.boot.common.biz.BaseController { 
    

    /********* SVC 정의부분 시작 *********/
    @Autowired
    private net.dstone.boot.sample.user.service.UserService userService; 
    /********* SVC 정의부분 끝 *********/
    
    /** 
     * 샘플사용자정보 리스트조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/fruitMarket/test.do") 
    public ModelAndView fruitMarketTest(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { 
   			mav = new ModelAndView("jsonView"); 
   		}else{
   			mav = new ModelAndView(""); 
   		}
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 									requestUtil;
   		Map 											returnObj;
   		Market											market;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		returnObj				= null;
   		market					= new FruitMarket();
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		userService.listUser(null);
   		market.setSellerForTrade("sample.item.Apple", 1000, 20);
   		market.setSellerForTrade("sample.item.Peache", 1500, 30);
   		
   		market.setBuyerForTrade("sample.buy.actor.HouseWife", 10000);
   		
   		market.trade("sample.buy.actor.HouseWife", "sample.item.Apple", 15);

   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    } 

} 

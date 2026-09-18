package net.dstone.boot.sample.google.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;
import net.dstone.common.config.ConfigProperty;

@Controller
@RequestMapping("/google/map")
@RequiredArgsConstructor
public class MapController extends net.dstone.boot.common.biz.BaseController { 

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    @RequestMapping("/initPage.do")
    public String initPage(Model model) {
    	
    	String mapsPlatformKey = configProperty.getProperty("interface.google.maps-platform-key");
    	
    	info("mapsPlatformKey["+mapsPlatformKey+"]");
    	
        model.addAttribute("mapsPlatformKey", mapsPlatformKey);
        
        return "sample/google/maps/map";
    }
    
}

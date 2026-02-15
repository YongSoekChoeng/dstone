package net.dstone.boot.sample.swagger.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import net.dstone.boot.common.biz.BaseController;

/**
 * NAME : SwaggerSampleController.java                                                                    					
 * DESC :                                                                                                        			
 * VER  : V1.0                                                                                                   			
 * Copyright 2019 Ready Korea All rights reserved                                                                			
 *------------------------------------------------------------------------------                                 			
 *                               MODIFICATION LOG                                                                			
 *------------------------------------------------------------------------------                                 			
 *    DATE     AUTHOR                      DESCRIPTION                        												
 * ----------  ------  ---------------------------------------------------------                                 			
 * 2025/02/17  정용석  						   Swagger 테스트                                                   			
 *------------------------------------------------------------------------------
 */
@Api(tags = "SwaggerSampleController API")                                      		
@Controller
public class SwaggerSampleController  extends BaseController {

    /**                                                                                                                     	
	 * 사용자 목록조회             
	 * @param input                                                                                                         	
	 * @return                                                                                                              	
	 * @throws Exception                                                                                                   	
	 */   
	@ApiOperation(value = "사용자(User)목록조회", nickname = "selectUsers", notes = "GET방식으로 사용자(User)목록을 조회.")
	@RequestMapping(value = "/restapi/sample/users", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<net.dstone.boot.sample.swagger.vo.UserVo>> selectUsers() throws Exception {

		//info("net.dstone.boot.sample.swagger.web.SwaggerSampleController.selectUsers() =======================>>>");
		
		List<net.dstone.boot.sample.swagger.vo.UserVo> userList = new ArrayList<net.dstone.boot.sample.swagger.vo.UserVo>();
		net.dstone.boot.sample.swagger.vo.UserVo user = null;
		ResponseEntity<List<net.dstone.boot.sample.swagger.vo.UserVo>> response	= null;
		
		userList.add((user = new net.dstone.boot.sample.swagger.vo.UserVo()));
		user.setId("hkd");
		user.setName("홍길동");

		userList.add((user = new net.dstone.boot.sample.swagger.vo.UserVo()));
		user.setId("sch");
		user.setName("성춘향");
		
		response = ResponseEntity.ok(userList);

		return response;
	}
	
    /**                                                                                                                     	
	 * 사용자 단건조회                        
	 * @param input                                                                                                         	
	 * @return                                                                                                              	
	 * @throws Exception                                                                                                   	
	 */   
	@ApiOperation(value = "사용자(User)단건조회", nickname = "selectUser", notes = "GET방식으로 사용자(User)단건 조회.")
	@RequestMapping(value = "/restapi/sample/users/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<net.dstone.boot.sample.swagger.vo.UserVo> selectUser(@ApiParam(value = "사용자 ID", required = true) @PathVariable String id) throws Exception {
		
		//info("net.dstone.boot.sample.swagger.web.SwaggerSampleController.selectUser() =======================>>> id["+id+"]");
		
		net.dstone.boot.sample.swagger.vo.UserVo user = new net.dstone.boot.sample.swagger.vo.UserVo();
		ResponseEntity<net.dstone.boot.sample.swagger.vo.UserVo> response	= null;
		
		user.setId(id);
		user.setName("홍길동");

		response = ResponseEntity.ok(user);

		return response;
	}

    /**                                                                                                                     	
	 * 신규 사용자입력    
	 * @param input                                                                                                         	
	 * @return                                                                                                              	
	 * @throws Exception                                                                                                   	
	 */   
	@ApiOperation(value = "신규 사용자(User)입력", nickname = "insertUser", notes = "POST방식으로 신규 사용자(User)입력.")
	@RequestMapping(value = "/restapi/sample/users", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<net.dstone.boot.sample.swagger.vo.ResultVo> insertUser(@RequestBody @ApiParam(value = "사용자(User) 정보", required = true) net.dstone.boot.sample.swagger.vo.UserVo newUser) throws Exception {
		
		//info("net.dstone.boot.sample.swagger.web.SwaggerSampleController.insertUser() =======================>>> newUser["+newUser+"]");

		net.dstone.boot.sample.swagger.vo.ResultVo result = new net.dstone.boot.sample.swagger.vo.ResultVo();
		ResponseEntity<net.dstone.boot.sample.swagger.vo.ResultVo> response	= null;

		result.setSuccessYn("Y");
		response = ResponseEntity.ok(result);

		return response;
	}
	
	
}

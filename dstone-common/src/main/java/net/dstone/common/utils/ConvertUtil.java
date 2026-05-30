package net.dstone.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ConvertUtil {

	public static Map<String, Object> convertToMap(HttpServletRequest request) {
		Map<String, Object> map = new HashMap<String, Object>();
		ObjectMapper mapper = new ObjectMapper();
		try {

			ServletInputStream inputStream = request.getInputStream();
			String messageBody = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
			messageBody = StringUtil.replace(messageBody, ": null", ": \"\"");

			map = mapper.readValue(messageBody, Map.class);
		} catch (Exception e) {
			// e.printStackTrace();
		}
		return map;
	}

	public static String convertToJson(Object param) {
		String jsonStr = "";
		ObjectMapper mapper = new ObjectMapper();
		try {
			jsonStr = mapper.writeValueAsString(param);
		} catch (Exception e) {
			// e.printStackTrace();
		}
		return jsonStr;
	}

	public static void writeJson(HttpServletResponse response, Map<String, Object> outMap) {
		try {
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			String responseStr = convertToJson((Object) outMap);
			response.getWriter().write(responseStr);
		} catch (Exception e) {
			// e.printStackTrace();
		}
	}

	public static String HEADER_PART = "headers";
	public static String PARAM_PART = "parameters";
	public static String FILE_PART = "files";
	public static String BODY_PART = "body";

	public static Map<String, Object> convertRequestToMap(HttpServletRequest request) throws IOException {
		Map<String, Object> result = new LinkedHashMap<>();

		// 1. 헤더 정보
		Map<String, String> headers = new LinkedHashMap<>();
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			headers.put(name, request.getHeader(name));
		}
		result.put(HEADER_PART, headers);

		// 2. 요청 파라미터
		Map<String, String[]> params = request.getParameterMap();
		Map<String, String> flatParams = new LinkedHashMap<>();
		for (Map.Entry<String, String[]> entry : params.entrySet()) {
			flatParams.put(entry.getKey(), String.join(",", entry.getValue()));
		}
		result.put(PARAM_PART, flatParams);

		// 3. Multipart 요청 처리
		if (request instanceof MultipartHttpServletRequest) {
			MultipartHttpServletRequest multiRequest = (MultipartHttpServletRequest) request;
			Map<String, Object> files = new LinkedHashMap<>();
			for (Map.Entry<String, MultipartFile> entry : multiRequest.getFileMap().entrySet()) {
				MultipartFile file = entry.getValue();
				Map<String, Object> fileInfo = new LinkedHashMap<>();
				fileInfo.put("fileName", file.getOriginalFilename());
				fileInfo.put("size", file.getSize());
				fileInfo.put("contentType", file.getContentType());
				// fileInfo.put("bytes", file.getBytes()); // 실제 바이트는 생략 가능
				files.put(entry.getKey(), fileInfo);
			}
			result.put(FILE_PART, files);
		} else {
			// 4. 일반 바디 (JSON 등)
			if (!"GET".equalsIgnoreCase(request.getMethod())) {
				String body = new BufferedReader(request.getReader()).lines()
						.collect(Collectors.joining(System.lineSeparator()));
				result.put(BODY_PART, body);
			}
		}

		return result;
	}
}

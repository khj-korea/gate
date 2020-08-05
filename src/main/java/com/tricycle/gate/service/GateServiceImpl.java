package com.tricycle.gate.service;

import com.tricycle.gate.mapper.mysql.MysqlGateMapper;
import com.tricycle.gate.mapper.postgre.PostgreGateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GateServiceImpl implements GateService {

	@Autowired
	private MysqlGateMapper mysqlGateMapper;

	@Autowired
	private PostgreGateMapper postgreGateMapper;
	
	@Override
	public int getSample(int i) {

		List<Map<String, Object>> tableList = mysqlGateMapper.getTables();

		List<Map<String, Object>> postgreTableList = postgreGateMapper.getPostgreTables();

		List<Map<String, Object>> gateMappingTables = postgreGateMapper.getGateMappingTables();

		return mysqlGateMapper.getSample();
	}

	@Override
	public List<Map<String, Object>> getGateMappingTables() {
		return null;
	}

	@Override
	public String getGateRedirectUrl(HttpServletRequest request, HttpServletResponse response, HttpSession session) {

		// 게이트 매핑 테이블 템플릿 획득
		List<Map<String, Object>> gateMappingTables = postgreGateMapper.getGateMappingTables();
		for (Map<String, Object> templateMap : gateMappingTables) {
			// 편의를 위해 url 객체 조립
			if (0 < templateMap.getOrDefault("url_template_asis", "").toString().length()) {
				try {
					templateMap.put("url_template_asis_obj", new URL(templateMap.get("url_template_asis").toString()));
				} catch (MalformedURLException e) {
					// 에러..
				}
			}

			if (0 < templateMap.getOrDefault("url_template_tobe", "").toString().length()) {
				try {
					templateMap.put("url_template_tobe_obj", new URL(templateMap.get("url_template_tobe").toString()));
				} catch (MalformedURLException e) {
					// 에러..
				}
			}
		}

		// 접속 URL 도메인 확인 후 사이트 확인
		String siteCd = getSiteCd(request);

		// 접속 단말 모바일/PC 여부 확인
		String deviceCd = getDeviceCd(request);

		String requestType = getRequestType(request, siteCd);

		Map<String, Object> template = null;
		for (Map<String, Object> templateMap : gateMappingTables) {
			// as-is to-be 매핑 테이블을 돌면서

			if (1 > request.getRequestURI().replace("/", "").length()) {
				// 쿼리스트링, 세부경로가 없으면 메인페이지

				if (templateMap.getOrDefault("type", "").toString().equals("Home")) {
					// 매핑 테이블에서 메인페이지 템플릿을 탐색

					if (siteCd.equals(templateMap.getOrDefault("site_cd", "").toString())) {
						// 동일 사이트 메인페이지 템플릿을 탐색
						if (deviceCd.equals(templateMap.getOrDefault("device_cd", "").toString())) {
							// 동일 단말 타입의 메인페이지 템플릿을 탐색
							template = templateMap;
							break;
						}
					}
				}
				continue;
			}

			if (null != templateMap.get("url_template_tobe_obj")) {
				// to-be 주소가 존재하고

				URL tobeUrl = (URL)templateMap.get("url_template_tobe_obj");
				if (requestType.equals(templateMap.get("type").toString()) &&
					siteCd.equals(templateMap.get("site_cd").toString()) &&
					deviceCd.equals(templateMap.get("device_cd").toString())) {

					// to-be 접속 타입이 접속 타입과 동일하면

					// 매핑 템플릿 획득
					template = templateMap;
					break;
				}
			}
		}

		if (null == template) {
			// 매핑 템플릿을 획득하지 못했으면

			for (Map<String, Object> templateMap : gateMappingTables) {
				if (templateMap.get("site_cd").toString().equals(siteCd) && templateMap.get("device_cd").equals(deviceCd)) {
					if (templateMap.get("type").toString().equals("Home")) {
						// 현재 접속 사이트, 현재 접속 디바이스별 메인페이지 템플릿 획득
						template = templateMap;
						break;
					}
				}
			}
		} else {
			// 매핑 템플릿을 획득하였으면

			// 현재 접속 디바이스와 동일한지 확인
			if (!deviceCd.equals(template.get("device_cd").toString())) {
				// 디바이스가 다르면..

				// 동일 사이트, 동일 타입의 맞는 디바이스로 템플릿 변경
				String type = template.get("type").toString();
				for (Map<String, Object> templateMap : gateMappingTables) {
					if (template.get("site_cd").toString().equals(siteCd)) {
						if (!template.get("device_cd").toString().equals(deviceCd)) {
							if (template.get("type").toString().equals(type)) {
								template = templateMap;
								break;
							}
						}
					}
				}
			}
		}

		if (null != request.getQueryString() && 0 < request.getQueryString().length()) {
			String returnUrl = template.get("url_template_asis").toString();

			// 쿼리 스트링이 존재하면..

			// 쿼리스트링 키밸류 Map으로 변환
			Map<String, Object> queryMap = null;
			try {
				queryMap = splitQuery(request.getQueryString());
			} catch (UnsupportedEncodingException e) {

			}

			if (requestType.equals("detail_pcode")) {
				// 상품 상세 모바일이면.. (PC는 쿼리스트링 미존재)

				// 상품번호 획득
				String prdNo = queryMap.getOrDefault("productNo", "").toString();

				// 상품번호로 P코드 획득
				Map<String, Object> prdMap = postgreGateMapper.getPrdInfoByPrdno(prdNo);

				// returnUrl에서 Param1을 Pcode로 변경
				if (null != prdMap ) {
					returnUrl = returnUrl.replace("{param1}", prdMap.getOrDefault("pcode", "").toString());
				}
			}
		} else {
			String returnUrl = template.get("url_template_asis").toString();

			if (requestType.equals("detail_pcode")) {
				// 상품 상세 PC이면.. (모바일은 쿼리스트링 존재)

				// 상품번호 획득
				String prdNo = request.getRequestURI().replace("/product/", "");

				// 상품번호로 P코드 획득
				Map<String, Object> prdMap = postgreGateMapper.getPrdInfoByPrdno(prdNo);

				// returnUrl에서 Param1을 Pcode로 변경
				if (null != prdMap ) {
					returnUrl = returnUrl.replace("{param1}", prdMap.getOrDefault("pcode", "").toString());
				}
			}

			return returnUrl;
		}

		// 임시
		String returnUrl = "";
		if (siteCd.equals("1")) {
			if (deviceCd.equals("001")) {
				returnUrl = "//www.halfclub.com";
			} else {
				returnUrl = "//m.halfclub.com";
			}
		} else {
			if (deviceCd.equals("001")) {
				returnUrl = "//www.boribori.co.kr";
			} else {
				returnUrl = "//m.boribori.co.kr";
			}
		}

		return returnUrl;
	}

	public String getDeviceCd(HttpServletRequest request) {
		String userAgent = request.getHeader("User-Agent").toUpperCase();

		if (userAgent.indexOf("MOBILE") > -1) {
			return "002";
		} else {
			return "001";
		}
	}

	public String getSiteCd(HttpServletRequest request) {

		if (request.getServerName().indexOf("halfclub.com") > -1) {
			return "1";
		} else if (request.getServerName().indexOf("boribori.co.kr") > -1) {
			return "2";
		} else {
			// default for test
			return "1";
		}
	}

	public String getRequestType(HttpServletRequest request, String siteCd) {

		String urlWithoutDomain = request.getRequestURI() + "/" + request.getQueryString();
		String requestType = "";

		if (0 == urlWithoutDomain.indexOf("/best")) {
			requestType = "Best";
			if (0 == urlWithoutDomain.indexOf("/best?tabNo=0&categoryNo=")) {
				requestType = "Best_category";
			}
		} else if (siteCd.equals("1") && 0 == urlWithoutDomain.indexOf("/home?cateType=D06629")) {
			requestType = "Best";
		} else if (siteCd.equals("2") && 0 == urlWithoutDomain.indexOf("/home?cateType=D06600")) {
			requestType = "Best";
		} else if (0 == urlWithoutDomain.indexOf("/cart")) {
			requestType = "Cart";
		} else if (0 == urlWithoutDomain.indexOf("/main") || 0 == urlWithoutDomain.indexOf("/home")) {
			requestType = "Home";
		} else if (0 == urlWithoutDomain.indexOf("/product/") || 0 == urlWithoutDomain.indexOf("/detail?productNo=")) {
			requestType = "detail_pcode";
		} else {
			requestType = "Home";
		}

		return requestType;
	}

	public static Map<String, Object> splitQuery(URL url) throws UnsupportedEncodingException {
		return splitQuery(url.getQuery());
	}
	public static Map<String, Object> splitQuery(String query) throws UnsupportedEncodingException {
		Map<String, Object> query_pairs = new LinkedHashMap<>();
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
		}
		return query_pairs;
	}
}

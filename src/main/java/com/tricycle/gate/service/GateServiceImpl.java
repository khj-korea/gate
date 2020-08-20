package com.tricycle.gate.service;

import com.tricycle.gate.mapper.mysql.MysqlGateMapper;
import com.tricycle.gate.mapper.postgre.PostgreGateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
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
	public List<Map<String, Object>> getGateMappingTables() {
		return null;
	}

	@Override
	public String getGateRedirectUrl(HttpServletRequest request, HttpServletResponse response, HttpSession session) {

		String redirectUrl = "";

		// 접속 URL 도메인 확인 후 사이트 확인
		String siteCd = getSiteCd(request);

		// 접속 단말 모바일/PC 여부 확인
		String deviceCd = getDeviceCd(request);

		// 도메인을 제외한 URI 획득
		//String requestUri = request.getRequestURI();
		//if (null != requestUri && 0 < requestUri.length() && requestUri.substring(0, 1).equals("/")) {
		//	requestUri = requestUri.substring(1);
		//}

		Boolean isHaveQueryString = null != request.getQueryString() && 0 < request.getQueryString().length();

		// url_parameter (requestUri) 값 획득
		String urlParameter = request.getQueryString();

		// URL 쿼리 맵 획득
		Map<String, Object> queryMap = null;
		try {
			queryMap = splitQuery(urlParameter);
			//queryMap = splitQuery(requestUri);
		} catch (UnsupportedEncodingException e) {

		}

		// 요청타입 획득
		//String requestType = getRequestType(request, siteCd);
		String requestType = "";
		if (null != queryMap) {
			requestType = queryMap.getOrDefault("type", "").toString();
		}

		// PartnerId 획득
		String partnerId = "";
		if (null != queryMap) {
			partnerId = queryMap.getOrDefault("partnerid", "").toString();
		}
		if (0 == partnerId.length()) {
			partnerId = siteCd.equals("1") ? "halfclub" : "b_boribori";
		}

		// 클라이언트 IP 획득
		String clientIp = "";
		if (null != request.getHeader("X-FORWARDED-FOR")) {
			clientIp = request.getHeader("X-FORWARDED-FOR");
		} else {
			clientIp = request.getRemoteAddr();
		}

		// UserAgent 획득
		String userAgent = request.getHeader("User-Agent").toUpperCase();
		if (null == userAgent || 1 > userAgent.length()) {
			return "";
		}

		// 이전URL 획득
		String refererUrl = "";
		HttpServletRequest request1 = ((ServletRequestAttributes) RequestContextHolder
				.getRequestAttributes()).getRequest();
		String temp = request1.getHeader("referer");
		if (null != request.getHeader("referer")) {
			refererUrl = request.getHeader("referer");
		}

		// pcid 쿠키 획득
		// uid 쿠키 획득
		String pcid = "";
		String uid = "";
		if (null != request.getCookies()) {
			Cookie[] cookies = request.getCookies();
			for (int i=0; i<cookies.length; i++) {
				Cookie cookie = cookies[i];
				if (cookie.getName().equals("PCID")) {
					pcid = cookie.getValue();
				} else if (cookie.getName().equals("UID")) {
					uid = cookie.getValue();
				}
			}
		}


		// 접속 요청한 모바일PC 여부 확인
		String reqDeviceCd = "";
		if (null != queryMap) {
			reqDeviceCd = queryMap.getOrDefault("device_cd", "").toString();
		}

		// 게이트 매핑 테이블 템플릿 획득
		Map<String, Object> mappingTableSearchMap = new HashMap<>();
		mappingTableSearchMap.put("siteCd", siteCd);
		mappingTableSearchMap.put("deviceCd", deviceCd);
		mappingTableSearchMap.put("type", requestType);
		List<Map<String, Object>> gateMappingTables = mysqlGateMapper.getGateMappingTables(mappingTableSearchMap);

		// 1. 방문 카운트 insert
		Map<String, Object> partnerConnCountMap = new HashMap<>();
		partnerConnCountMap.put("partnerId", partnerId);
		partnerConnCountMap.put("siteCd", siteCd);
		partnerConnCountMap.put("deviceCd", deviceCd);
		partnerConnCountMap.put("clientIp", clientIp);
		partnerConnCountMap.put("userAgent", userAgent);
		partnerConnCountMap.put("refererUrl", refererUrl);
		partnerConnCountMap.put("pcid", pcid);
		partnerConnCountMap.put("uid", uid);
		partnerConnCountMap.put("urlParameter", urlParameter);
		mysqlGateMapper.insertPartnerConnCount(partnerConnCountMap);

		// 방문 카운트 insert 한 레코드의 SEQ를 읽어온다
		String partnerConnSeq = "";
		partnerConnSeq = partnerConnCountMap.getOrDefault("seq", "").toString();

		Map<String, Object> excptLogMap = new HashMap<>();
		excptLogMap.put("log_seq", partnerConnSeq);
		// 2. 매출코드 유효성 체크
		Map<String, Object> partnerIdDetailMap = mysqlGateMapper.getPartnerIdDetail(partnerId);
		if (null == partnerIdDetailMap) {
			// 매출코드가 DB에 없음..

			// todo: 예외처리.. 무엇을?

			// todo: Log Insert.. 무엇을?
			excptLogMap.put("log_type","1");
			int res = mysqlGateMapper.insertExcptLog(excptLogMap);
			// 기본 매출코드 셋팅 후 홈 랜딩

			// response에 쿠키로 mnm 에 사이트별 기본 매출코드 심기
			addPartnerCookie(response, siteCd, partnerId);

			// 기본 홈 주소 return
			// 매핑테이블에 Home 타입 조회
			Map<String, Object> mappingTemplate = null;
			for (Map<String, Object> partner : gateMappingTables) {
				if (siteCd.equals(partner.getOrDefault("site_cd", "").toString())) {
					// 동일 사이트
					if (deviceCd.equals(partner.getOrDefault("device_cd", "").toString())) {
						// 동일 디바이스
						if (partner.getOrDefault("type", "").toString().toLowerCase().equals("home")) {
							// 메인화면 타입
							mappingTemplate = partner;
							break;
						}
					}
				}
			}
			//return mappingTemplate.getOrDefault("url_template_asis", "").toString();
			redirectUrl = mappingTemplate.getOrDefault("url_template_asis", "").toString();
			redirectUrl += "?gSeq=" + partnerConnSeq + "&_n_m2=" + partnerId;;
		} else {
			// 매출코드가 존재

			if (reqDeviceCd.equals("001")) {
				// PC 게이트페이지로 접근 요청

				if (deviceCd.equals("001")) {
					// 현재 접속 장비가 PC
					// do nothing
				} else {
					// 현재 접속 장비가 MC

					// 매출코드를 MC매출코드로 변경
					if (0 < partnerIdDetailMap.getOrDefault("mobileid", "").toString().length()) {
						partnerId = partnerIdDetailMap.getOrDefault("mobileid", "").toString();
					}
				}
			} else {
				// MC 게이트페이지로 접근 요청
				// 분기처리 없음
			}

			// 매핑테이블에 타입 조회
			Map<String, Object> mappingTemplate = null;
			for (Map<String, Object> partner : gateMappingTables) {
				if (siteCd.equals(partner.getOrDefault("site_cd", "").toString())) {
					// 동일 사이트

					if (deviceCd.equals(partner.getOrDefault("device_cd", "").toString())) {
						// 동일 디바이스

						if (requestType.toLowerCase().equals(partner.getOrDefault("type", "").toString().toLowerCase())) {
							// 동일 타입

							mappingTemplate = partner;
							break;
						}
					}
				}
			}

			// 해당 타입 템플릿 존재 확인
			if (null == mappingTemplate) {
				// 템플릿 없음

				// todo: 예외처리.. 무엇을?

				// todo: Log Insert.. 무엇을?
				excptLogMap.put("log_type","2");
				int res = mysqlGateMapper.insertExcptLog(excptLogMap);
				// 기본 매출코드 셋팅 후 홈 랜딩

				// response에 쿠키로 mnm 에 사이트별 매출코드 심기
				addPartnerCookie(response, siteCd, partnerId);

				// 기본 홈 주소 return
				// 매핑테이블에 Home 타입 조회
				for (Map<String, Object> partner : gateMappingTables) {
					if (siteCd.equals(partner.getOrDefault("site_cd", "").toString())) {
						// 동일 사이트
						if (deviceCd.equals(partner.getOrDefault("device_cd", "").toString())) {
							// 동일 디바이스
							if (partner.getOrDefault("type", "").toString().toLowerCase().equals("home")) {
								// 메인화면 타입
								mappingTemplate = partner;
								break;
							}
						}
					}
				}
				//return mappingTemplate.getOrDefault("url_template_asis", "").toString();
				redirectUrl = mappingTemplate.getOrDefault("url_template_asis", "").toString();
				redirectUrl += "?gSeq=" + partnerConnSeq + "&_n_m2=" + partnerId;;
			} else {
				// 템플릿 있음

				// 리다이렉트 url 획득
				redirectUrl = mappingTemplate.getOrDefault("url_template_asis", "").toString();

				// 파라메터 1~5 존재 확인 후 to-be 테이블에서 replace하기
				for (int index = 1; index < 6; index++) {
					if (0 < mappingTemplate.getOrDefault(String.format("param%d", index), "").toString().length()) {
						//String key = mappingTemplate.get(String.format("param%d", index)).toString();
						String key = String.format("param%d", index);

						String value = "";
						if (null != queryMap) {
							value = queryMap.getOrDefault(key, "").toString();
						}

						redirectUrl = redirectUrl.replace(String.format("{param%d}", index), value);
					}
				}

				// response에 쿠키로 mnm 에 매출코드 심기
				addPartnerCookie(response, siteCd, partnerId);

				if (redirectUrl.contains("?")){
					redirectUrl += "&gSeq=" + partnerConnSeq + "&_n_m2=" + partnerId;;
				}else{
					redirectUrl += "?gSeq=" + partnerConnSeq + "&_n_m2=" + partnerId;;
				}

			}

			//return redirectUrl;
		}

		//todo: redirect URL 뒤쪽에 방문 카운트 insert 한 레코드의 SEQ를 붙인다.
		/* 예제:
		redirectUrl += "&gSeq=" + partnerConnSeq;
		 */
		
		return redirectUrl;
	}

	@Override
	public String getDeviceCd(HttpServletRequest request) {
		String userAgent = request.getHeader("User-Agent").toUpperCase();

		if (userAgent.indexOf("MOBILE") > -1) {
			return "002";
		} else {
			return "001";
		}
	}

	@Override
	public String getSiteCd(HttpServletRequest request) {

		if (request.getServerName().indexOf(SiteDefine.Halfclub.getSiteCookieDomain()) > -1) {
			return SiteDefine.Halfclub.getSiteCd();
		} else if (request.getServerName().indexOf(SiteDefine.Boribori.getSiteCookieDomain()) > -1) {
			return SiteDefine.Boribori.getSiteCd();
		} else {
			// default for test
			return SiteDefine.Halfclub.getSiteCd();
		}
	}

	@Override
	public String getRequestType(HttpServletRequest request, String siteCd) {

		String urlWithoutDomain = request.getRequestURI();
		if (null != request.getQueryString() && 0 < request.getQueryString().length()) {
			urlWithoutDomain += "?" + request.getQueryString();
		}
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
			if (null != request.getQueryString() && 0 < request.getQueryString().length()) {
				requestType = "Best";
			}
		} else if (0 == urlWithoutDomain.indexOf("/product/") || 0 == urlWithoutDomain.indexOf("/detail?productNo=")) {
			requestType = "detail_pcode";
		} else if (0 == urlWithoutDomain.indexOf("/event") || 0 == urlWithoutDomain.indexOf("/plan")) {
			requestType = "Theme";
		} else {
			requestType = "Home";
		}

		return requestType;
	}

	@Override
	public Map<String, Object> splitQuery(URL url) throws UnsupportedEncodingException {
		return splitQuery(url.getQuery());
	}
	@Override
	public Map<String, Object> splitQuery(String query) throws UnsupportedEncodingException {
		Map<String, Object> query_pairs = new LinkedHashMap<>();
		if (null != query) {
			String[] pairs = query.split("&");
			if (null != pairs) {
				for (String pair : pairs) {
					int idx = pair.indexOf("=");
					if (-1 < idx) {
						query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
					}
				}
			}
		}
		return query_pairs;
	}

	@Override
	public void addPartnerCookie(HttpServletResponse response, String siteCd, String partnerId) {

		final Cookie cookie = new Cookie("mnm", partnerId);
		cookie.setDomain(siteCd.equals(SiteDefine.Halfclub.getSiteCd()) ? SiteDefine.Halfclub.getSiteCookieDomain() : SiteDefine.Boribori.getSiteCookieDomain());
		cookie.setPath("/");
		cookie.setMaxAge(60 * 60 * 24 * 365); // 60초 * 60분 * 24시 * 365일
		cookie.setSecure(false);
		cookie.setHttpOnly(false);
		response.addCookie(cookie);

		setCookie(response, siteCd, "NFG", "Y", 60 * 60 * 24 * 365, false, false);
	}

	@Override
	public void setCookie(HttpServletResponse response, String siteCd, String cookieName, String cookieValue, int expiry, boolean Secure, boolean HttpOnly) {

		final Cookie cookie = new Cookie(cookieName, cookieValue);
		cookie.setDomain(siteCd.equals(SiteDefine.Halfclub.getSiteCd()) ? SiteDefine.Halfclub.getSiteCookieDomain() : SiteDefine.Boribori.getSiteCookieDomain());
		cookie.setPath("/");
		cookie.setMaxAge(expiry);
		cookie.setSecure(false);
		cookie.setHttpOnly(false);
		response.addCookie(cookie);
	}

}

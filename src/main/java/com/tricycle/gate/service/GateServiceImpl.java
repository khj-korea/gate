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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;

@Service
public class GateServiceImpl implements GateService {

	@Autowired
	private MysqlGateMapper mysqlGateMapper;

	@Autowired
	private PostgreGateMapper postgreGateMapper;

	private String partnerId;

	@Override
	public List<Map<String, Object>> getGateMappingTables() {
		return null;
	}

	@Override
	public String getGateRedirectUrl(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException {


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

		String urltemplateType = "url_template_asis";


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
		//String partnerId = "";
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
		String userAgent = request.getHeader("User-Agent");
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

		//to-be 확인 여부
		String reqNfg = "";
		if (null != queryMap) {
			reqNfg = queryMap.getOrDefault("isnfg", "").toString();
			urltemplateType = reqNfg.toLowerCase().equals("y") ? "url_template_tobe" : "url_template_asis";
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
		mappingTableSearchMap.put("partnerid", partnerId);
		List<Map<String, Object>> gateMappingTables = mysqlGateMapper.getGateMappingTables(mappingTableSearchMap);

		//해당 partnerid에 예외로직이 없을경우
		if(gateMappingTables.size() < 1){
			mappingTableSearchMap.put("partnerid", "");
			gateMappingTables = mysqlGateMapper.getGateMappingTables(mappingTableSearchMap);
		}

		// 에러 로그 Map
		Map<String, Object> excptLogMap = new HashMap<>();

		// 2. 매출코드 유효성 체크
		Map<String, Object> partnerIdDetailMap = mysqlGateMapper.getPartnerIdDetail(partnerId);
		if (null == partnerIdDetailMap) {
			// 매출코드가 DB에 없음..

			// 매출코드가 정상이 아니어도 방문 카운트 Insert
			String partnerConnSeq = this.insertPartnerConn(partnerId, siteCd, deviceCd, clientIp, userAgent, refererUrl, pcid, uid, urlParameter);

			// todo: 예외처리.. 무엇을?

			// 에러 Log Insert
			excptLogMap.put("log_seq", partnerConnSeq);
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

			redirectUrl = mappingTemplate.getOrDefault(urltemplateType, "").toString();

			if (redirectUrl.contains("?")){
				redirectUrl += "&_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
			}else{
				redirectUrl += "?_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
			}

		} else {
			// 매출코드가 존재

			if (deviceCd.equals("001")) {
				// 현재 접속 장비가 PC
				// do nothing
			} else {
				// 현재 접속 장비가 MC

				// 매출코드를 MC매출코드로 변경
				if (0 < partnerIdDetailMap.getOrDefault("mobileid", "").toString().length()) {
					partnerId = partnerIdDetailMap.getOrDefault("mobileid", "").toString();
				}

				// 모바일 매출코드로 변경되었으니 mh_pc_ver 쿠키 N값으로 생성 => 201120_모바일에서 기기일 경우 생성
				setCookie(response, siteCd, "mh_pc_ver", "N", 60 * 60 * 24 * 5, false, false);
			}

			/*if (deviceCd.equals("002")) {
				// 디바이스가 모바일이면 쿠키 삽입(로컬스토리지 로그 삽입 대체)
				//this.setCookie(response, siteCd, "NFG_D", "M", null, false, false);

				// 디바이스가 모바일이면 쿠키 삽입 => 201120_하단으로 이동
				this.setCookie(response, siteCd, "pCheck", "Y", null, false, false);
			}*/

			// 방문 카운트 Insert
			String partnerConnSeq = this.insertPartnerConn(partnerId, siteCd, deviceCd, clientIp, userAgent, refererUrl, pcid, uid, urlParameter);

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

				// 에러 Log Insert
				excptLogMap.put("log_seq", partnerConnSeq);
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

				redirectUrl = mappingTemplate.getOrDefault(urltemplateType, "").toString();

				if (redirectUrl.contains("?")){
					redirectUrl += "&_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
				}else{
					redirectUrl += "?_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
				}

			} else {
				// 템플릿 있음

				// 리다이렉트 url 획득
				redirectUrl = mappingTemplate.getOrDefault(urltemplateType, "").toString();


				// 파라메터 1~5 존재 확인 후 to-be 테이블에서 replace하기
				for (int index = 1; index < 6; index++) {
					if (0 < mappingTemplate.getOrDefault(String.format("param%d", index), "").toString().length()) {
						//String key = mappingTemplate.get(String.format("param%d", index)).toString();

						String key = "";

						// &para 단어 unicode 치환 이슈로 인해 p1~p5 방식으로 사용변경 ( 기존 사용유저 에러방지를 위해 param방식 살려둠 )
						if(request.getQueryString().contains("param")){
							key = String.format("param%d", index);
						}else{
							key = String.format("p%d", index);
						}

						String value = "";
						if (null != queryMap) {
							value = queryMap.getOrDefault(key, "").toString();
						}

						//차세대 오픈 시에는 Pcode를 prd_no로 변경하여 리다이렉트
						if(urltemplateType.equals("url_template_tobe") &&
								((requestType.equals("detail_prstcd") && (key.equals("param3") || key.equals("p3")))
								|| requestType.equals("detail_pcode") && (key.equals("param1") || key.equals("p1"))))
						{
							List<Map<String, Object>> prdNoInfo = postgreGateMapper.getPrdInfoByPCode(value.toUpperCase(), siteCd);
							if(prdNoInfo != null && prdNoInfo.size() > 0) {
								value = prdNoInfo.get(0).getOrDefault("prd_no", value).toString();
							}
						}

						redirectUrl = redirectUrl.replace(String.format("{param%d}", index), value);
					}
				}

				// response에 쿠키로 mnm 에 매출코드 심기
				addPartnerCookie(response, siteCd, partnerId);

				// redirect URL 뒤쪽에 방문 카운트 insert 한 레코드의 SEQ를 붙인다
				if (redirectUrl.contains("?")){
					redirectUrl += "&_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
				}else{
					redirectUrl += "?_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
				}

				if(urltemplateType.equals("url_template_tobe"))
				{
					redirectUrl += "&partnerid=" + partnerId;
				}

			}

			// 디바이스가 모바일이면 쿠키 삽입 => 201120_하프PC 공통게이트에도 존재하는 쿠키 따라서, 공통게이트 페이지 인입시 pCheck 쿠키 삽입
			this.setCookie(response, siteCd, "pCheck", "Y", null, false, false);

		}

		return redirectUrl;
	}

	@Override
	public String getNaverGateRedirectUrl(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException {

		String redirectUrl = "";

		// 접속 URL 도메인 확인 후 사이트 확인
		String siteCd = getSiteCd(request);

		// 접속 단말 모바일/PC 여부 확인
		String deviceCd = getDeviceCd(request);

		Boolean isHaveQueryString = null != request.getQueryString() && 0 < request.getQueryString().length();

		// url_parameter (requestUri) 값 획득
		String urlParameter = request.getQueryString();

		String urltemplateType = "url_template_asis";

		// URL 쿼리 맵 획득
		Map<String, Object> queryMap = null;
		try {
			queryMap = splitQuery(urlParameter);
		} catch (UnsupportedEncodingException e) {

		}

		// 요청타입 획득
		String requestType = "";
		if (null != queryMap) {
			requestType = queryMap.getOrDefault("type", "").toString();
		}

		// PartnerId 획득
		//String partnerId = "";
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
		String userAgent = request.getHeader("User-Agent");
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

		//to-be 확인 여부
		String reqNfg = "";
		if (null != queryMap) {
			reqNfg = queryMap.getOrDefault("isnfg", "").toString();
			urltemplateType = reqNfg.toLowerCase().equals("y") ? "url_template_tobe" : "url_template_asis";
		}

		// 1. 게이트 매핑 테이블 템플릿 획득
		Map<String, Object> mappingTableSearchMap = new HashMap<>();
		mappingTableSearchMap.put("siteCd", siteCd);
		mappingTableSearchMap.put("deviceCd", deviceCd);
		mappingTableSearchMap.put("type", requestType);
		mappingTableSearchMap.put("partnerid", partnerId);
		List<Map<String, Object>> gateMappingTables = mysqlGateMapper.getGateMappingTables(mappingTableSearchMap);

		//해당 partnerid에 예외로직이 없을경우
		if(gateMappingTables.size() < 1){
			mappingTableSearchMap.put("partnerid", "");
			gateMappingTables = mysqlGateMapper.getGateMappingTables(mappingTableSearchMap);
		}

		// 에러 로그 Map
		Map<String, Object> excptLogMap = new HashMap<>();

		// 2. 매출코드 유효성 체크
		Map<String, Object> partnerIdDetailMap = mysqlGateMapper.getPartnerIdDetail(partnerId);

		// 매출코드가 DB에 없음
		if (null == partnerIdDetailMap) {
			// 매출코드가 정상이 아니어도 방문 카운트 Insert
			String partnerConnSeq = this.insertPartnerConn(partnerId, siteCd, deviceCd, clientIp, userAgent, refererUrl, pcid, uid, urlParameter);

			// todo: 예외처리.. 무엇을?

			// 에러 Log Insert
			excptLogMap.put("log_seq", partnerConnSeq);
			excptLogMap.put("log_type","1");
			int res = mysqlGateMapper.insertExcptLog(excptLogMap);

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

			redirectUrl = mappingTemplate.getOrDefault(urltemplateType, "").toString();

			if (redirectUrl.contains("?")){
				redirectUrl += "&_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
			}else{
				redirectUrl += "?_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
			}

		} else {
			// 매출코드가 존재
			// 네이버 매출코드 처리
			boolean isChangedNaverPartnerid = this.naverPartnerWork(response, partnerId, siteCd, deviceCd, queryMap);

			if (deviceCd.equals("001")) {
				// 현재 접속 장비가 PC
				// do nothing
			} else {
				// 현재 접속 장비가 MC
				// 매출코드를 MC매출코드로 변경
				if (0 < partnerIdDetailMap.getOrDefault("mobileid", "").toString().length()) {
					if (false == isChangedNaverPartnerid) {
						partnerId = partnerIdDetailMap.getOrDefault("mobileid", "").toString();
					}
				}
				//모바일일 때
				setCookie(response, siteCd, "mh_pc_ver", "N", 60 * 60 * 24 * 5, false, false);
			}

			// 방문 카운트 Insert
			String partnerConnSeq = this.insertPartnerConn(partnerId, siteCd, deviceCd, clientIp, userAgent, refererUrl, pcid, uid, urlParameter);

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

				// 에러 Log Insert
				excptLogMap.put("log_seq", partnerConnSeq);
				excptLogMap.put("log_type","2");
				int res = mysqlGateMapper.insertExcptLog(excptLogMap);

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

				redirectUrl = mappingTemplate.getOrDefault(urltemplateType, "").toString();

				if (redirectUrl.contains("?")){
					redirectUrl += "&_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
				}else{
					redirectUrl += "?_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
				}

				//네이버 프리미엄 로그 전환
				if ((partnerId.equals("h_naver_m") || partnerId.equals("b_naverdb")  || -1 < partnerId.indexOf("_naver_sbsa_m")) && null != queryMap.get("napm")) {
					redirectUrl += "&NaPm=" + queryMap.getOrDefault("napm", "").toString();
				}
			} else {
				// 템플릿 있음

				// 리다이렉트 url 획득
				redirectUrl = mappingTemplate.getOrDefault(urltemplateType, "").toString();


				// 파라메터 1~5 존재 확인 후 to-be 테이블에서 replace하기
				for (int index = 1; index < 6; index++) {
					if (0 < mappingTemplate.getOrDefault(String.format("param%d", index), "").toString().length()) {
						//String key = mappingTemplate.get(String.format("param%d", index)).toString();

						String key = "";

						// &para 단어 unicode 치환 이슈로 인해 p1~p5 방식으로 사용변경 ( 기존 사용유저 에러방지를 위해 param방식 살려둠 )
						if(request.getQueryString().contains("param")){
							key = String.format("param%d", index);
						}else{
							key = String.format("p%d", index);
						}

						String value = "";
						if (null != queryMap) {
							value = queryMap.getOrDefault(key, "").toString();
						}

						//차세대 오픈 시에는 Pcode를 prd_no로 변경하여 리다이렉트
						if(urltemplateType.equals("url_template_tobe") &&
								((requestType.equals("detail_prstcd") && (key.equals("param3") || key.equals("p3")))
										|| requestType.equals("detail_pcode") && (key.equals("param1") || key.equals("p1"))))
						{
							List<Map<String, Object>> prdNoInfo = postgreGateMapper.getPrdInfoByPCode(value.toUpperCase(), siteCd);
							if(prdNoInfo != null && prdNoInfo.size() > 0) {
								value = prdNoInfo.get(0).getOrDefault("prd_no", value).toString();
							}
						}

						redirectUrl = redirectUrl.replace(String.format("{param%d}", index), value);
					}
				}

				// response에 쿠키로 mnm 에 매출코드 심기
				addPartnerCookie(response, siteCd, partnerId);

				// redirect URL 뒤쪽에 방문 카운트 insert 한 레코드의 SEQ를 붙인다
				if (redirectUrl.contains("?")){
					redirectUrl += "&_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
				}else{
					redirectUrl += "?_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
				}

				if(urltemplateType.equals("url_template_tobe"))
				{
					redirectUrl += "&partnerid=" + partnerId;
				}

				if ((partnerId.equals("h_naver_m") || partnerId.equals("b_naverdb")  || -1 < partnerId.indexOf("_naver_sbsa_m")) && null != queryMap.get("napm")) {
					redirectUrl += "&NaPm=" + queryMap.getOrDefault("napm", "").toString();
				}

			}
		}

		// 디바이스가 모바일이면 쿠키 삽입 => 201120_하프PC 공통게이트에도 존재하는 쿠키 따라서, 공통게이트 페이지 인입시 pCheck 쿠키 삽입
		this.setCookie(response, siteCd, "pCheck", "Y", null, false, false);

		return redirectUrl;
	}

	@Override
	public String getDeviceCd(HttpServletRequest request) {
		String userAgent = request.getHeader("User-Agent").toUpperCase();

		if (userAgent.indexOf("MOBILE") > -1 || userAgent.indexOf("TRICYCLE") > -1) {
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
						query_pairs.put(URLDecoder.decode(pair.substring(0, idx).toLowerCase(), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
					}
				}
			}
		}
		return query_pairs;
	}

	@Override
	public void addPartnerCookie(HttpServletResponse response, String siteCd, String partnerId) {

		final Cookie cookie = new Cookie("mnm", this.partnerId);
		cookie.setDomain(siteCd.equals(SiteDefine.Halfclub.getSiteCd()) ? SiteDefine.Halfclub.getSiteCookieDomain() : SiteDefine.Boribori.getSiteCookieDomain());
		cookie.setPath("/");
		cookie.setMaxAge(60 * 60 * 6); // 60초 * 60분 * 6시간
		cookie.setSecure(false);
		cookie.setHttpOnly(false);
		response.addCookie(cookie);

		setCookie(response, siteCd, "NFG", "Y", 60 * 60 * 6, false, false);
	}

	@Override
	public void setCookie(HttpServletResponse response, String siteCd, String cookieName, String cookieValue, Integer expiry, boolean Secure, boolean HttpOnly) {

		final Cookie cookie = new Cookie(cookieName, cookieValue);
		cookie.setDomain(siteCd.equals(SiteDefine.Halfclub.getSiteCd()) ? SiteDefine.Halfclub.getSiteCookieDomain() : SiteDefine.Boribori.getSiteCookieDomain());
		cookie.setPath("/");
		if (null != expiry) {
			cookie.setMaxAge(expiry);
		}
		//else {
			//cookie.setMaxAge(60 * 60 * 24); // 60초 * 60분 * 24시
		//}
		cookie.setSecure(false);
		cookie.setHttpOnly(false);
		response.addCookie(cookie);
	}

	@Override
	public String insertPartnerConn(String partnerId, String siteCd, String deviceCd, String clientIp, String userAgent, String refererUrl, String pcid, String uid, String urlParameter) {
		Map<String, Object> partnerConnCountMap = new HashMap<>();
		partnerConnCountMap.put("partnerId", this.partnerId);
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

		return partnerConnSeq;
	}

	public boolean naverPartnerWork(HttpServletResponse response, String partnerId, String siteCd, String deviceCd, Map<String, Object> queryMap) {
		boolean isChangedNaverPartnerid = false;
		if (-1 < this.partnerId.indexOf("naverdb") || -1 < this.partnerId.indexOf("_naver_m")) {
			Boolean isNaverImported = false;
			List<String> chkNvKeys = Arrays.asList(
					"n_ad",
					"n_media",
					"n_rank",
					"n_query",
					"n_ad_group",
					"n_mall_pid",
					"n_campaign_type",
					"nv_ad"
			);

			for (String nvKey : chkNvKeys) {
				if (queryMap.containsKey(nvKey)) {
					isNaverImported = true;
					break;
				}
			}

			if (true == isNaverImported) {
				String sitePrefix = "";
				switch (siteCd) {
					case "1":
						sitePrefix = "h_";
						break;
					case "2":
						sitePrefix = "b_";
						break;
				}
				if (deviceCd.equals("001")) {
					this.partnerId = sitePrefix + "naver_sbsa_w";
				} else {
					this.partnerId = sitePrefix + "naver_sbsa_m";
				}
				isChangedNaverPartnerid = true;
			}

			// 네이버 마일리지 Ncisy 체크
			if (null != queryMap.get("ncisy")) {
				String sitePrefix = "";
				switch (siteCd) {
					case "1":
						sitePrefix = "H_";
						break;
					case "2":
						sitePrefix = "B_";
						break;
				}
				this.setCookie(response, siteCd, sitePrefix + "NaverNcisy", queryMap.getOrDefault("ncisy", "").toString(), null, false, false);
			}

			// 네이버 CPA 스크립트 관련
			if (null != queryMap.get("napm")) {
				this.setCookie(response, siteCd, "CPAValidator", queryMap.getOrDefault("napm", "").toString(), 60 * 60 * 24, false, false);
			}

			if (-1 < partnerId.indexOf("naverlogo")) {
			}
		}

		// 네이버 nv_pchs url 존재시 처리
		if (null != queryMap.get("nv_pchs")) {
			// nv_pchs 값을 쿠키로 입력

			this.setCookie(response, siteCd, "nv_pchs", queryMap.get("nv_pchs").toString(), 60 * 60 * 24 * 30, false, false);
		}

		return isChangedNaverPartnerid;
	}
}

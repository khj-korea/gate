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
import java.net.URLEncoder;
import java.util.*;

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
	public String getGateRedirectUrl(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException {

		String partnerId = "";
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
			String siteId = queryMap.getOrDefault("site", "").toString(); //IM요청-겟앤쇼 partnerid param 사용X
			partnerId = siteId.equals("") ? queryMap.getOrDefault("partnerid", "").toString() : siteId;
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

			//asis type 기기 랜딩 체크
			if(urltemplateType.equals("url_template_asis") && (requestType.toLowerCase().equals("category")
					|| (siteCd.equals("1") && deviceCd.equals("001") && (requestType.toLowerCase().equals("best_highlight") || requestType.toLowerCase().equals("best_category")))
					|| (requestType.toLowerCase().equals("search"))))
			{
				String typechk = typeChkAsis(siteCd, deviceCd, requestType, queryMap);
				if(!typechk.equals(requestType)){
					requestType = typechk;
					mappingTableSearchMap.put("type", requestType);
				}
			}

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
			excptLogMap.put("partner_id", partnerId);
			int res = mysqlGateMapper.insertExcptLog(excptLogMap);
			// 기본 매출코드 셋팅 후 홈 랜딩

			// response에 쿠키로 mnm 에 사이트별 기본 매출코드 심기
			addPartnerCookie(response, siteCd, partnerId, 0);

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
			// 아이라이크 param 확인
			if(!(queryMap.getOrDefault("valuefromclick", "").toString().equals("")))
			{
				Integer time = Integer.parseInt(partnerIdDetailMap.getOrDefault("cookie_time","0").toString()) * 60;
				setCookie(response, siteCd, "c_ValueFromClick", queryMap.getOrDefault("valuefromclick", "").toString(), time, false, false);
			}

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
				excptLogMap.put("partner_id", partnerId);
				int res = mysqlGateMapper.insertExcptLog(excptLogMap);
				// 기본 매출코드 셋팅 후 홈 랜딩

				// response에 쿠키로 mnm 에 사이트별 매출코드 심기
				addPartnerCookie(response, siteCd, partnerId, Integer.parseInt(partnerIdDetailMap.getOrDefault("cookie_time","0").toString()));

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
				addPartnerCookie(response, siteCd, partnerId, Integer.parseInt(partnerIdDetailMap.getOrDefault("cookie_time","0").toString()));

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

		//키워드검색광고 등 parameter 추가로 들어오는 것들에 대해 add 해줌
		redirectUrl = addRedirectUrl(redirectUrl, queryMap);
		
		return redirectUrl;
	}

	@Override
	public String getNaverGateRedirectUrl(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException {

		String partnerId = "";
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

			//asis type 기기 랜딩 체크
			if(urltemplateType.equals("url_template_asis") && (requestType.toLowerCase().equals("category")
					|| (siteCd.equals("1") && deviceCd.equals("001") && (requestType.toLowerCase().equals("best_highlight") || requestType.toLowerCase().equals("best_category")))
					|| (requestType.toLowerCase().equals("search"))))
			{
				String typechk = typeChkAsis(siteCd, deviceCd, requestType, queryMap);
				if(!typechk.equals(requestType)){
					requestType = typechk;
					mappingTableSearchMap.put("type", requestType);
				}
			}

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
			excptLogMap.put("partner_id", partnerId);
			int res = mysqlGateMapper.insertExcptLog(excptLogMap);

			// response에 쿠키로 mnm 에 사이트별 기본 매출코드 심기
			addPartnerCookie(response, siteCd, partnerId, 0);

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
				excptLogMap.put("partner_id", partnerId);
				int res = mysqlGateMapper.insertExcptLog(excptLogMap);

				// response에 쿠키로 mnm 에 사이트별 매출코드 심기
				addPartnerCookie(response, siteCd, partnerId, Integer.parseInt(partnerIdDetailMap.getOrDefault("cookie_time","0").toString()));

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
				addPartnerCookie(response, siteCd, partnerId, Integer.parseInt(partnerIdDetailMap.getOrDefault("cookie_time","0").toString()));

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

		//키워드검색광고 등 parameter 추가로 들어오는 것들에 대해 add 해줌
		redirectUrl = addRedirectUrl(redirectUrl, queryMap);

		return redirectUrl;
	}

	@Override
	public String getLpGateRedirectUrl(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException {

		String partnerId = "";
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
			//queryMap = splitQuery(requestUri);
		} catch (UnsupportedEncodingException e) {

		}

		// 요청타입, PartnerId, 링크프라이스 param, to-be 확인 여부 획득
		String requestType = "";
		String a_id = "", m_id = "", p_id = "", l_id = "", l_cd1 = "", l_cd2 = "", rd = "";
		String reqNfg = "";
		if (null != queryMap) {
			requestType = queryMap.getOrDefault("type", "").toString();
			partnerId = queryMap.getOrDefault("partnerid", "").toString();
			reqNfg = queryMap.getOrDefault("isnfg", "").toString();
			urltemplateType = reqNfg.toLowerCase().equals("y") ? "url_template_tobe" : "url_template_asis";

			a_id = queryMap.getOrDefault("a_id", "").toString();
			m_id = queryMap.getOrDefault("m_id", "").toString();
			p_id = queryMap.getOrDefault("p_id", "").toString();
			l_id = queryMap.getOrDefault("l_id", "").toString();
			l_cd1 = queryMap.getOrDefault("l_cd1", "").toString();
			l_cd2 = queryMap.getOrDefault("l_cd2", "").toString();
			rd = queryMap.getOrDefault("rd", "").toString();
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

		// 게이트 매핑 테이블 템플릿 획득
		//asis type 기기 랜딩 체크
		if(urltemplateType.equals("url_template_asis") && (requestType.toLowerCase().equals("category")
				|| (siteCd.equals("1") && deviceCd.equals("001") && (requestType.toLowerCase().equals("best_highlight") || requestType.toLowerCase().equals("best_category")))
				|| (requestType.toLowerCase().equals("search"))))
		{
			String typechk = typeChkAsis(siteCd, deviceCd, requestType, queryMap);
			if(!typechk.equals(requestType)){
				requestType = typechk;
			}
		}

		Map<String, Object> mappingTableSearchMap = new HashMap<>();
		mappingTableSearchMap.put("siteCd", siteCd);
		mappingTableSearchMap.put("deviceCd", deviceCd);
		mappingTableSearchMap.put("type", requestType);
		mappingTableSearchMap.put("partnerid", "");

		List<Map<String, Object>> gateMappingTables = mysqlGateMapper.getGateMappingTables(mappingTableSearchMap);

		// 에러 로그 Map
		Map<String, Object> excptLogMap = new HashMap<>();

		// 2. 매출코드 유효성 체크
		Map<String, Object> partnerIdDetailMap = mysqlGateMapper.getPartnerIdDetail(partnerId);
		if (null == partnerIdDetailMap) {
			// 매출코드가 DB에 없음..
			// 매출코드가 정상이 아니어도 방문 카운트 Insert
			String partnerConnSeq = this.insertPartnerConn(partnerId, siteCd, deviceCd, clientIp, userAgent, refererUrl, pcid, uid, urlParameter);

			// 에러 Log Insert
			excptLogMap.put("log_seq", partnerConnSeq);
			excptLogMap.put("log_type","1");
			excptLogMap.put("partner_id", partnerId);
			int res = mysqlGateMapper.insertExcptLog(excptLogMap);
			// 기본 매출코드 셋팅 후 홈 랜딩

			// response에 쿠키로 mnm 에 사이트별 기본 매출코드 심기
			addPartnerCookie(response, siteCd, partnerId, 0);

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

			// 방문 카운트 Insert
			String partnerConnSeq = this.insertPartnerConn(partnerId, siteCd, deviceCd, clientIp, userAgent, refererUrl, pcid, uid, urlParameter);

			// 링크프라이스 param check
			String lpinfo = "";
			if(a_id.equals("") || m_id.equals("") || p_id.equals("") || l_id.equals("") || l_cd1.equals("") || l_cd2.equals("") || rd.equals("")) {
				partnerId = siteCd.equals("1") ? "halfclub" : "b_boribori";

				// 에러 Log Insert
				excptLogMap.put("log_seq", partnerConnSeq);
				excptLogMap.put("log_type","3");
				excptLogMap.put("partner_id", partnerId);
				int res = mysqlGateMapper.insertExcptLog(excptLogMap);
			}
			else {

				Integer time = Integer.parseInt(partnerIdDetailMap.getOrDefault("cookie_time","0").toString()) * 60;
				lpinfo = a_id + "|" + p_id + "|" + l_id + "|" + l_cd1 + "|" + l_cd2;
				setCookie(response, siteCd, "LPINFO", lpinfo, time, false, false);
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

				// 에러 Log Insert
				excptLogMap.put("log_seq", partnerConnSeq);
				excptLogMap.put("log_type","2");
				excptLogMap.put("partner_id", partnerId);
				int res = mysqlGateMapper.insertExcptLog(excptLogMap);
				// 기본 매출코드 셋팅 후 홈 랜딩

				// response에 쿠키로 mnm 에 사이트별 매출코드 심기
				addPartnerCookie(response, siteCd, partnerId, Integer.parseInt(partnerIdDetailMap.getOrDefault("cookie_time","0").toString()));

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

				//lpinfo 존재할때,
				if(deviceCd.equals("002") && !lpinfo.equals("") && !queryMap.containsKey("lpinfo")) {
					redirectUrl = deepLinkLP(siteCd, queryMap, lpinfo, redirectUrl);
				}
				else {
					if (redirectUrl.contains("?")){
						redirectUrl += "&_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
					}else{
						redirectUrl += "?_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
					}
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
				addPartnerCookie(response, siteCd, partnerId, Integer.parseInt(partnerIdDetailMap.getOrDefault("cookie_time","0").toString()));

				//lpinfo 존재할때,
				if(deviceCd.equals("002") && !lpinfo.equals("") && !queryMap.containsKey("lpinfo")) {
					redirectUrl = deepLinkLP(siteCd, queryMap, lpinfo, redirectUrl);
				}
				else {
					if (redirectUrl.contains("?")){
						redirectUrl += "&_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
					}else{
						redirectUrl += "?_n_m2=" + partnerId + "&gSeq=" + partnerConnSeq;
					}
				}

			}

			// 디바이스가 모바일이면 쿠키 삽입 => 201120_하프PC 공통게이트에도 존재하는 쿠키 따라서, 공통게이트 페이지 인입시 pCheck 쿠키 삽입
			this.setCookie(response, siteCd, "pCheck", "Y", null, false, false);

		}

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
		String[] chkParam = {
				"site_cd",
				"device_cd",
				"type",
				"p1",
				"p2",
				"p3",
				"p4",
				"p5",
				"adseq",
				"partnerid",
				"site",
				"returnurl",
				"isnfg",
				"_n_m2",
				"gseq",
				"pcid",
				"uid",
				"kind",
				"par1",
				"par2",
				"par3",
				"par4",
				"par5",
				"par6",
				"par7",
				"par8",
				"par9"
		};

		Map<String, Object> query_pairs = new LinkedHashMap<>();
		if (null != query) {
			String[] pairs = query.split("&");
			if (null != pairs) {
				for (String pair : pairs) {
					int idx = pair.indexOf("=");
					if (-1 < idx) {
						if((Arrays.asList(chkParam).contains(pair.substring(0, idx).toLowerCase()))) {
							query_pairs.put(URLDecoder.decode(pair.substring(0, idx).toLowerCase(), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
						}
						else {
							query_pairs.put(URLDecoder.decode(pair.substring(0, idx).toLowerCase(), "UTF-8"), pair.substring(idx + 1));
						}
					}
				}
			}
		}
		return query_pairs;
	}

	@Override
	public void addPartnerCookie(HttpServletResponse response, String siteCd, String partnerId, Integer cookieTime) {
		if(cookieTime == 0) {
			cookieTime = 60 * 60 * 6;// 60초 * 60분 * 6시간
		}
		else {
			cookieTime = 60 * cookieTime;
		}

		final Cookie cookie = new Cookie("mnm", partnerId);
		cookie.setDomain(siteCd.equals(SiteDefine.Halfclub.getSiteCd()) ? SiteDefine.Halfclub.getSiteCookieDomain() : SiteDefine.Boribori.getSiteCookieDomain());
		cookie.setPath("/");
		cookie.setMaxAge(cookieTime); // 60초 * 60분 * 6시간
		cookie.setSecure(false);
		cookie.setHttpOnly(false);
		response.addCookie(cookie);

		setCookie(response, siteCd, "_partid_", partnerId, cookieTime, false, false);
		setCookie(response, siteCd, "NFG", "Y", cookieTime, false, false);
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

		return partnerConnSeq;
	}

	@Override
	public boolean naverPartnerWork(HttpServletResponse response, String partnerId, String siteCd, String deviceCd, Map<String, Object> queryMap) {
		boolean isChangedNaverPartnerid = false;
		if (-1 < partnerId.indexOf("naverdb") || -1 < partnerId.indexOf("_naver_m")) {
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
					partnerId = sitePrefix + "naver_sbsa_w";
				} else {
					partnerId = sitePrefix + "naver_sbsa_m";
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

	@Override
	public boolean numCheck(String category) {
		try {
			Double.parseDouble(category);
			return true;
		} catch(NumberFormatException e) {
			return false;
		}
	}

	@Override
	public String addRedirectUrl(String redirectUrl, Map<String, Object> queryMap) {

		String[] addParam = {
				"cccp", //크로스미디어
				"cckw", //크로스미디어
				"gclid" //ga
		};

		for(Map.Entry<String,Object> entry : queryMap.entrySet()){
			if(Arrays.asList(addParam).contains(entry.getKey())) {
				redirectUrl = redirectUrl + "&" + entry.getKey() + "=" + entry.getValue().toString();
			}
		}

		return redirectUrl;
	}

	@Override
	public String typeChkAsis(String siteCd, String deviceCd, String requestType, Map<String, Object> queryMap) {
		// asis type=best_category&best_highlight
		//하프 기기 = pc / 세팅은 모바일 카테고리 / pc로 전환시 에러
		if(siteCd.equals("1") && deviceCd.equals("001")
				&& ((requestType.toLowerCase().equals("best_highlight") && numCheck(queryMap.getOrDefault("p2", "").toString()))
				|| (requestType.toLowerCase().equals("best_category") && numCheck(queryMap.getOrDefault("p1", "").toString())))) {
			requestType = "best";
		}
		//asis type=category
		else if(requestType.toLowerCase().equals("category")) {
			//하프일때,
			if(siteCd.equals("1")) {
				//기기 pc && 숫자 일때 || 기기 mo && 문자 일때

				if((deviceCd.equals("001") && numCheck(queryMap.getOrDefault("p1", "").toString()))
						|| (deviceCd.equals("002") && !numCheck(queryMap.getOrDefault("p1", "").toString()))){
					requestType = "home";
				}
			}
			//보리일때,
			else {
				//기기 pc && 모바일 카테고리 || 기기 mo && 모바일카테고리 아닐 때
				if(deviceCd.equals("001") && queryMap.getOrDefault("p1", "").toString().contains("_")
						|| deviceCd.equals("002") && (!queryMap.getOrDefault("p1", "").toString().contains("_") && !(queryMap.getOrDefault("p1", "").toString().toLowerCase().contains("global")))) {
					requestType = "home";
				}
				else if(deviceCd.equals("002") && ((queryMap.getOrDefault("p1", "").toString().contains("_")) || queryMap.getOrDefault("p1", "").toString().toLowerCase().contains("global"))) {
					queryMap.put("p1",  queryMap.getOrDefault("p1", "").toString().replaceAll("_","/"));
				}
			}
		}
		else if(requestType.toLowerCase().equals("search")) {
			try {
				queryMap.put("p1",  URLEncoder.encode(queryMap.getOrDefault("p1", "").toString(),"UTF-8"));
			} catch (UnsupportedEncodingException e) {

			}
		}
		return requestType;
	}

	@Override
	public String  addLpParam(Map<String, Object> queryMap, String device) {

		List<String> addParam = Arrays.asList("site_cd", "device_cd", "type", "p1", "p2", "p3", "p4", "p5", "adseq", "isnfg");

		if (device.equals("M")) {
			addParam = Arrays.asList("site_cd", "device_cd", "type", "p1", "p2", "p3", "p4", "p5", "adseq", "isnfg",
					"a_id", "m_id", "p_id", "l_id", "l_cd1", "l_cd2", "rd");
		}

		String returnUrl = "";

		for(Map.Entry<String,Object> entry : queryMap.entrySet()){
			if(addParam.contains(entry.getKey())) {
				if(returnUrl.equals("")) {
					returnUrl = entry.getKey() + "=" + entry.getValue().toString();
				} else {
					returnUrl = returnUrl + "&" + entry.getKey() + "=" + entry.getValue().toString();
				}
			}
		}

		return returnUrl;
	}

	@Override
	public String appSchemeLP(String siteCd, String url) {

		String scheme = "";
		String returnUrl = "";

		if (siteCd.equals("1")) {
			scheme = "halfclub://?P_RETURN_URL=appurl";
		}
		else {
			scheme = "boribori://?P_RETURN_URL=appurl";
		}

		try {
			returnUrl = URLEncoder.encode((scheme.replaceAll("appurl", url)), "UTF-8");
		} catch (UnsupportedEncodingException e) {

		}

		return returnUrl;
	}

	@Override
	public String deepLinkLP(String siteCd, Map<String, Object> queryMap, String lpinfo, String redirectUrl) {
		String urlMobile = "", urlApp = "";

		try {
			if(siteCd.equals("1")) {
				urlMobile = "http://gate.halfclub.com/lp?" + addLpParam(queryMap, "M") + "&partnerid=linkprice_m&LPINFO=";
				urlApp = "http://gate.halfclub.com?" + addLpParam(queryMap, "APP") +  "&LPINFO=" + lpinfo + "&partnerid=h_linkprice_ap&ReturnUrl=" + redirectUrl;

				redirectUrl = "https://uOxGHKnFv0OKE14T4i6ZgQ.adtouch.adbrix.io/api/v1/click/xZPK2th5cEeit7ZQN0MZDA?cb_1=";
			} else {
				urlMobile = "http://gate.boribori.co.kr/lp?" + addLpParam(queryMap, "M") + "&partnerid=b_linkprice_m&LPINFO=";
				urlApp = "http://gate.boribori.co.kr?" + addLpParam(queryMap, "APP") +  "&LPINFO=" + URLEncoder.encode(lpinfo, "UTF-8") + "&partnerid=b_linkprice_ap&ReturnUrl=" + redirectUrl;

				redirectUrl = "https://t7Y3bT7AqkSXRBp4gH9VJw.adtouch.adbrix.io/api/v1/click/4HEpebpDFE2e4ykWsAKExw?cb_1=";
			}
			redirectUrl = redirectUrl  + URLEncoder.encode(lpinfo, "UTF-8") + "&abx_deeplink_url=" + appSchemeLP(siteCd, urlApp) + "&m_adtouch_custom_url=" + URLEncoder.encode(urlMobile,"UTF-8") + "&adv_agency=linkpcps_0522";
		} catch (UnsupportedEncodingException e) {

		}

		return redirectUrl;
	}

	@Override
	public String getLogPage(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException {

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

		String status = "";
		String kind, par1, par2, par3, par4, par5, par6, par7, par8, par9;

		kind = queryMap.getOrDefault("kind","").toString();
		par1 = queryMap.getOrDefault("par1","").toString();
		par2 = queryMap.getOrDefault("par2","").toString();
		par3 = queryMap.getOrDefault("par3","").toString();
		par4 = queryMap.getOrDefault("par4","").toString();
		par5 = queryMap.getOrDefault("par5","").toString();
		par6 = queryMap.getOrDefault("par6","").toString();
		par7 = queryMap.getOrDefault("par7","").toString();
		par8 = queryMap.getOrDefault("par8","").toString();
		par9 = queryMap.getOrDefault("par9","").toString();

		try{
			queryMap.put("pcid", pcid);
			queryMap.put("uid", uid);
			queryMap.put("kind", kind);
			queryMap.put("par1", par1);
			queryMap.put("par2", par2);
			queryMap.put("par3", par3);
			queryMap.put("par4", par4);
			queryMap.put("par5", par5);
			queryMap.put("par6", par6);
			queryMap.put("par7", par7);
			queryMap.put("par8", par8);
			queryMap.put("par9", par9);
			mysqlGateMapper.insertLog(queryMap);

			status = "success";
		}catch (Exception e){
			status = "fail";
		}

		return status;
	}
}

package com.tricycle.gate.service;

import lombok.Getter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public interface GateService {

	public enum SiteDefine {
		Halfclub("1", "하프클럽", "halfclub.com"),
		Boribori("2", "보리보리", "boribori.co.kr")
		;

		@Getter
		private String siteCd;

		@Getter
		private String siteName;

		@Getter
		private String siteCookieDomain;

		@Getter
		private String siteDefaultUrlMO;

		SiteDefine(String siteCd, String siteName, String siteCookieDomain) {
			this.siteCd = siteCd;
			this.siteName = siteName;
			this.siteCookieDomain = siteCookieDomain;
			this.siteDefaultUrlMO = "m." + siteCookieDomain;
		}
	}

	public List<Map<String, Object>> getGateMappingTables();

	public String getGateRedirectUrl(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, HttpSession httpSession) throws IOException;
	public String getNaverGateRedirectUrl(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, HttpSession httpSession) throws IOException;

	public String getDeviceCd(HttpServletRequest request);
	public String getSiteCd(HttpServletRequest request);
	public String getRequestType(HttpServletRequest request, String siteCd);
	public Map<String, Object> splitQuery(URL url) throws UnsupportedEncodingException;
	public Map<String, Object> splitQuery(String query) throws UnsupportedEncodingException;
	public void addPartnerCookie(HttpServletResponse response, String siteCd, String partnerId, Integer cookieTime);
	public void setCookie(HttpServletResponse response, String siteCd, String cookieName, String cookieValue, Integer expiry, boolean Secure, boolean HttpOnly);

	public String insertPartnerConn(String partnerId, String siteCd, String deviceCd, String clientIp, String userAgent, String refererUrl, String pcid, String uid, String urlParameter);
	public boolean naverPartnerWork(HttpServletResponse response, String partnerId, String siteCd, String deviceCd, Map<String, Object> queryMap);
	public boolean numCheck(String category);
	public String addRedirectUrl(String redirectUrl, Map<String, Object> queryMap);
	public String typeChkAsis(String siteCd, String deviceCd, String requestType, Map<String, Object> queryMap);
}

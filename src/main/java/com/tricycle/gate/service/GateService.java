package com.tricycle.gate.service;

import lombok.Getter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
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

	public String getGateRedirectUrl(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, HttpSession httpSession);

	public String getDeviceCd(HttpServletRequest request);
	public String getSiteCd(HttpServletRequest request);
	public String getRequestType(HttpServletRequest request, String siteCd);
	public Map<String, Object> splitQuery(URL url) throws UnsupportedEncodingException;
	public Map<String, Object> splitQuery(String query) throws UnsupportedEncodingException;
	public void addPartnerCookie(HttpServletResponse response, String siteCd, String partnerId);
	public void setCookie(HttpServletResponse response, String siteCd, String cookieName, String cookieValue, int expiry, boolean Secure, boolean HttpOnly);
}

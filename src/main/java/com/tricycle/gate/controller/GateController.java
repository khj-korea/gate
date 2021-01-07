package com.tricycle.gate.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.tricycle.gate.service.GateService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@Controller
@Slf4j
@RequestMapping("/**")
public class GateController {
	@Autowired
	private GateService gateService;

	@RequestMapping(value = "/index.html", method = RequestMethod.GET)
	@ResponseBody
	public String getIndexPage(Model model, final HttpSession session, HttpServletResponse response, HttpServletRequest request) {
		return "<html><body>Tricycle Gate-responsebody  <button type=\"button\" onclick=\"location.href='http://gate.halfclub.com?site_cd=1&type=Home' \">test referer</button> </body></html>";
	}

	@RequestMapping(method = RequestMethod.GET)
	public void getGatePage(Model model, final HttpSession session, HttpServletResponse response, HttpServletRequest request) throws IOException {

		String redirectUrl = "";

		// redirect url 획득하기
		redirectUrl = gateService.getGateRedirectUrl(request, response, session);

		if (null != redirectUrl && 0 < redirectUrl.length()) {

			redirectUrl = redirectUrl.replace("http:", "");
			redirectUrl = redirectUrl.replace("https:", "");

			// redirect url정상시 해당 url 이동

			response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
			response.setHeader("Location", redirectUrl);
		} else {
			// 에러.. 기존 매핑 url(디폴트 URL)로 이동
			String siteCd = gateService.getSiteCd(request);
			String deviceCd = gateService.getDeviceCd(request);
			if (siteCd.equals(GateService.SiteDefine.Halfclub.getSiteCd())) {
				response.setHeader("Location", deviceCd.equals("001") ? GateService.SiteDefine.Halfclub.getSiteCookieDomain() : GateService.SiteDefine.Halfclub.getSiteDefaultUrlMO());
			} else {
				response.setHeader("Location", deviceCd.equals("001") ? GateService.SiteDefine.Boribori.getSiteCookieDomain() : GateService.SiteDefine.Boribori.getSiteDefaultUrlMO());
			}
		}
	}

	@RequestMapping(value = "/naver", method = RequestMethod.GET)
	public void getNaverGatePage(Model model, final HttpSession session, HttpServletResponse response, HttpServletRequest request) throws IOException {

		String redirectUrl = "";

		// redirect url 획득하기
		redirectUrl = gateService.getNaverGateRedirectUrl(request, response, session);

		if (null != redirectUrl && 0 < redirectUrl.length()) {

			redirectUrl = redirectUrl.replace("http:", "");
			redirectUrl = redirectUrl.replace("https:", "");

			// redirect url정상시 해당 url 이동

			response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
			response.setHeader("Location", redirectUrl);
		} else {
			// 에러.. 기존 매핑 url(디폴트 URL)로 이동
			String siteCd = gateService.getSiteCd(request);
			String deviceCd = gateService.getDeviceCd(request);
			if (siteCd.equals(GateService.SiteDefine.Halfclub.getSiteCd())) {
				response.setHeader("Location", deviceCd.equals("001") ? GateService.SiteDefine.Halfclub.getSiteCookieDomain() : GateService.SiteDefine.Halfclub.getSiteDefaultUrlMO());
			} else {
				response.setHeader("Location", deviceCd.equals("001") ? GateService.SiteDefine.Boribori.getSiteCookieDomain() : GateService.SiteDefine.Boribori.getSiteDefaultUrlMO());
			}
		}
	}

	@RequestMapping(value = "/lp", method = RequestMethod.GET)
	public void getLpGatePage(Model model, final HttpSession session, HttpServletResponse response, HttpServletRequest request) throws IOException {

		String redirectUrl = "";

		// redirect url 획득하기
		redirectUrl = gateService.getLpGateRedirectUrl(request, response, session);

		if (null != redirectUrl && 0 < redirectUrl.length()) {

			redirectUrl = redirectUrl.replace("http:", "");
			redirectUrl = redirectUrl.replace("https:", "");

			// redirect url정상시 해당 url 이동

			response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
			response.setHeader("Location", redirectUrl);
		} else {
			// 에러.. 기존 매핑 url(디폴트 URL)로 이동
			String siteCd = gateService.getSiteCd(request);
			String deviceCd = gateService.getDeviceCd(request);
			if (siteCd.equals(GateService.SiteDefine.Halfclub.getSiteCd())) {
				response.setHeader("Location", deviceCd.equals("001") ? GateService.SiteDefine.Halfclub.getSiteCookieDomain() : GateService.SiteDefine.Halfclub.getSiteDefaultUrlMO());
			} else {
				response.setHeader("Location", deviceCd.equals("001") ? GateService.SiteDefine.Boribori.getSiteCookieDomain() : GateService.SiteDefine.Boribori.getSiteDefaultUrlMO());
			}
		}
	}
	@RequestMapping(value = "/log", method = RequestMethod.GET)
	public void getLog(Model model, final HttpSession session, HttpServletResponse response, HttpServletRequest request) throws IOException {

		String redirectUrl = "";

		// redirect url 획득하기
		redirectUrl = gateService.getLogPage(request, response, session);

	}
}
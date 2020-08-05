package com.tricycle.gate.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.tricycle.gate.service.GateService;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Controller
@Slf4j
@RequestMapping("/**")
public class GateController {
	@Autowired
	private GateService gateService;

	@RequestMapping(method = RequestMethod.GET)
	public String getGatePage(Model model, final HttpSession session, HttpServletResponse response, HttpServletRequest request) {

		gateService.getSample(1);

		String redirectUrl = "";

		// redirect url 획득하기
		redirectUrl = gateService.getGateRedirectUrl(request, response, session);

		if (null != redirectUrl && 0 < redirectUrl.length()) {

			redirectUrl = redirectUrl.replace("http://", "");
			redirectUrl = redirectUrl.replace("https://", "");

			// redirect url정상시 해당 url 이동
			return "redirect://" + redirectUrl;
		} else {
			// 에러.. 기존 매핑 url로 이동
			return "redirect://www.halfclub.com";
		}
	}
}

package com.tricycle.gate.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

public interface GateService {

	public int getSample(int i);

	public List<Map<String, Object>> getGateMappingTables();

	public String getGateRedirectUrl(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, HttpSession httpSession);
}

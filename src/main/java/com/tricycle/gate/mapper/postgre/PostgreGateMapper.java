package com.tricycle.gate.mapper.postgre;

import com.tricycle.gate.config.PostgreConnMapper;

import java.util.List;
import java.util.Map;

@PostgreConnMapper
public interface PostgreGateMapper {

    Map<String, Object> getPrdInfoByPrdno(String prdNo);
    List<Map<String, Object>> getPrdInfoByPCode(String pcode, String site_cd);
}

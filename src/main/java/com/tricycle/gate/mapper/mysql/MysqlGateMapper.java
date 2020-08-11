package com.tricycle.gate.mapper.mysql;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface MysqlGateMapper {

	List<Map<String, Object>> getGateMappingTables();

	int insertPartnerConnCount(Map<String, Object> partnerConnCountMap);

	Map<String, Object> getPartnerIdDetail(String partnerId);
}

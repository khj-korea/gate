package com.tricycle.gate.mapper.mysql;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface MysqlGateMapper {


	int getSample();

	List<Map<String, Object>> getTables();
}

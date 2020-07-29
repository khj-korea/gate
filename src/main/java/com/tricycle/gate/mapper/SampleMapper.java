package com.tricycle.gate.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SampleMapper {

	public int getSample(int i);
}

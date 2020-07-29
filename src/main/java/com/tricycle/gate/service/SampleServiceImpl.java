package com.tricycle.gate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tricycle.gate.mapper.SampleMapper;

@Service
public class SampleServiceImpl implements SampleService{

	@Autowired
	private SampleMapper sampleMapper;
	
	@Override
	public int getSample(int i) {
		return sampleMapper.getSample(i);
	}
}

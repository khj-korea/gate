package com.tricycle.gate.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tricycle.gate.service.SampleService;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/sample")
public class SampleController {
	@Autowired
    private SampleService sampleService;

//이 밑에는 알아서...
	@GetMapping("/getSample/{sampleId}")
    public int getSample(int i) {
      
		return sampleService.getSample(i);
    }
}

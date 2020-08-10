package com.tricycle.gate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;

@SpringBootApplication
public class GateApplication extends SpringBootServletInitializer{

	public static void main(String[] args) {
		SpringApplication.run(GateApplication.class, args);
	}

}

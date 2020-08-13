package com.tricycle.gate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class GateApplication extends SpringBootServletInitializer{

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(GateApplication.class);
		application.addListeners(new ApplicationPidFileWriter());
		application.run(args);
		
		//SpringApplication.run(GateApplication.class, args);
	}

}

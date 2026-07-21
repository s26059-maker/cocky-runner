package com.cocky.cockyrunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CockyRunnerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CockyRunnerApplication.class, args);
	}

}
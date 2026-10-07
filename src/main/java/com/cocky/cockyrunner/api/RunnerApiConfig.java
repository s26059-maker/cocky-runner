package com.cocky.cockyrunner.api;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RunnerApiProperties.class)
public class RunnerApiConfig {

    @Bean
    public FilterRegistrationBean<RunnerTokenFilter> runnerTokenFilter(RunnerApiProperties properties) {
        FilterRegistrationBean<RunnerTokenFilter> registration =
                new FilterRegistrationBean<>(new RunnerTokenFilter(properties.token()));
        registration.addUrlPatterns("/internal/*");
        return registration;
    }
}

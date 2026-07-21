package com.cocky.cockyrunner.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "runner.docker")
public record DockerProperties(
        Map<String, String> images,
        int timeoutSeconds,
        String memory,
        double cpus,
        int pidsLimit,
        int maxOutputBytes
) {
}
package com.cocky.cockyrunner.config;

import com.cocky.cockyrunner.domain.Language;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "runner.docker")
public record DockerProperties(
        Map<Language, LanguageDockerProperties> languages,
        int timeoutSeconds,
        String memory,
        double cpus,
        int pidsLimit,
        int maxOutputBytes,
        String workDir
) {
}
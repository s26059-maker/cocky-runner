package com.cocky.cockyrunner.config;

import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Single lookup point for {@link LanguageSpec}s. Every language-specific branch
 * in the judge pipeline (image, source file name, compile/run command, time
 * limit multiplier) should resolve through {@link #get(Language)} rather than
 * switching on {@link Language} directly elsewhere.
 *
 * <p>Lives in {@code config} rather than {@code domain} because building one
 * requires {@link DockerProperties} and a Spring {@link Component} lifecycle
 * (see below) - everything else in {@code domain} is a plain enum/record with
 * no framework or configuration dependency, and this class would have been the
 * one exception to that.
 *
 * <p>Per-language docker settings (image, startup budget) come from
 * {@link DockerProperties#languages()} (environment-configurable); everything else
 * about a {@link LanguageSpec} is fixed in code. Built once at construction time -
 * if any {@link Language} has no configured entry, no image, or a non-positive
 * startup budget, the constructor throws and, since this is a Spring bean, the
 * application fails to start rather than failing on the first grading request.
 */
@Component
public final class LanguageSpecRegistry {

    private final Map<Language, LanguageSpec> specs;

    public LanguageSpecRegistry(DockerProperties properties) {
        Map<Language, LanguageSpec> built = new EnumMap<>(Language.class);
        for (Language language : Language.values()) {
            LanguageDockerProperties languageProperties = resolveLanguageProperties(properties, language);
            String image = resolveImage(languageProperties, language);
            long startupBudgetMs = resolveStartupBudgetMs(languageProperties, language);
            built.put(language, buildSpec(language, image, startupBudgetMs));
        }
        this.specs = Map.copyOf(built);
    }

    private static LanguageDockerProperties resolveLanguageProperties(DockerProperties properties, Language language) {
        String key = language.name().toLowerCase();
        LanguageDockerProperties languageProperties =
                properties.languages() == null ? null : properties.languages().get(language);
        if (languageProperties == null) {
            throw new IllegalStateException(
                    "no docker configuration for language " + language
                            + " - set runner.docker.languages." + key);
        }
        return languageProperties;
    }

    private static String resolveImage(LanguageDockerProperties languageProperties, Language language) {
        String image = languageProperties.image();
        if (image == null || image.isBlank()) {
            throw new IllegalStateException(
                    "no docker image configured for language " + language
                            + " - set runner.docker.languages." + language.name().toLowerCase() + ".image");
        }
        return image;
    }

    private static long resolveStartupBudgetMs(LanguageDockerProperties languageProperties, Language language) {
        long startupBudgetMs = languageProperties.startupBudgetMs();
        if (startupBudgetMs <= 0) {
            throw new IllegalStateException(
                    "runner.docker.languages." + language.name().toLowerCase() + ".startup-budget-ms"
                            + " is missing or not positive: " + startupBudgetMs);
        }
        return startupBudgetMs;
    }

    private static LanguageSpec buildSpec(Language language, String image, long startupBudgetMs) {
        return switch (language) {
            case C -> new LanguageSpec(
                    image,
                    "main.c",
                    List.of("gcc", "-O2", "-std=c17", "-o", "main", "main.c", "-lm"),
                    List.of("./main"),
                    1.0,
                    startupBudgetMs
            );
            case PYTHON -> new LanguageSpec(
                    image,
                    "main.py",
                    List.of(),
                    List.of("python3", "main.py"),
                    3.0,
                    startupBudgetMs
            );
            case JAVA -> new LanguageSpec(
                    image,
                    "Main.java",
                    List.of("javac", "-encoding", "UTF-8", "-d", "/work", "Main.java"),
                    List.of("java", "-XX:+UseSerialGC", "-Xss8m", "-cp", "/work", "Main"),
                    2.0,
                    startupBudgetMs
            );
        };
    }

    public LanguageSpec get(Language language) {
        LanguageSpec spec = specs.get(language);
        if (spec == null) {
            throw new IllegalStateException("no LanguageSpec registered for language: " + language);
        }
        return spec;
    }
}

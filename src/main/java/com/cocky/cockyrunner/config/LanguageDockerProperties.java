package com.cocky.cockyrunner.config;

/**
 * Per-language docker configuration under {@code runner.docker.languages.<language>}:
 * which image to run the language in, and how much slack ({@code startupBudgetMs}) to
 * add to that language's resolved run timeout for container startup overhead. Bound as
 * the value type of {@link DockerProperties#languages()}.
 */
public record LanguageDockerProperties(
        String image,
        long startupBudgetMs
) {
}

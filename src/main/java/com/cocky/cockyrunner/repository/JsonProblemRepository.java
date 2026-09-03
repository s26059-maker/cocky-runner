package com.cocky.cockyrunner.repository;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.domain.Problem;
import com.cocky.cockyrunner.runner.DockerRunner;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class JsonProblemRepository implements ProblemRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonProblemRepository.class);
    private static final String LOCATION_PATTERN = "classpath:problems/*.json";

    private final Map<String, Problem> problems = new ConcurrentHashMap<>();

    /**
     * Loads every problem definition, then validates each one against every
     * {@link Language}: same principle as {@link LanguageSpecRegistry}'s image-
     * mapping check - a problem/language combination whose resolved timeout would
     * exceed {@link DockerRunner#MAX_TIMEOUT_MS} must fail application startup,
     * not surface as a confusing 400 the first time someone submits in that
     * language. Runs here (rather than e.g. inside {@link LanguageSpecRegistry})
     * because this is the one place that already loads the full problem set at
     * startup - {@link LanguageSpecRegistry} has no notion of a "problem" and
     * validating there would mean reaching back out to this repository instead.
     */
    public JsonProblemRepository(ObjectMapper objectMapper, LanguageSpecRegistry languageSpecRegistry,
                                  DockerProperties dockerProperties) {
        loadProblems(objectMapper);
        validateTimeLimits(problems.values(), languageSpecRegistry, dockerProperties.startupBudgetMs());
    }

    /**
     * Package-private and static (rather than a private instance method) so a test
     * can exercise the validation itself against a hand-built {@link Problem} list,
     * without going through classpath resource scanning - the resources under
     * {@code classpath:problems/*.json} are shared with every other test that
     * builds a full Spring context, so a deliberately-over-limit fixture file
     * there would break those too.
     *
     * @param startupBudgetMs same value {@link com.cocky.cockyrunner.service.JudgeService}
     *                        will add at run time (see {@link LanguageSpec#resolvedTimeoutMs}) -
     *                        passed in here too so this startup check validates the same
     *                        budget-inclusive value that will actually be used, not just the
     *                        bare multiplier result.
     */
    static void validateTimeLimits(Collection<Problem> problems, LanguageSpecRegistry languageSpecRegistry,
                                    long startupBudgetMs) {
        for (Problem problem : problems) {
            for (Language language : Language.values()) {
                LanguageSpec spec = languageSpecRegistry.get(language);
                long resolvedTimeoutMs = spec.resolvedTimeoutMs(problem.timeLimitMs(), startupBudgetMs);
                if (resolvedTimeoutMs > DockerRunner.MAX_TIMEOUT_MS) {
                    throw new IllegalStateException(
                            "problem '" + problem.id() + "' exceeds the max run timeout for language " + language
                                    + ": timeLimitMs=" + problem.timeLimitMs()
                                    + " x multiplier=" + spec.timeLimitMultiplier()
                                    + " = " + resolvedTimeoutMs + "ms, which is over the max of "
                                    + DockerRunner.MAX_TIMEOUT_MS + "ms - lower the problem's timeLimitMs "
                                    + "in its JSON definition");
                }
            }
        }
    }

    private void loadProblems(ObjectMapper objectMapper) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            log.error("Failed to scan problem definitions at {}", LOCATION_PATTERN, e);
            return;
        }

        if (resources.length == 0) {
            log.error("No problem definition files found at {}", LOCATION_PATTERN);
            return;
        }

        for (Resource resource : resources) {
            try (InputStream inputStream = resource.getInputStream()) {
                Problem problem = objectMapper.readValue(inputStream, Problem.class);
                problems.put(problem.id(), problem);
                log.info("Loaded problem '{}' from {}", problem.id(), resource.getFilename());
            } catch (IOException | JacksonException e) {
                log.error("Failed to parse problem definition file: {}", resource.getFilename(), e);
            }
        }
    }

    @Override
    public List<Problem> findAll() {
        return List.copyOf(problems.values());
    }

    @Override
    public Optional<Problem> findById(String id) {
        return Optional.ofNullable(problems.get(id));
    }
}
package com.cocky.cockyrunner.repository;

import com.cocky.cockyrunner.domain.Problem;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class JsonProblemRepository implements ProblemRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonProblemRepository.class);
    private static final String LOCATION_PATTERN = "classpath:problems/*.json";

    private final Map<String, Problem> problems = new ConcurrentHashMap<>();

    public JsonProblemRepository(ObjectMapper objectMapper) {
        loadProblems(objectMapper);
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
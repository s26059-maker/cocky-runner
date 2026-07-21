package com.cocky.cockyrunner.repository;

import com.cocky.cockyrunner.domain.Problem;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository {

    List<Problem> findAll();

    Optional<Problem> findById(String id);
}
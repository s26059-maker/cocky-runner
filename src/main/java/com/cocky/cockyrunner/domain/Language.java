package com.cocky.cockyrunner.domain;

/**
 * A language a submission can be judged in. This is a bare identity - all the
 * docker/compile/run configuration for a language lives in {@link LanguageSpec},
 * looked up via {@link com.cocky.cockyrunner.config.LanguageSpecRegistry}. Adding
 * a new language (e.g. JAVA) only requires adding a value here plus a matching
 * entry in the registry.
 */
public enum Language {
    C,
    PYTHON
}

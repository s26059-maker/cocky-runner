package com.cocky.cockyrunner.domain;

/**
 * A language a submission can be judged in. This is a bare identity - all the
 * docker/compile/run configuration for a language lives in {@link LanguageSpec},
 * looked up via {@link com.cocky.cockyrunner.config.LanguageSpecRegistry}. Adding
 * a new language (e.g. JAVA) means adding a value here <em>and</em> a matching
 * {@code case} to the switch in {@code LanguageSpecRegistry.buildSpec()} - the
 * switch is exhaustive over this enum, so the compiler refuses to build until
 * the second step is done too; you can't add one without the other.
 */
public enum Language {
    C,
    PYTHON,
    JAVA
}

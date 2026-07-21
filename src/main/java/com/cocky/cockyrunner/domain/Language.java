package com.cocky.cockyrunner.domain;

import java.util.List;

public enum Language {

    PYTHON("main.py", List.of("python", "/app/main.py"));

    private final String fileName;
    private final List<String> runCommand;

    Language(String fileName, List<String> runCommand) {
        this.fileName = fileName;
        this.runCommand = runCommand;
    }

    public String fileName() {
        return fileName;
    }

    public List<String> runCommand() {
        return runCommand;
    }
}
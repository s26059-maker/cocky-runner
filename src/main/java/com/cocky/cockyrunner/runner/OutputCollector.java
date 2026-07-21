package com.cocky.cockyrunner.runner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Drains an InputStream on its own thread so stdout/stderr can be read concurrently
 * without deadlocking on the process's pipe buffers. Caps captured output at maxBytes.
 */
class OutputCollector implements Runnable {

    private final InputStream inputStream;
    private final int maxBytes;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private volatile boolean truncated = false;

    OutputCollector(InputStream inputStream, int maxBytes) {
        this.inputStream = inputStream;
        this.maxBytes = maxBytes;
    }

    @Override
    public void run() {
        byte[] chunk = new byte[8192];
        int read;
        try {
            while ((read = inputStream.read(chunk)) != -1) {
                int remaining = maxBytes - buffer.size();
                if (remaining <= 0) {
                    truncated = true;
                    continue;
                }
                if (read > remaining) {
                    truncated = true;
                    buffer.write(chunk, 0, remaining);
                } else {
                    buffer.write(chunk, 0, read);
                }
            }
        } catch (IOException e) {
            // stream closed because the process was killed - nothing more to read
        }
    }

    String output() {
        String text = buffer.toString(StandardCharsets.UTF_8);
        return truncated ? text + "\n... (truncated)" : text;
    }
}
package com.cocky.cockyrunner.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects requests without a matching {@code X-Runner-Token} header with 401. Registered for /internal/** only. */
public class RunnerTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Runner-Token";

    private final byte[] expectedToken;

    public RunnerTokenFilter(String token) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        // constant-time compare so the token can't be probed byte by byte
        if (provided == null || !MessageDigest.isEqual(expectedToken, provided.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"invalid or missing " + HEADER + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}

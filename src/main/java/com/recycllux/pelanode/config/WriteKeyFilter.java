package com.recycllux.pelanode.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Step B-13. Every POST under /api requires header X-PN-Key = PN_WRITE_API_KEY
 * (P0-11 section 9). Reads stay open for the jury. With no key configured, writes
 * are open and a warning is logged: acceptable on a developer laptop only.
 */
@Component
public class WriteKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WriteKeyFilter.class);
    public static final String HEADER = "X-PN-Key";

    private final byte[] key;

    public WriteKeyFilter(PelanodeProperties props) {
        String k = props.writeApiKey();
        this.key = k == null || k.isBlank() ? null : k.getBytes(StandardCharsets.UTF_8);
        if (key == null) {
            log.warn("PN_WRITE_API_KEY is not set: write endpoints are OPEN. Set it on every shared deployment.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (key != null) {
            String given = req.getHeader(HEADER);
            if (given == null || !MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8))) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json");
                res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or wrong X-PN-Key\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}

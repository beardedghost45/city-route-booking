package com.cityroutebooking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Configuration
public class FrontendCorsConfiguration implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public FrontendCorsConfiguration(@Value("${app.cors.allowed-origins:}") String configuredOrigins) {
        this.allowedOrigins = parseOrigins(configuredOrigins);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Idempotency-Key", "If-Match", "Authorization")
                .exposedHeaders("ETag", "Location")
                .allowCredentials(false)
                .maxAge(3600);
    }

    private static List<String> parseOrigins(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(configuredOrigins.split(",", -1))
                .map(String::strip)
                .map(FrontendCorsConfiguration::normalizeOrigin)
                .toList();
    }

    private static String normalizeOrigin(String origin) {
        if (origin.isBlank() || origin.contains("*")) {
            throw new IllegalArgumentException("CORS origins must be explicit HTTP(S) origins without wildcards");
        }
        final URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid frontend CORS origin: " + origin, exception);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || host == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                || uri.getRawUserInfo() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("CORS entries must be exact HTTP(S) origins without a path: " + origin);
        }

        scheme = scheme.toLowerCase(Locale.ROOT);
        host = host.toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
            port = -1;
        }
        return scheme + "://" + host + (port == -1 ? "" : ":" + port);
    }
}

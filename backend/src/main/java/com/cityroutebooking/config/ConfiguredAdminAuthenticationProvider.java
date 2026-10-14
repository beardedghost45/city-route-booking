package com.cityroutebooking.config;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

final class ConfiguredAdminAuthenticationProvider implements AuthenticationProvider {

    private final String username;
    private final String password;

    ConfiguredAdminAuthenticationProvider(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BadCredentialsException("Invalid admin credentials");
        }
        String presentedUsername = authentication.getName();
        Object credentials = authentication.getCredentials();
        if (!(credentials instanceof String presentedPassword)) {
            throw new BadCredentialsException("Invalid admin credentials");
        }
        boolean usernameMatches = constantTimeEquals(username, presentedUsername);
        boolean passwordMatches = constantTimeEquals(password, presentedPassword);
        if (!(usernameMatches & passwordMatches)) {
            throw new BadCredentialsException("Invalid admin credentials");
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}

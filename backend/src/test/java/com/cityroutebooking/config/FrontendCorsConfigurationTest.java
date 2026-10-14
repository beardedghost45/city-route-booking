package com.cityroutebooking.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrontendCorsConfigurationTest {

    @Test
    void rejectsWildcardOriginsAndWildcardSubdomains() {
        assertThatThrownBy(() -> new FrontendCorsConfiguration("*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without wildcards");
        assertThatThrownBy(() -> new FrontendCorsConfiguration("https://*.example.test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without wildcards");
    }
}

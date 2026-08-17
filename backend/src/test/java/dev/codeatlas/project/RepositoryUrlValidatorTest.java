package dev.codeatlas.project;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryUrlValidatorTest {
    private final RepositoryUrlValidator validator = new RepositoryUrlValidator();

    @Test
    void rejectsNonHttpsRepositories() {
        assertThatThrownBy(() -> validator.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsUnapprovedHostsBeforeConnecting() {
        assertThatThrownBy(() -> validator.validate("https://example.com/project.git"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持");
    }

    @Test
    void rejectsCredentialsEmbeddedInUrl() {
        assertThatThrownBy(() -> validator.validate("https://token@github.com/acme/project.git"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("账号");
    }
}


package dev.codeatlas.project;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryIndexerTest {
    @Test
    void buildsOfficialGitHubArchiveUrlForDefaultBranch() {
        assertThat(RepositoryIndexer.githubArchiveUri(
                "https://github.com/spring-projects/spring-petclinic.git", "").toString())
                .isEqualTo("https://codeload.github.com/spring-projects/spring-petclinic/zip/HEAD");
    }

    @Test
    void preservesNamedBranchInOfficialArchiveUrl() {
        assertThat(RepositoryIndexer.githubArchiveUri(
                "https://github.com/acme/example.git", "feature/search").toString())
                .isEqualTo("https://codeload.github.com/acme/example/zip/refs/heads/feature/search");
    }
}

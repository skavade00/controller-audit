package io.github.controlleraudit.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMaskerTest {

    private final AuditMasker masker = new AuditMasker();

    @Test
    void masksJsonFields() {
        String out = masker.maskBody("{\"user\":\"a\",\"password\":\"p@ss\",\"nested\":{\"token\":123}}",
                Set.of("password", "token"));
        assertThat(out).contains("\"password\":\"****\"").contains("\"token\":\"****\"")
                .contains("\"user\":\"a\"").doesNotContain("p@ss");
    }

    @Test
    void masksTruncatedJson() {
        assertThat(masker.maskBody("{\"password\":\"abc", Set.of("password"))).doesNotContain("abc");
    }

    @Test
    void masksFormFields() {
        assertThat(masker.maskBody("a=1&password=xyz&b=2", Set.of("password")))
                .isEqualTo("a=1&password=****&b=2");
    }

    @Test
    void masksHeadersCaseInsensitively() {
        Map<String, List<String>> out = masker.maskHeaders(
                Map.of("Authorization", List.of("Bearer x"), "Accept", List.of("*/*")), Set.of("authorization"));
        assertThat(out.get("Authorization")).containsExactly("****");
        assertThat(out.get("Accept")).containsExactly("*/*");
    }
}

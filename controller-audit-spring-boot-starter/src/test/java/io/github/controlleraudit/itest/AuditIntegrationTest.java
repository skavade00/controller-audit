package io.github.controlleraudit.itest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestApp.class, properties = {
        "controller-audit.mode=SYNC",
        "spring.datasource.url=jdbc:h2:mem:audittest;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class AuditIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void auditsAndMasksAndHonoursAuditIgnore() throws Exception {
        mvc.perform(post("/hello")
                        .header("Authorization", "Bearer super-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sachin\",\"password\":\"hunter2\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/quiet")).andExpect(status().isOk());

        Integer count = jdbc.queryForObject("select count(*) from api_audit_log", Integer.class);
        assertThat(count).isEqualTo(1);

        String requestBody = jdbc.queryForObject("select request_body from api_audit_log", String.class);
        String requestHeaders = jdbc.queryForObject("select request_headers from api_audit_log", String.class);
        String responseBody = jdbc.queryForObject("select response_body from api_audit_log", String.class);
        assertThat(requestBody).contains("sachin").contains("\"password\":\"****\"").doesNotContain("hunter2");
        assertThat(requestHeaders).doesNotContain("super-secret");
        assertThat(responseBody).contains("hi sachin");
    }
}

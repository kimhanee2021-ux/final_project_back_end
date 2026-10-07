package org.ict.final_project_back_end.domain.board;

import com.jayway.jsonpath.JsonPath;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No enclosing test transaction: each request must commit its own transaction. */
@SpringBootTest
@AutoConfigureMockMvc
class BoardApiCommitIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;
  private static final String EMAIL = "board-commit-test@example.com";

  @AfterEach void cleanup() {
    users.findByEmail(EMAIL).ifPresent(user -> {
      jdbc.update("delete from board_posts where author_id = ?", user.getId());
      users.deleteById(user.getId());
    });
  }

  @Test void createCommitsBeforeIndependentRead() throws Exception {
    String signupBody = mvc.perform(post("/api/auth/signup").contentType("application/json")
        .content("{\"email\":\"" + EMAIL + "\",\"nickname\":\"커밋검증사용자\",\"password\":\"BoardTest123!\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = JsonPath.read(signupBody, "$.token");
    var result = mvc.perform(post("/api/board").header("Authorization", "Bearer " + token)
        .contentType("application/json").content("{\"category\":\"TEST\",\"title\":\"저장 확정\",\"content\":\"다른 요청에서 조회\",\"isAnonymous\":false}"))
        .andExpect(status().isCreated()).andReturn();
    long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    assertEquals("/api/board/" + id, result.getResponse().getHeader("Location"));
    assertEquals("저장 확정", jdbc.queryForObject("select title from board_posts where id = ?", String.class, id));
    mvc.perform(get("/api/board/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.title").value("저장 확정"));
  }
}

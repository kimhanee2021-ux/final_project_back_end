package org.ict.final_project_back_end.domain.board;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BoardApiIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private UserRepository users;
  @Autowired private JwtService jwt;
  private static final String VALID = "{\"category\":\"TEST\",\"title\":\"게임 이야기\",\"content\":\"내용입니다\",\"isAnonymous\":false}";

  private String signup() throws Exception {
    String body = mvc.perform(post("/api/auth/signup").contentType("application/json")
        .content("{\"email\":\"board-test@example.com\",\"nickname\":\"게시판테스트\",\"password\":\"BoardTest123!\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$.token");
  }

  private long create(String token, String json) throws Exception {
    String body = mvc.perform(post("/api/board").header("Authorization", "Bearer " + token)
        .contentType("application/json").content(json)).andExpect(status().isCreated())
        .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  @Test void signupLoginCreatePersistAndRead() throws Exception {
    signup();
    String loginBody = mvc.perform(post("/api/auth/login").contentType("application/json")
        .content("{\"email\":\"board-test@example.com\",\"password\":\"BoardTest123!\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = JsonPath.read(loginBody, "$.token");
    long authorId = users.findByEmail("board-test@example.com").orElseThrow().getId();
    long id = create(token, VALID);
    entityManager.flush();
    entityManager.clear();
    Object storedAuthor = entityManager.createNativeQuery("select author_id from board_posts where id = :id")
        .setParameter("id", id).getSingleResult();
    assertEquals(authorId, ((Number) storedAuthor).longValue());
    mvc.perform(get("/api/board/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.title").value("게임 이야기"))
        .andExpect(jsonPath("$.authorId").value(authorId))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());
  }

  @Test void suppliedAuthorCannotOverrideJwtAuthor() throws Exception {
    String token = signup();
    long id = create(token, VALID.replace("\"category\"", "\"authorId\":999999,\"category\""));
    entityManager.flush();
    long authorId = users.findByEmail("board-test@example.com").orElseThrow().getId();
    mvc.perform(get("/api/board/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(authorId));
  }

  @Test void anonymousDetailHidesAuthor() throws Exception {
    String token = signup();
    long id = create(token, VALID.replace("false", "true"));
    entityManager.flush();
    entityManager.clear();
    mvc.perform(get("/api/board/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.isAnonymous").value(true))
        .andExpect(jsonPath("$.authorId").doesNotExist());
  }

  @Test void unauthenticatedCreateIsRejected() throws Exception {
    mvc.perform(post("/api/board").contentType("application/json").content(VALID))
        .andExpect(status().isUnauthorized());
  }

  @Test void invalidJwtIsRejected() throws Exception {
    mvc.perform(post("/api/board").header("Authorization", "Bearer invalid-token")
        .contentType("application/json").content(VALID)).andExpect(status().isUnauthorized());
  }

  @Test void unauthenticatedReadIsRejected() throws Exception {
    mvc.perform(get("/api/board/1")).andExpect(status().isUnauthorized());
  }

  @Test void missingPostIs404() throws Exception {
    mvc.perform(get("/api/board/999999").header("Authorization", "Bearer " + signup()))
        .andExpect(status().isNotFound());
  }

  @Test void deletedUserCannotCreate() throws Exception {
    String token = jwt.generateToken(999999L, "deleted@example.com");
    mvc.perform(post("/api/board").header("Authorization", "Bearer " + token)
        .contentType("application/json").content(VALID)).andExpect(status().isNotFound());
  }

  @Test void blankTitleIs400AndDoesNotSave() throws Exception {
    String token = signup();
    mvc.perform(post("/api/board").header("Authorization", "Bearer " + token)
        .contentType("application/json").content(VALID.replace("게임 이야기", " ")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").exists());
    assertEquals(0L, ((Number) entityManager.createNativeQuery("select count(*) from board_posts")
        .getSingleResult()).longValue());
  }

  @Test void missingAnonymousFlagIs400() throws Exception {
    mvc.perform(post("/api/board").header("Authorization", "Bearer " + signup())
        .contentType("application/json").content(VALID.replace(",\"isAnonymous\":false", "")))
        .andExpect(status().isBadRequest());
  }
  @Test void invalidFieldsAre400() throws Exception {
    String token = signup();
    String[][] cases = {
        {VALID.replace("\"TEST\"", "\" \""), "category"},
        {VALID.replace("TEST", "가".repeat(31)), "category"},
        {VALID.replace("내용입니다", " "), "content"},
        {VALID.replace("게임 이야기", "가".repeat(256)), "title"},
        {VALID.replace("\"category\"", "\"tag\":\"" + "가".repeat(51) + "\",\"category\""), "tag"},
        {VALID.replace("false", "null"), "isAnonymous"}
    };
    for (String[] item : cases) {
      mvc.perform(post("/api/board").header("Authorization", "Bearer " + token)
          .contentType("application/json").content(item[0]))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + item[1]).exists());
    }
  }

  @Test void malformedJsonIs400() throws Exception {
    mvc.perform(post("/api/board").header("Authorization", "Bearer " + signup())
        .contentType("application/json").content("{"))
        .andExpect(status().isBadRequest());
  }}

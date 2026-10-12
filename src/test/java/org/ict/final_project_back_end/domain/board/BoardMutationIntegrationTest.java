package org.ict.final_project_back_end.domain.board;

import jakarta.persistence.EntityManager;
import org.ict.final_project_back_end.domain.board.entity.BoardPost;
import org.ict.final_project_back_end.domain.board.repository.BoardPostRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BoardMutationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired BoardPostRepository posts;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired EntityManager em;
  private BoardPost post;
  private String owner;
  private String other;
  private static final String BODY = """
      {"category":"B","appId":42,"tag":"sale","title":"수정 제목","content":"수정 본문","isAnonymous":true,"expiresAt":null}
      """;
  @BeforeEach void setup() {
    User a = users.save(User.builder().email("edit-a@example.invalid").nickname("작성자").passwordHash("test").build());
    User b = users.save(User.builder().email("edit-b@example.invalid").nickname("다른사람").passwordHash("test").build());
    owner = "Bearer " + jwt.generateToken(a.getId(), a.getEmail());
    other = "Bearer " + jwt.generateToken(b.getId(), b.getEmail());
    post = posts.saveAndFlush(BoardPost.builder().category("A").title("원본").content("원본 본문").author(a).build());
  }
  @Test void ownerUpdatesPersistedFieldsWithoutChangingAuthorOrCreatedAt() throws Exception {
    var id = post.getId(); var authorId = post.getAuthor().getId(); var created = post.getCreatedAt();
    mvc.perform(put("/api/board/{id}", id).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("수정 제목"));
    em.flush(); em.clear();
    BoardPost saved = posts.findById(id).orElseThrow();
    assertEquals("B", saved.getCategory()); assertEquals("수정 본문", saved.getContent());
    assertEquals(42L, saved.getAppId()); assertEquals("sale", saved.getTag()); assertTrue(saved.getAnonymous());
    assertEquals(authorId, saved.getAuthor().getId()); assertEquals(created.withNano(0), saved.getCreatedAt().withNano(0));
    assertNotNull(saved.getUpdatedAt());
  }
  @Test void otherUserCannotUpdateOrDelete() throws Exception {
    mvc.perform(put("/api/board/{id}", post.getId()).header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    mvc.perform(delete("/api/board/{id}", post.getId()).header("Authorization", other)).andExpect(status().isForbidden());
    em.clear(); assertEquals("원본", posts.findById(post.getId()).orElseThrow().getTitle());
  }
  @Test void ownerDeletesAndPostDisappears() throws Exception {
    var id = post.getId();
    mvc.perform(delete("/api/board/{id}", id).header("Authorization", owner)).andExpect(status().isNoContent());
    em.flush(); em.clear(); assertFalse(posts.existsById(id));
    mvc.perform(get("/api/board/{id}", id).header("Authorization", owner)).andExpect(status().isNotFound());
  }
  @Test void missingPostReturns404() throws Exception {
    mvc.perform(put("/api/board/{id}", Long.MAX_VALUE).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
    mvc.perform(delete("/api/board/{id}", Long.MAX_VALUE).header("Authorization", owner)).andExpect(status().isNotFound());
  }
  @Test void invalidUpdateDoesNotChangePost() throws Exception {
    mvc.perform(put("/api/board/{id}", post.getId()).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("수정 제목", " "))).andExpect(status().isBadRequest());
    em.clear(); assertEquals("원본", posts.findById(post.getId()).orElseThrow().getTitle());
  }
  @Test void mutationRequiresAuthentication() throws Exception {
    mvc.perform(put("/api/board/{id}", post.getId()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/board/{id}", post.getId())).andExpect(status().isUnauthorized());
  }

  @Test void linkedCommentPreventsDeletionWithoutLosingData() throws Exception {
    var comment = org.ict.final_project_back_end.domain.board.entity.BoardComment.builder()
        .post(post).author(post.getAuthor()).content("보존할 댓글").build();
    em.persist(comment); em.flush();
    mvc.perform(delete("/api/board/{id}", post.getId()).header("Authorization", owner))
        .andExpect(status().isConflict());
    em.clear(); assertTrue(posts.existsById(post.getId()));
    assertNotNull(em.find(org.ict.final_project_back_end.domain.board.entity.BoardComment.class, comment.getId()));
  }

  @Test void linkedImagePreventsDeletion() throws Exception {
    em.persist(org.ict.final_project_back_end.domain.board.entity.BoardPostImage.builder()
        .post(post).imageUrl("https://example.invalid/test.png").build()); em.flush();
    mvc.perform(delete("/api/board/{id}", post.getId()).header("Authorization", owner))
        .andExpect(status().isConflict());
    em.clear(); assertTrue(posts.existsById(post.getId()));
  }
}

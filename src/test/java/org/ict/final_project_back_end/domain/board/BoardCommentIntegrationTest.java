package org.ict.final_project_back_end.domain.board;

import jakarta.persistence.EntityManager;
import org.ict.final_project_back_end.domain.board.entity.*;
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
class BoardCommentIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired EntityManager em;
  @Autowired BoardPostRepository posts;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  private User author;
  private BoardPost post;
  private String owner;
  private String other;
  private String url;
  @BeforeEach void setup() {
    author = users.save(User.builder().email("comment-a@example.invalid").nickname("댓글작성자").passwordHash("test").build());
    User b = users.save(User.builder().email("comment-b@example.invalid").nickname("다른사람").passwordHash("test").build());
    owner = "Bearer " + jwt.generateToken(author.getId(), author.getEmail());
    other = "Bearer " + jwt.generateToken(b.getId(), b.getEmail());
    post = posts.saveAndFlush(BoardPost.builder().category("A").title("글").content("본문").author(author).build());
    url = "/api/board/" + post.getId() + "/comments";
  }
  private BoardComment add(String content) {
    BoardComment c = BoardComment.builder().post(post).author(author).content(content).build();
    em.persist(c); em.flush(); return c;
  }
  @Test void createUsesJwtAuthorAndPersistsContent() throws Exception {
    mvc.perform(post(url).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
        .content("{\"content\":\"댓글 본문\",\"authorId\":999}"))
        .andExpect(status().isCreated()).andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.content").value("댓글 본문"))
        .andExpect(jsonPath("$.authorId").value(author.getId()));
    em.clear();
    var saved = em.createQuery("select c from BoardComment c where c.post.id = :id", BoardComment.class)
        .setParameter("id", post.getId()).getSingleResult();
    assertEquals("댓글 본문", saved.getContent()); assertEquals(author.getId(), saved.getAuthor().getId());
  }
  @Test void listPaginatesInOldestOrderAndExcludesOtherPosts() throws Exception {
    var first = add("첫 댓글"); add("두번째");
    var p2 = posts.saveAndFlush(BoardPost.builder().category("A").title("다른 글").content("본문").author(author).build());
    em.persist(BoardComment.builder().post(p2).author(author).content("제외").build()); em.flush();
    mvc.perform(get(url).header("Authorization", owner).param("size", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].id").value(first.getId()))
        .andExpect(jsonPath("$.content[0].authorNickname").value("댓글작성자"));
    mvc.perform(get(url).header("Authorization", owner).param("size", "1").param("page", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].content").value("두번째"));
  }
  @Test void authorUpdatesAndDeletesComment() throws Exception {
    var c = add("원본"); var path = url + "/" + c.getId();
    mvc.perform(put(path).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"수정\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("수정"));
    em.clear(); assertEquals("수정", em.find(BoardComment.class, c.getId()).getContent());
    mvc.perform(delete(path).header("Authorization", owner)).andExpect(status().isNoContent());
    em.clear(); assertNull(em.find(BoardComment.class, c.getId()));
  }
  @Test void otherUserCannotMutateComment() throws Exception {
    var c = add("보존"); var path = url + "/" + c.getId();
    mvc.perform(put(path).header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"수정\"}")).andExpect(status().isForbidden());
    mvc.perform(delete(path).header("Authorization", other)).andExpect(status().isForbidden());
    em.clear(); assertEquals("보존", em.find(BoardComment.class, c.getId()).getContent());
  }
  @Test void missingPostCommentOrWrongPostReturns404() throws Exception {
    mvc.perform(get("/api/board/9223372036854775807/comments").header("Authorization", owner)).andExpect(status().isNotFound());
    mvc.perform(post("/api/board/9223372036854775807/comments").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"본문\"}")).andExpect(status().isNotFound());
    mvc.perform(delete(url + "/9223372036854775807").header("Authorization", owner)).andExpect(status().isNotFound());
    var c = add("댓글");
    var p2 = posts.saveAndFlush(BoardPost.builder().category("A").title("별도").content("본문").author(author).build());
    mvc.perform(delete("/api/board/" + p2.getId() + "/comments/" + c.getId()).header("Authorization", owner)).andExpect(status().isNotFound());
  }
  @Test void invalidContentAndPaginationAreRejected() throws Exception {
    for (String body : new String[]{"{}", "{\"content\":\" \"}", "{\"content\":\"" + "가".repeat(2001) + "\"}"}) {
      mvc.perform(post(url).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    mvc.perform(get(url).header("Authorization", owner).param("size", "101")).andExpect(status().isBadRequest());
    mvc.perform(get(url).header("Authorization", owner).param("page", "-1")).andExpect(status().isBadRequest());
  }
  @Test void allCommentActionsRequireAuthentication() throws Exception {
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}")).andExpect(status().isUnauthorized());
    mvc.perform(put(url + "/1").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}")).andExpect(status().isUnauthorized());
    mvc.perform(delete(url + "/1")).andExpect(status().isUnauthorized());
  }
  @Test void cannotDeleteCommentWithReplies() throws Exception {
    var parent = add("부모");
    em.persist(BoardComment.builder().post(post).author(author).parent(parent).content("답글").build()); em.flush();
    mvc.perform(delete(url + "/" + parent.getId()).header("Authorization", owner)).andExpect(status().isConflict());
    em.clear(); assertNotNull(em.find(BoardComment.class, parent.getId()));
  }
}

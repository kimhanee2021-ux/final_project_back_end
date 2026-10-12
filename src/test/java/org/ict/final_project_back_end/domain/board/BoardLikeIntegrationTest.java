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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BoardLikeIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired EntityManager em;
  @Autowired BoardPostRepository posts;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  private BoardPost post;
  private User author;
  private String owner;
  private String other;
  private String url;
  @BeforeEach void setup() {
    author = users.save(User.builder().email("like-a@example.invalid").nickname("작성자").passwordHash("test").build());
    var b = users.save(User.builder().email("like-b@example.invalid").nickname("추천자").passwordHash("test").build());
    owner = "Bearer " + jwt.generateToken(author.getId(), author.getEmail());
    other = "Bearer " + jwt.generateToken(b.getId(), b.getEmail());
    post = posts.saveAndFlush(BoardPost.builder().category("A").title("추천 글").content("본문").author(author).build());
    url = "/api/board/" + post.getId() + "/likes";
  }
  @Test void repeatedLikeIsIdempotentAndStatusIsPersonal() throws Exception {
    for (int i=0;i<2;i++) mvc.perform(put(url).header("Authorization", other))
        .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(1)).andExpect(jsonPath("$.liked").value(true));
    mvc.perform(get(url).header("Authorization", owner)).andExpect(status().isOk())
        .andExpect(jsonPath("$.likeCount").value(1)).andExpect(jsonPath("$.liked").value(false));
    mvc.perform(put(url).header("Authorization", owner)).andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(2));
    em.clear();
    assertEquals(2L, em.createQuery("select count(i) from ContentInteraction i where i.targetType = 'BOARD_POST' and i.targetId = :id and i.interactionType = 'LIKE'", Long.class).setParameter("id",post.getId()).getSingleResult());
  }
  @Test void repeatedCancelOnlyRemovesCurrentUsersLike() throws Exception {
    mvc.perform(put(url).header("Authorization", owner)).andExpect(status().isOk());
    mvc.perform(put(url).header("Authorization", other)).andExpect(status().isOk());
    for(int i=0;i<2;i++) mvc.perform(delete(url).header("Authorization", other))
        .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(1)).andExpect(jsonPath("$.liked").value(false));
    mvc.perform(get(url).header("Authorization", owner)).andExpect(status().isOk()).andExpect(jsonPath("$.liked").value(true));
  }
  @Test void authenticationAndMissingPostsAreRejected() throws Exception {
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(put(url)).andExpect(status().isUnauthorized());
    mvc.perform(delete(url)).andExpect(status().isUnauthorized());
    var missing = "/api/board/9223372036854775807/likes";
    mvc.perform(get(missing).header("Authorization", owner)).andExpect(status().isNotFound());
    mvc.perform(put(missing).header("Authorization", owner)).andExpect(status().isNotFound());
    mvc.perform(delete(missing).header("Authorization", owner)).andExpect(status().isNotFound());
  }
  @Test void postDeletionRemovesOnlyItsOwnInteractions() throws Exception {
    mvc.perform(put(url).header("Authorization", other)).andExpect(status().isOk());
    em.persist(ContentInteraction.builder().targetType("REVIEW").targetId(post.getId()).interactionType("LIKE").user(author).build()); em.flush();
    mvc.perform(delete("/api/board/"+post.getId()).header("Authorization",owner)).andExpect(status().isNoContent());
    em.clear();
    assertEquals(0L, em.createQuery("select count(i) from ContentInteraction i where i.targetType = 'BOARD_POST' and i.targetId = :id",Long.class).setParameter("id",post.getId()).getSingleResult());
    assertEquals(1L, em.createQuery("select count(i) from ContentInteraction i where i.targetType = 'REVIEW' and i.targetId = :id",Long.class).setParameter("id",post.getId()).getSingleResult());
  }
}

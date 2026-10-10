package org.ict.final_project_back_end.domain.board;

import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BoardListIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired BoardPostRepository posts;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  private User author;
  private String bearer;

  @BeforeEach void setup() {
    author = users.save(User.builder().email("board-list@example.invalid").nickname("목록검증")
        .passwordHash("test-only").build());
    bearer = "Bearer " + jwt.generateToken(author.getId(), author.getEmail());
  }

  private BoardPost add(String category, String title, String content, boolean anonymous) {
    return posts.save(BoardPost.builder().category(category).title(title).content(content)
        .anonymous(anonymous).author(author).createdAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build());
  }

  @Test void stableLatestOrderAndPaginationWithAnonymousSummary() throws Exception {
    BoardPost first = add("A", "첫 글", "본문", false);
    BoardPost second = add("A", "둘째 글", "본문", false);
    BoardPost third = add("A", "셋째 글", "본문", true);
    mvc.perform(get("/api/board").header("Authorization", bearer).param("size", "2"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].id").value(third.getId()))
        .andExpect(jsonPath("$.content[1].id").value(second.getId()))
        .andExpect(jsonPath("$.content[0].authorId").doesNotExist())
        .andExpect(jsonPath("$.content[0].content").doesNotExist())
        .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
    mvc.perform(get("/api/board").header("Authorization", bearer).param("page", "1").param("size", "2"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(first.getId()))
        .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.last").value(true));
    mvc.perform(get("/api/board").header("Authorization", bearer).param("page", "2").param("size", "2"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test void combinesCategoryAndCaseInsensitiveTitleOrContentSearch() throws Exception {
    add("A", "Steam 세일", "일반 본문", false);
    add("A", "추천 글", "steam 게임 이야기", false);
    add("B", "Steam 다른 게시판", "일반 본문", false);
    add("A", "기타 글", "다른 이야기", false);
    mvc.perform(get("/api/board").header("Authorization", bearer)
        .param("category", " A ").param("keyword", " STEAM "))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get("/api/board").header("Authorization", bearer).param("category", "B"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/board").header("Authorization", bearer).param("keyword", "Steam"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test void treatsSearchWildcardsAsLiteralCharacters() throws Exception {
    add("A", "100%_할인", "본문", false);
    add("A", "100XX할인", "본문", false);
    mvc.perform(get("/api/board").header("Authorization", bearer).param("keyword", "%_"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].title").value("100%_할인"));
  }

  @Test void rejectsUnauthenticatedList() throws Exception {
    mvc.perform(get("/api/board")).andExpect(status().isUnauthorized());
  }

  @Test void rejectsInvalidPaginationAndOversizedFilters() throws Exception {
    String[][] cases = {{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "x"},
        {"category", "가".repeat(31)}, {"keyword", "가".repeat(201)}};
    for (String[] item : cases) {
      mvc.perform(get("/api/board").header("Authorization", bearer).param(item[0], item[1]))
          .andExpect(status().isBadRequest());
    }
  }

  @Test void emptyListHasDefaultPageMetadata() throws Exception {
    mvc.perform(get("/api/board").header("Authorization", bearer).param("category", " ").param("keyword", " "))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(0));
  }
}

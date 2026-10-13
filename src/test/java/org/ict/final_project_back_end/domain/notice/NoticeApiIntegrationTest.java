package org.ict.final_project_back_end.domain.notice;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoticeApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired jakarta.persistence.EntityManager em;
  private String owner;
  private String other;
  private final ObjectMapper json = new ObjectMapper();
  private static final String BODY = "{\"title\":\"서비스 점검 안내\",\"content\":\"오늘 점검합니다.\"}";

  @BeforeEach void setup() {
    User a = users.save(User.builder().email("notice-a@example.invalid").nickname("공지작성자").passwordHash("test").build());
    User b = users.save(User.builder().email("notice-b@example.invalid").nickname("다른회원").passwordHash("test").build());
    owner = "Bearer " + jwt.generateToken(a.getId(), a.getEmail());
    other = "Bearer " + jwt.generateToken(b.getId(), b.getEmail());
  }

  private long create(String body) throws Exception {
    var result = mvc.perform(post("/api/notices").header("Authorization", owner)
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  @Test void createdNoticeCanBeReadWithTitleContentAndDate() throws Exception {
    long id = create(BODY);
    mvc.perform(get("/api/notices/{id}", id).header("Authorization", other))
        .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("서비스 점검 안내"))
        .andExpect(jsonPath("$.content").value("오늘 점검합니다."))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());
  }

  @Test void listHasStableNewestFirstPaginationAndOmitsBody() throws Exception {
    long first = create(BODY);
    long second = create(BODY.replace("서비스 점검 안내", "두 번째 공지"));
    mvc.perform(get("/api/notices?page=0&size=1").header("Authorization", owner))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(second))
        .andExpect(jsonPath("$.content[0].content").doesNotExist())
        .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
    mvc.perform(get("/api/notices?page=1&size=1").header("Authorization", owner))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(first));
  }

  @Test void authorCanUpdateAndDelete() throws Exception {
    long id = create(BODY);
    mvc.perform(put("/api/notices/{id}", id).header("Authorization", owner)
        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("오늘 점검합니다.", "점검 완료")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("점검 완료"));
    mvc.perform(get("/api/notices/{id}", id).header("Authorization", owner))
        .andExpect(jsonPath("$.content").value("점검 완료"));
    mvc.perform(delete("/api/notices/{id}", id).header("Authorization", owner)).andExpect(status().isNoContent());
    mvc.perform(get("/api/notices/{id}", id).header("Authorization", owner)).andExpect(status().isNotFound());
  }

  @Test void anotherMemberCannotModifyNotice() throws Exception {
    long id = create(BODY);
    mvc.perform(put("/api/notices/{id}", id).header("Authorization", other)
        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    mvc.perform(delete("/api/notices/{id}", id).header("Authorization", other)).andExpect(status().isForbidden());
    mvc.perform(get("/api/notices/{id}", id).header("Authorization", owner))
        .andExpect(jsonPath("$.content").value("오늘 점검합니다."));
  }

  @Test void missingNoticeReturns404ForReadUpdateAndDelete() throws Exception {
    mvc.perform(get("/api/notices/9223372036854775807").header("Authorization", owner)).andExpect(status().isNotFound());
    mvc.perform(put("/api/notices/9223372036854775807").header("Authorization", owner)
        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
    mvc.perform(delete("/api/notices/9223372036854775807").header("Authorization", owner)).andExpect(status().isNotFound());
  }

  @Test void invalidBodyIsRejectedWithoutCreatingNotice() throws Exception {
    for (String invalid : new String[]{"{\"title\":\" \",\"content\":\"본문\"}", "{\"title\":\"제목\",\"content\":\" \"}", "{}",
        BODY.replace("서비스 점검 안내", "x".repeat(256))}) {
      mvc.perform(post("/api/notices").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
          .content(invalid)).andExpect(status().isBadRequest());
    }
    mvc.perform(get("/api/notices").header("Authorization", owner)).andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test void invalidUpdateLeavesOriginalContent() throws Exception {
    long id = create(BODY);
    mvc.perform(put("/api/notices/{id}", id).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
        .content(BODY.replace("서비스 점검 안내", " "))).andExpect(status().isBadRequest());
    mvc.perform(get("/api/notices/{id}", id).header("Authorization", owner))
        .andExpect(jsonPath("$.title").value("서비스 점검 안내"));
  }

  @Test void invalidPaginationIsRejected() throws Exception {
    for (String query : new String[]{"page=-1", "size=0", "size=101", "page=abc"}) {
      mvc.perform(get("/api/notices?" + query).header("Authorization", owner)).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
  }

  @Test void endpointsRequireLogin() throws Exception {
    mvc.perform(get("/api/notices")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/notices/1")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/notices").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
    mvc.perform(put("/api/notices/1").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/notices/1")).andExpect(status().isUnauthorized());
  }

  @Test void errorsFollowTeamCodeContract() throws Exception {
    long id = create(BODY);
    mvc.perform(delete("/api/notices/{id}", id).header("Authorization", other))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOTICE_FORBIDDEN"));
    mvc.perform(get("/api/notices/9223372036854775807").header("Authorization", owner))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
    mvc.perform(post("/api/notices").header("Authorization", owner)
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.title").isNotEmpty()).andExpect(jsonPath("$.errors.content").isNotEmpty());
  }

  @Test void deletingAuthorAccountPreservesNoticeAndPreventsAnotherMemberTakingOwnership() throws Exception {
    long id = create(BODY);
    // 실제 요청처럼 공지 작성과 회원 탈퇴가 서로 다른 영속성 컨텍스트에서 실행된다.
    em.flush(); em.clear();
    mvc.perform(delete("/api/users/me").header("Authorization", owner)).andExpect(status().isNoContent());
    em.flush(); em.clear();
    mvc.perform(get("/api/notices/{id}", id).header("Authorization", other))
        .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("서비스 점검 안내"))
        .andExpect(jsonPath("$.authorId").isEmpty());
    mvc.perform(delete("/api/notices/{id}", id).header("Authorization", other)).andExpect(status().isForbidden());
  }
}

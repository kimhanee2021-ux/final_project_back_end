package org.ict.final_project_back_end.global.exception;

import com.jayway.jsonpath.JsonPath;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 에러 응답이 { "code": ..., "error": ... } 형태로 내려가는지 검증한다.
// 프론트는 code로만 분기하므로 code 값이 바뀌면 이 테스트가 깨져야 한다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorResponseCodeIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private GlobalExceptionHandler handler;

  private static final String SIGNUP = "{\"email\":\"code-test@example.com\",\"nickname\":\"코드테스트\",\"password\":\"CodeTest123!\"}";

  private String signup() throws Exception {
    String body = mvc.perform(post("/api/auth/signup").contentType("application/json").content(SIGNUP))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return "Bearer " + JsonPath.read(body, "$.token");
  }

  @Test void duplicateEmail() throws Exception {
    signup();
    mvc.perform(post("/api/auth/signup").contentType("application/json").content(SIGNUP))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
        .andExpect(jsonPath("$.error").value("이미 가입된 이메일입니다"));
  }

  @Test void duplicateNickname() throws Exception {
    signup();
    mvc.perform(post("/api/auth/signup").contentType("application/json")
            .content(SIGNUP.replace("code-test@", "other@")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"))
        .andExpect(jsonPath("$.error").value("이미 사용 중인 닉네임입니다"));
  }

  @Test void invalidRequest() throws Exception {
    mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.error").value("이메일과 비밀번호를 모두 입력해주세요"));
  }

  @Test void unknownEmailAndWrongPasswordAreInvalidCredentials() throws Exception {
    mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"email\":\"nobody@example.com\",\"password\":\"x\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error").value("가입되지 않은 이메일입니다"));
    signup();
    mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"email\":\"code-test@example.com\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error").value("비밀번호가 일치하지 않습니다"));
  }

  @Test void wrongCurrentPasswordIsInvalidCredentials() throws Exception {
    mvc.perform(patch("/api/users/me").header("Authorization", signup()).contentType("application/json")
            .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"NewPass123!\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error").value("현재 비밀번호가 일치하지 않습니다"));
  }

  @Test void invalidSteamLinkTokenIsInvalidCredentials() throws Exception {
    mvc.perform(get("/api/auth/steam").param("token", "invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error").value("유효하지 않거나 만료된 토큰입니다"));
  }

  @Test void invalidSteamState() throws Exception {
    mvc.perform(get("/api/auth/steam/callback").param("state", "invalid-state"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_STEAM_AUTH"));
  }

  @Test void steamNotLinked() throws Exception {
    mvc.perform(post("/api/library/steam/sync").header("Authorization", signup()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STEAM_NOT_LINKED"))
        .andExpect(jsonPath("$.error").value("스팀 계정이 연동되어 있지 않습니다"));
  }

  @Test void boardNotFound() throws Exception {
    mvc.perform(get("/api/board/999999").header("Authorization", signup()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOARD_POST_NOT_FOUND"));
  }

  @Test void constraintViolationIsValidationError() {
    var response = handler.handleConstraintViolation(new ConstraintViolationException("invalid", Set.of()));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("VALIDATION_ERROR", response.getBody().code());
  }

  @Test void requestBodyValidationIsValidationError() throws Exception {
    mvc.perform(post("/api/board").header("Authorization", signup()).contentType("application/json")
            .content("{\"category\":\"TEST\",\"title\":\"\",\"content\":\"내용\",\"isAnonymous\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.title").exists());
  }

  @Test void authenticationRequiredIsUnauthenticated() {
    var response = handler.handleBusinessException(new AuthenticationRequiredException());
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertEquals(new ErrorResponse("UNAUTHENTICATED", "로그인이 필요합니다."), response.getBody());
  }

  @Test void nullPointerIsInternalErrorNotUnauthorized() {
    var response = handler.handleNullPointer(new NullPointerException());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("INTERNAL_ERROR", response.getBody().code());
  }
}

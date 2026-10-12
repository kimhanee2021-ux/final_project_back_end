package org.ict.final_project_back_end.domain.board.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardPostCreateRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Test
  void 정상_입력은_통과한다() {
    var request = request("게임 이야기", "게시글 내용입니다.");

    assertTrue(validator.validate(request).isEmpty());
  }

  @Test
  void 공백만_있는_제목은_거부한다() {
    var request = request("   ", "게시글 내용입니다.");

    assertTrue(validator.validate(request).stream()
        .anyMatch(v -> v.getPropertyPath().toString().equals("title")));
  }

  @Test
  void 빈_내용은_거부한다() {
    var request = request("게임 이야기", "");

    assertTrue(validator.validate(request).stream()
        .anyMatch(v -> v.getPropertyPath().toString().equals("content")));
  }

  @Test
  void 제목이_255자를_넘으면_거부한다() {
    var request = request("가".repeat(256), "게시글 내용입니다.");

    assertTrue(validator.validate(request).stream()
        .anyMatch(v -> v.getPropertyPath().toString().equals("title")));
  }

  private BoardPostCreateRequest request(String title, String content) {
    return new BoardPostCreateRequest(
        "TEST",
        null,
        null,
        title,
        content,
        false,
        null
    );
  }
}
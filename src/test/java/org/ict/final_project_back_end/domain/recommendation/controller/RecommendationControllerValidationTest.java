package org.ict.final_project_back_end.domain.recommendation.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.ict.final_project_back_end.domain.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

// 컨트롤러를 직접 호출하면 Bean Validation을 거치지 않아, 실제 검증 설정(인터페이스/구현체 제약 충돌 HV000151 포함)을 따로 확인한다
class RecommendationControllerValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  private final RecommendationController controller = new RecommendationController(mock(RecommendationService.class));

  @Test
  void limitWithinRangeIsValid() throws NoSuchMethodException {
    assertTrue(validate(10).isEmpty());
    assertTrue(validate(1).isEmpty());
    assertTrue(validate(50).isEmpty());
  }

  @Test
  void limitOutOfRangeIsRejected() throws NoSuchMethodException {
    assertEquals(1, validate(0).size());
    assertEquals(1, validate(51).size());
  }

  private Set<ConstraintViolation<RecommendationController>> validate(int limit) throws NoSuchMethodException {
    Method method = RecommendationController.class.getMethod("getRecommendations", Long.class, int.class);
    return validator.forExecutables().validateParameters(controller, method, new Object[]{1L, limit});
  }
}

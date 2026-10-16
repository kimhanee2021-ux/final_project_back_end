package org.ict.final_project_back_end.domain.review.controller;

import org.ict.final_project_back_end.domain.review.exception.SteamUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** ReviewController에만 적용된다. 기존 전역 예외 처리와 충돌하지 않는다. */
@RestControllerAdvice(assignableTypes = ReviewController.class)
public class ReviewExceptionHandler {

    @ExceptionHandler(SteamUnavailableException.class)
    public ResponseEntity<Map<String, String>> handle(SteamUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", "STEAM_UNAVAILABLE",
                             "message", "스팀 리뷰를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."));
    }
}

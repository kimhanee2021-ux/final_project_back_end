package org.ict.final_project_back_end.domain.gamenews.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsDetailResponse;
import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsPageResponse;
import org.springframework.http.ResponseEntity;

@Tag(name = "Game news", description = "공식 소식의 한국어 AI 브리핑 공개 조회. 조회는 수집/AI 생성을 실행하지 않습니다.")
@ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 게임 번호, 분류 또는 페이징"),
    @ApiResponse(responseCode = "404", description = "게임 또는 공개 가능한 브리핑 없음")})
public interface GameNewsApiDocs {
  @Operation(summary = "전체 게임 소식 목록", description = "게시 시각 내림차순, 동일 시각이면 newsId 내림차순. 이전 성공본도 반환하며 원문 변경 시 stale=true입니다.")
  ResponseEntity<GameNewsPageResponse> list(
      @Parameter(description = "게임 필터. 생략하면 전체", example = "570") Long appId,
      @Parameter(description = "NOTICE, PATCH, EVENT, MAINTENANCE, DEV_NEWS, OTHER. 대소문자 무관", example = "PATCH") String category,
      @Parameter(description = "0부터 시작", example = "0") int page,
      @Parameter(description = "1~100, 기본 20", example = "20") int size);

  @Operation(summary = "게임별 소식 목록", description = "해당 게임의 공개 가능한 브리핑만 반환합니다. 브리핑이 없으면 빈 페이지입니다.")
  ResponseEntity<GameNewsPageResponse> listForGame(long appId, String category, int page, int size);

  @Operation(summary = "게임 소식 상세", description = "한국어 aiContent와 원문 링크를 제공합니다. 다른 게임의 소식과 최초 생성 미완료 소식은 404입니다. 시각은 UTC ISO 8601입니다.")
  ResponseEntity<GameNewsDetailResponse> detail(long appId, long newsId);
}

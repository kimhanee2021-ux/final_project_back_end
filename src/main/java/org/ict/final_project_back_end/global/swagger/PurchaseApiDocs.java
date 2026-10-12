package org.ict.final_project_back_end.global.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.final_project_back_end.domain.purchase.dto.LicenseImportRequest;
import org.ict.final_project_back_end.domain.purchase.dto.LicenseImportResponse;
import org.ict.final_project_back_end.domain.purchase.dto.PurchaseImportRequest;
import org.ict.final_project_back_end.domain.purchase.dto.PurchaseImportResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Map;

// PurchaseController의 springdoc(OpenAPI) 문서화를 전담하는 인터페이스.
@Tag(name = "Purchase", description = "스팀 구매내역 가져오기 API")
public interface PurchaseApiDocs {

  @Operation(
      summary = "스팀 구매내역 가져오기",
      description = "프론트가 스팀 구매내역 페이지(HTML)를 브라우저에서 파싱해 보낸 거래를 저장한다. "
          + "이미 가져온 거래(externalKey)는 건너뛰고, 결제 금액은 구매일 환율로 원화로 바꿔 함께 저장한다. "
          + "상품 이름은 보유 게임·게임 목록·스팀 검색으로 appId에 연결하며, DLC는 스팀에서 확인되면 본편 게임에 연결한다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "가져오기 성공",
          content = @Content(schema = @Schema(implementation = PurchaseImportResponse.class))),
      @ApiResponse(responseCode = "400", description = "요청 형식 오류 (code: VALIDATION_ERROR)", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content)
  })
  ResponseEntity<PurchaseImportResponse> importHistory(
      @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
      PurchaseImportRequest request
  );

  @Operation(
      summary = "스팀 라이선스 가져오기",
      description = "프론트가 스팀 라이선스 페이지(HTML)를 파싱해 보낸 라이선스(획득일·획득 방법)를 저장한다. 금액은 없으며, "
          + "구매내역에 결제 기록이 없는 게임(선물·무료 배포·제품 키)의 획득일과 이유를 보여주는 데 쓴다. "
          + "이름으로 내 보유 게임에만 연결하고, 이미 가져온 라이선스(externalKey)는 건너뛴다."
  )
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "가져오기 성공",
          content = @Content(schema = @Schema(implementation = LicenseImportResponse.class))),
      @ApiResponse(responseCode = "400", description = "요청 형식 오류", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content)
  })
  ResponseEntity<LicenseImportResponse> importLicenses(
      @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
      LicenseImportRequest request
  );

  @Operation(summary = "가져온 구매내역·라이선스 모두 삭제", description = "다시 가져오기 전에 초기화할 때 사용한다. 응답: { deleted: 삭제한 거래 수 }")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "삭제 성공"),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content)
  })
  ResponseEntity<Map<String, Integer>> deleteAll(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);
}

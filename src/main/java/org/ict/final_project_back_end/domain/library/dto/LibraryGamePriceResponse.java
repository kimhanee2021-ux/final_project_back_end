package org.ict.final_project_back_end.domain.library.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LibraryGamePriceResponse(

    @Schema(description = "스팀 App ID", example = "730")
    Long appId,

    @Schema(description = "게임 이름", example = "Counter-Strike 2")
    String name,

    @Schema(description = "무료 게임 여부. 가격 조회에 실패한 경우 null", example = "false")
    Boolean isFree,

    @Schema(description = "할인 전 원가(원). 무료 게임이면 0, 가격 조회에 실패한 경우 null", example = "32000")
    Integer originalPriceKrw,

    @Schema(description = "할인 적용 후 현재 가격(원). 무료 게임이면 0, 가격 조회에 실패한 경우 null", example = "16000")
    Integer currentPriceKrw,

    @Schema(description = "할인율(%). 할인이 없으면 0, 가격 조회에 실패한 경우 null", example = "50")
    Integer discountPercent
) {
}

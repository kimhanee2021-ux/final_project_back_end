package org.ict.final_project_back_end.domain.game.dto;

import java.util.List;

// appId 커서 방식 응답(오름차순). 다음 요청의 afterAppId로 nextAfterAppId를 넘기면 이어서 받을 수 있다.
// hasNext가 false이면 마지막 페이지다.
public record GameCursorPageResponse(
    List<GameResponse> content,
    Long nextAfterAppId,
    boolean hasNext
) {
}

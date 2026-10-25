package org.ict.final_project_back_end.domain.search.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 검색 모델 서버의 오류 응답 { "code", "error" }. 로그용이다 (code가 NO_SEARCH_KEYWORDS, SEARCH_UNAVAILABLE 등)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchModelError(String code, String error) {
}

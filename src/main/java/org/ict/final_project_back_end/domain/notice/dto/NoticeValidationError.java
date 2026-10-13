package org.ict.final_project_back_end.domain.notice.dto;

import java.util.Map;

public record NoticeValidationError(String code, String error, Map<String, String> errors) { }

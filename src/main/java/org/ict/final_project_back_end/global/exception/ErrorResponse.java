package org.ict.final_project_back_end.global.exception;

// code: 프론트가 분기에 쓰는 안정적인 식별자(문구가 바뀌어도 유지된다)
// error: 화면에 그대로 노출되는 사용자용 문구
public record ErrorResponse(String code, String error) {
}

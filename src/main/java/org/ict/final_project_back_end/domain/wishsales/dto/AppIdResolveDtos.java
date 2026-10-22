package org.ict.final_project_back_end.domain.wishsales.dto;

// [추가] 게임 이름 -> 스팀 appid 찾기 응답 형식.
// status: OK(찾음), NOT_FOUND(정확히 같은 이름의 기본 게임이 없음), ERROR(검색 실패)
public final class AppIdResolveDtos {
    private AppIdResolveDtos() {} // 모음 클래스라 객체를 만들지 않음

    public record ResolveResponse(String name, Long appId, String status, String reason) {
        public static ResolveResponse ok(String name, long appId) {
            return new ResolveResponse(name, appId, "OK", null);
        }
        public static ResolveResponse notFound(String name) {
            return new ResolveResponse(name, null, "NOT_FOUND", "정확히 같은 이름의 기본 게임을 찾지 못했어요");
        }
        public static ResolveResponse error(String name, String reason) {
            return new ResolveResponse(name, null, "ERROR", reason);
        }
    }
}

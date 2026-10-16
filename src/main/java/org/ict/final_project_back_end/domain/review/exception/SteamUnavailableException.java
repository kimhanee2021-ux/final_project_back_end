package org.ict.final_project_back_end.domain.review.exception;

public class SteamUnavailableException extends RuntimeException {

    public SteamUnavailableException(long appId, Throwable cause) {
        super("Steam review API unavailable. appId=" + appId, cause);
    }
}

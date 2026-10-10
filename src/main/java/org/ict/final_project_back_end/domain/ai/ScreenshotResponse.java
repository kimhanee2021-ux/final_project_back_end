package org.ict.final_project_back_end.domain.ai;

import java.util.List;

public record ScreenshotResponse(String summary, List<String> details, List<String> actions,
                                 String uncertainty, boolean isMock) { }

package org.ict.final_project_back_end.domain.aiManager.dto;
import java.util.List;
import jakarta.validation.constraints.*;
public record IdRequest(@Positive long appId){}

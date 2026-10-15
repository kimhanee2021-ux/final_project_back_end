package org.ict.final_project_back_end.domain.aiManager.dto;
import java.util.List;
import jakarta.validation.constraints.*;
public record Watch(List<Long> appIds,long targetWon,String label,boolean enabled,String lastChecked,Boolean matched,String error,List<ProductPrice> prices,Long totalMinor){}

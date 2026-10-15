package org.ict.final_project_back_end.domain.aiWorldcup.dto;
import java.util.List;
public record WorldcupResult(String explanation,List<WorldcupGame> pair,boolean complete,String summary,List<String> traits,List<String> evidence,String uncertainty,List<WorldcupGame> favorites) {}

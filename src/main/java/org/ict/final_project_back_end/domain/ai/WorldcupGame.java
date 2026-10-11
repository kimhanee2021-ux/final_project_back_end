package org.ict.final_project_back_end.domain.ai;
import java.util.List;
public record WorldcupGame(long appId,String name,String description,String imageUrl,List<String> genres,List<String> tags,List<String> groupIds,String releasedAt,int reviewCount,double sampleMedianHours,int sampleSize,Integer currentPlayers,String source) {}

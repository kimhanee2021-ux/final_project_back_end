package org.ict.final_project_back_end.domain.manager;
import java.util.List;
public record ProductPrice(long appId,String name,String type,Long parentAppId,String currency,Long minorPrice,String imageUrl,List<Long> dlcIds,String checkedAt) {}

package org.ict.final_project_back_end.domain.briefing.service;

import java.util.List;
import org.ict.final_project_back_end.domain.briefing.entity.OfficialNews;
public interface BriefingAiClient {
  record Translation(String gid,String title,String summary) {}
  record Analysis(List<BriefingContent.Item> items,List<Translation> translations) {}
  boolean configured();
  Analysis analyze(List<OfficialNews> sources) throws Exception;
}

package org.ict.final_project_back_end.domain.gamenews.client;

import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse.Article;
import org.ict.final_project_back_end.domain.gamenews.service.BriefingContent.Draft;

public interface BbsBriefingClient {
  boolean configured();
  String model();
  Draft generate(String gameName, Article article);
}

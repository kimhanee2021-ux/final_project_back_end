package org.ict.final_project_back_end.domain.gamenews.client.intent;

import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Slots;

public interface NewsVoiceIntentAiClient {
  Slots extract(String transcript);

  default Slots extract(String transcript, AiContext context) {
    return extract(transcript);
  }
}

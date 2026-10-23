package org.ict.final_project_back_end.domain.gamenews.client;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Slots;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;
public interface NewsVoiceIntentAiClient {
  Slots extract(String transcript);
  default Slots extract(String transcript, AiContext context) { return extract(transcript); }
}

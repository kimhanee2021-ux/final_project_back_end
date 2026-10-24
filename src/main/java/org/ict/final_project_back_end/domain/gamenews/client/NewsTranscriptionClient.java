package org.ict.final_project_back_end.domain.gamenews.client;

import java.util.List;

public interface NewsTranscriptionClient {
  String transcribe(byte[] audio, String mime, List<String> hints);
}

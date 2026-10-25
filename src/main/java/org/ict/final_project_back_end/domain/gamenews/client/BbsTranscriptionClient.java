package org.ict.final_project_back_end.domain.gamenews.client;

public interface BbsTranscriptionClient {
  boolean configured();
  String transcribe(byte[] audio, String mimeType);
}

package org.ict.final_project_back_end.domain.gamenews.client;

public interface BbsSpeechClient {
  boolean configured();
  Speech generate(String savedScript);
  record Speech(byte[] wav, double seconds) {}
}

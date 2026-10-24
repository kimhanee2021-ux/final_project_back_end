package org.ict.final_project_back_end.domain.gamenews.client;

/** One bounded utterance. Audio and transcripts are never persisted. */
public interface BbsLiveTranscriptionClient {
  boolean configured();
  Stream open(Events events);
  interface Stream extends AutoCloseable {
    void append(byte[] pcm);
    void commit();
    @Override void close();
  }
  interface Events {
    void ready();
    void partial(String text);
    void completed(String text);
    void failed();
  }
}

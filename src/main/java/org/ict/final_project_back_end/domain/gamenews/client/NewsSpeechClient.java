package org.ict.final_project_back_end.domain.gamenews.client;

import java.util.Arrays;
import java.util.function.*;

public interface NewsSpeechClient {
  byte[] synthesize(String text);
  default void stream(String text, Consumer<byte[]> chunks, BooleanSupplier cancelled) {
    byte[] wav = synthesize(text);
    if (!cancelled.getAsBoolean()) chunks.accept(Arrays.copyOfRange(wav, 44, wav.length));
  }
}

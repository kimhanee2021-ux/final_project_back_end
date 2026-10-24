package org.ict.final_project_back_end.domain.gamenews.audio;

import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.springframework.http.HttpStatus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

// 24kHz 모노 PCM16 리틀 엔디언 음성을 WAV로 변환하며 음성 제공자에 의존하지 않습니다.
public final class PcmWavEncoder {
  public static final int MAX_PCM_BYTES = 8_000_000;

  private PcmWavEncoder() {}

  public static byte[] encode(byte[] pcm) {
    if (pcm.length == 0 || pcm.length % 2 != 0 || pcm.length > MAX_PCM_BYTES) throw unavailable();
    ByteBuffer out = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
    out.put("RIFF".getBytes(StandardCharsets.US_ASCII))
        .putInt(36 + pcm.length)
        .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(24000)
        .putInt(48000)
        .putShort((short) 2)
        .putShort((short) 16)
        .put("data".getBytes(StandardCharsets.US_ASCII))
        .putInt(pcm.length)
        .put(pcm);
    return out.array();
  }

  private static GameSearchException unavailable() {
    return new GameSearchException(
        HttpStatus.BAD_GATEWAY,
        "SPEECH_UNAVAILABLE",
        "OpenAI 음성을 준비하지 못했어요. 잠시 후 다시 듣기를 누르거나 화면의 요약을 확인해 주세요.");
  }
}

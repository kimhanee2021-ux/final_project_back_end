package org.ict.final_project_back_end.domain.aiCommon.service;

import static org.springframework.http.HttpStatus.*;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;

// PC 사진 분석에서 쓰는 이미지 검증.
public final class AiImageValidator {
  private AiImageValidator() {}

  // Check dimensions without expanding compressed pixels into a large raster.
  public static void validateImage(byte[] b, String mime) throws Exception {
    int width, height;
    if ("image/webp".equals(mime)) {
      if (b.length < 30
          || !ascii(b, 0, "RIFF")
          || !ascii(b, 8, "WEBP")
          || uint(b, 4, 4) + 8 != b.length) throw invalid();
      int p = 12;
      width = 0;
      height = 0;
      while (p + 8 <= b.length) {
        long length = uint(b, p + 4, 4);
        int data = p + 8;
        if (length < 0 || data + length > b.length) throw invalid();
        if (ascii(b, p, "VP8X") && length >= 10) {
          width = 1 + (int) uint(b, data + 4, 3);
          height = 1 + (int) uint(b, data + 7, 3);
          break;
        }
        if (ascii(b, p, "VP8L") && length >= 5 && (b[data] & 255) == 0x2f) {
          long bits = uint(b, data + 1, 4);
          width = 1 + (int) (bits & 0x3fff);
          height = 1 + (int) ((bits >> 14) & 0x3fff);
          break;
        }
        if (ascii(b, p, "VP8 ")
            && length >= 10
            && (b[data + 3] & 255) == 0x9d
            && (b[data + 4] & 255) == 1
            && (b[data + 5] & 255) == 0x2a) {
          width = (int) uint(b, data + 6, 2) & 0x3fff;
          height = (int) uint(b, data + 8, 2) & 0x3fff;
          break;
        }
        p = (int) (data + length + (length & 1));
      }
    } else {
      try (var stream =
          new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(b))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw invalid();
        var reader = readers.next();
        try {
          String format = reader.getFormatName();
          if (("image/png".equals(mime) && !"png".equalsIgnoreCase(format))
              || ("image/jpeg".equals(mime) && !"jpeg".equalsIgnoreCase(format))) throw invalid();
          reader.setInput(stream, true, true);
          width = reader.getWidth(0);
          height = reader.getHeight(0);
        } finally {
          reader.dispose();
        }
      }
    }
    if (width <= 0 || height <= 0 || (long) width * height > 24_000_000)
      throw new AiException(BAD_REQUEST, "2400만 화소 이하의 정상적인 이미지를 선택해주세요.");
  }

  private static boolean ascii(byte[] b, int offset, String text) {
    if (offset + text.length() > b.length) return false;
    for (int i = 0; i < text.length(); i++)
      if (b[offset + i] != (byte) text.charAt(i)) return false;
    return true;
  }

  private static long uint(byte[] b, int offset, int count) {
    long value = 0;
    for (int i = 0; i < count; i++) value |= (long) (b[offset + i] & 255) << (8 * i);
    return value;
  }

  private static AiException invalid() {
    return new AiException(BAD_REQUEST, "파일 형식과 이미지 내용을 확인해주세요.");
  }
}

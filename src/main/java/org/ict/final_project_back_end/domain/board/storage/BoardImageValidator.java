package org.ict.final_project_back_end.domain.board.storage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.ict.final_project_back_end.domain.board.exception.BoardImageException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
@Component
public class BoardImageValidator {
  public record ValidatedImage(byte[] bytes, String extension) { }
  public ValidatedImage validate(MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) throw invalid("이미지는 20MB 이하의 비어 있지 않은 파일이어야 합니다.");
    try {
      byte[] bytes = file.getBytes();
      if (bytes.length > 20L * 1024 * 1024) throw invalid("이미지 용량이 너무 큽니다.");
      try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw invalid("JPG 또는 PNG 이미지만 업로드할 수 있습니다.");
        var reader = readers.next();
        try {
          reader.setInput(stream, true, true);
          String format = reader.getFormatName();
          String extension;
          if (format.equalsIgnoreCase("png")) extension = "png";
          else if (format.equalsIgnoreCase("jpeg") || format.equalsIgnoreCase("jpg")) extension = "jpg";
          else throw invalid("JPG 또는 PNG 이미지만 업로드할 수 있습니다.");
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width < 1 || height < 1 || (long)width * height > 40_000_000L)
            throw invalid("이미지는 최대 4천만 화소까지 업로드할 수 있습니다.");
          var decoded = reader.read(0);
          if (decoded == null) throw invalid("손상된 이미지입니다.");
          decoded.flush();
          return new ValidatedImage(bytes, extension);
        } finally { reader.dispose(); }
      }
    } catch(IOException | IllegalArgumentException e) { throw invalid("이미지 파일을 읽을 수 없습니다."); }
  }
  private BoardImageException invalid(String message) { return new BoardImageException(HttpStatus.BAD_REQUEST, "INVALID_BOARD_IMAGE", message); }
}

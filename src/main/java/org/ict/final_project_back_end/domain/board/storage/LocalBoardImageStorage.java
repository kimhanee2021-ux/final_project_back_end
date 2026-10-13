package org.ict.final_project_back_end.domain.board.storage;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.ict.final_project_back_end.domain.board.exception.BoardImageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
@Component
public class LocalBoardImageStorage implements BoardImageStorage {
  private final Path root;
  public LocalBoardImageStorage(@Value("${board.images.directory:./uploads/board}") String directory) {
    this.root = Path.of(directory).toAbsolutePath().normalize();
  }
  @Override public String save(byte[] bytes, String extension) {
    if (!extension.equals("png") && !extension.equals("jpg")) throw new BoardImageException(HttpStatus.BAD_REQUEST, "INVALID_BOARD_IMAGE", "지원하지 않는 이미지입니다.");
    String key = UUID.randomUUID() + "." + extension;
    Path path = resolve(key);
    try {
      Files.createDirectories(root);
      Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      return key;
    } catch(IOException e) {
      try { Files.deleteIfExists(path); } catch(IOException cleanup) { e.addSuppressed(cleanup); }
      throw new BoardImageException(HttpStatus.INTERNAL_SERVER_ERROR, "BOARD_IMAGE_STORAGE_ERROR", "이미지 저장에 실패했습니다.");
    }
  }
  @Override public byte[] read(String key) {
    try { return Files.readAllBytes(resolve(key)); }
    catch(NoSuchFileException e) { throw new BoardImageException(HttpStatus.NOT_FOUND, "BOARD_IMAGE_NOT_FOUND", "이미지 파일이 없습니다."); }
    catch(IOException e) { throw new BoardImageException(HttpStatus.INTERNAL_SERVER_ERROR, "BOARD_IMAGE_STORAGE_ERROR", "이미지를 읽을 수 없습니다."); }
  }
  @Override public void delete(String key) {
    try { Files.deleteIfExists(resolve(key)); }
    catch(IOException e) { throw new BoardImageException(HttpStatus.INTERNAL_SERVER_ERROR, "BOARD_IMAGE_STORAGE_ERROR", "이미지 파일 정리에 실패했습니다."); }
  }
  private Path resolve(String key) {
    if (key == null || !key.matches("[a-f0-9-]{36}\\.(png|jpg)"))
      throw new BoardImageException(HttpStatus.NOT_FOUND, "BOARD_IMAGE_NOT_FOUND", "이미지를 찾을 수 없습니다.");
    Path path = root.resolve(key).normalize();
    if (!path.getParent().equals(root)) throw new BoardImageException(HttpStatus.NOT_FOUND, "BOARD_IMAGE_NOT_FOUND", "이미지를 찾을 수 없습니다.");
    return path;
  }
}

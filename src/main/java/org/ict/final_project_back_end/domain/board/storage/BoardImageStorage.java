package org.ict.final_project_back_end.domain.board.storage;
public interface BoardImageStorage {
  String save(byte[] bytes, String extension);
  byte[] read(String key);
  void delete(String key);
}

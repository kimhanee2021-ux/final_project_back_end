package org.ict.final_project_back_end.domain.board.controller;
import java.net.URI;
import java.util.List;
import org.ict.final_project_back_end.domain.board.dto.BoardImageResponse;
import org.ict.final_project_back_end.domain.board.service.BoardImageService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/board/{postId}/images")
public class BoardImageController {
  private final BoardImageService service;
  public BoardImageController(BoardImageService service) { this.service=service; }
  @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<BoardImageResponse> upload(@AuthenticationPrincipal Long userId,
      @PathVariable("postId") Long postId,@RequestParam("file") MultipartFile file) {
    var response=service.upload(userId,postId,file);
    return ResponseEntity.created(URI.create(response.url())).body(response);
  }
  @GetMapping public List<BoardImageResponse> list(@PathVariable("postId") Long postId) { return service.list(postId); }
  @GetMapping("/files/{key}")
  public ResponseEntity<byte[]> file(@PathVariable("postId") Long postId,@PathVariable("key") String key) {
    byte[] bytes=service.read(postId,key);
    return ResponseEntity.ok().contentType(key.endsWith(".png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG)
        .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(bytes);
  }
  @PutMapping("/{imageId}/cover")
  public BoardImageResponse selectCover(@AuthenticationPrincipal Long userId,
      @PathVariable("postId") Long postId,@PathVariable("imageId") Long imageId) {
    return service.selectCover(userId,postId,imageId);
  }
  @DeleteMapping("/{imageId}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
      @PathVariable("postId") Long postId,@PathVariable("imageId") Long imageId) {
    service.delete(userId,postId,imageId); return ResponseEntity.noContent().build();
  }
}

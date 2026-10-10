package org.ict.final_project_back_end.domain.board.service;
import java.util.List;
import org.ict.final_project_back_end.domain.board.dto.BoardImageResponse;
import org.ict.final_project_back_end.domain.board.entity.BoardPost;
import org.ict.final_project_back_end.domain.board.entity.BoardPostImage;
import org.ict.final_project_back_end.domain.board.exception.*;
import org.ict.final_project_back_end.domain.board.repository.BoardPostRepository;
import org.ict.final_project_back_end.domain.board.repository.BoardPostImageRepository;
import org.ict.final_project_back_end.domain.board.storage.BoardImageStorage;
import org.ict.final_project_back_end.domain.board.storage.BoardImageValidator;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
@Service
@Transactional(readOnly = true)
public class BoardImageServiceImpl implements BoardImageService {
  private static final Logger log = LoggerFactory.getLogger(BoardImageServiceImpl.class);
  private final BoardPostRepository posts;
  private final BoardPostImageRepository images;
  private final BoardImageStorage storage;
  private final BoardImageValidator validator;
  public BoardImageServiceImpl(BoardPostRepository posts, BoardPostImageRepository images,
      BoardImageStorage storage, BoardImageValidator validator) {
    this.posts=posts; this.images=images; this.storage=storage; this.validator=validator;
  }
  @Override @Transactional
  public BoardImageResponse upload(Long userId, Long postId, MultipartFile file) {
    var post=ownedPost(userId, postId);
    var existing=images.findByPostIdOrderBySortOrderAscIdAsc(postId);
    if(existing.size() >= 10) throw new BoardImageException(HttpStatus.CONFLICT,"BOARD_IMAGE_LIMIT_EXCEEDED","이미지는 글당 최대 10장까지 업로드할 수 있습니다.");
    var validated=validator.validate(file);
    String key=storage.save(validated.bytes(), validated.extension());
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if(status != STATUS_COMMITTED) cleanup(key);
      }
    });
    int order=existing.stream().mapToInt(BoardPostImage::getSortOrder).max().orElse(-1)+1;
    var image=images.saveAndFlush(BoardPostImage.builder().post(post).imageUrl(url(postId,key)).sortOrder(order).build());
    if(post.getImageUrl()==null) post.selectImage(image.getImageUrl());
    return BoardImageResponse.from(image);
  }
  @Override public List<BoardImageResponse> list(Long postId) {
    if(!posts.existsById(postId)) throw new BoardPostNotFoundException();
    return images.findByPostIdOrderBySortOrderAscIdAsc(postId).stream().map(BoardImageResponse::from).toList();
  }
  @Override public byte[] read(Long postId, String key) {
    images.findByPostIdAndImageUrl(postId,url(postId,key)).orElseThrow(this::missingImage);
    return storage.read(key);
  }
  @Override @Transactional
  public BoardImageResponse selectCover(Long userId, Long postId, Long imageId) {
    var post=ownedPost(userId,postId);
    var image=images.findByIdAndPostId(imageId,postId).orElseThrow(this::missingImage);
    post.selectImage(image.getImageUrl());
    return BoardImageResponse.from(image);
  }
  @Override @Transactional
  public void delete(Long userId, Long postId, Long imageId) {
    var post=ownedPost(userId,postId);
    var image=images.findByIdAndPostId(imageId,postId).orElseThrow(this::missingImage);
    String imageUrl=image.getImageUrl();
    String key=imageUrl.substring(imageUrl.lastIndexOf('/')+1);
    images.delete(image); images.flush();
    if(imageUrl.equals(post.getImageUrl())) {
      var remaining=images.findByPostIdOrderBySortOrderAscIdAsc(postId);
      post.selectImage(remaining.isEmpty()?null:remaining.getFirst().getImageUrl());
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { cleanup(key); }
    });
  }
  private BoardPost ownedPost(Long userId,Long postId) {
    if(userId==null || userId<=0) throw new AuthenticationRequiredException();
    var post=posts.findByIdForUpdate(postId).orElseThrow(BoardPostNotFoundException::new);
    if(!post.getAuthor().getId().equals(userId)) throw new BoardPostForbiddenException();
    return post;
  }
  private BoardImageException missingImage() { return new BoardImageException(HttpStatus.NOT_FOUND,"BOARD_IMAGE_NOT_FOUND","이미지를 찾을 수 없습니다."); }
  private String url(Long postId,String key) { return "/api/board/"+postId+"/images/files/"+key; }
  private void cleanup(String key) {
    try { storage.delete(key); }
    catch(RuntimeException e) { log.error("이미지 파일 정리 실패: {}",key,e); }
  }
}

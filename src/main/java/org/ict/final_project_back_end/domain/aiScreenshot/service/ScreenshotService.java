package org.ict.final_project_back_end.domain.aiScreenshot.service;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiCommon.service.AiRateLimit;
import org.ict.final_project_back_end.domain.aiScreenshot.dto.ScreenshotResponse;

import java.io.ByteArrayInputStream;
import java.util.Set;
import javax.imageio.ImageIO;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import static org.springframework.http.HttpStatus.*;

@Service
public class ScreenshotService {
  private final GeminiScreenshotClient gemini;
  private final AiRateLimit limit;
  private final UserRepository users;
  public ScreenshotService(GeminiScreenshotClient gemini, AiRateLimit limit, UserRepository users) {
    this.gemini=gemini; this.limit=limit; this.users=users;
  }
  public ScreenshotResponse analyze(Long userId, MultipartFile image, String type, String game) {
    if(userId==null || !users.existsById(userId)) throw new AiException(UNAUTHORIZED,"로그인이 필요합니다.");
    if(!Set.of("item","play","error").contains(type) || game.length()>100)
      throw new AiException(BAD_REQUEST,"분석 유형과 게임 이름(100자 이내)을 확인해주세요.");
    if(image.isEmpty()) throw new AiException(BAD_REQUEST,"이미지를 선택해주세요.");
    if(image.getSize()>10*1024*1024) throw new AiException(PAYLOAD_TOO_LARGE,"이미지는 10MB 이하로 선택해주세요.");
    String mime=image.getContentType();
    if(mime==null || !Set.of("image/png","image/jpeg","image/webp").contains(mime))
      throw new AiException(BAD_REQUEST,"JPG, PNG, WEBP 이미지를 선택해주세요.");
    byte[] bytes;
    try { bytes=image.getBytes(); org.ict.final_project_back_end.domain.aiCommon.service.AiImageValidator.validateImage(bytes,mime); }
    catch(AiException e) { throw e; }
    catch(Exception e) { throw new AiException(BAD_REQUEST,"이미지를 읽을 수 없습니다. 다른 파일을 선택해주세요."); }
    limit.begin(userId);
    try { return gemini.analyzeScreenshot(bytes,mime,type,game.trim()); }
    finally { limit.end(userId); }
  }

}

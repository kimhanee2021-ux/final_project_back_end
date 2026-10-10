package org.ict.final_project_back_end.domain.ai;

import java.io.ByteArrayInputStream;
import java.util.Set;
import javax.imageio.ImageIO;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import static org.springframework.http.HttpStatus.*;

@Service
public class ScreenshotService {
  private final GeminiClient gemini;
  private final AiRateLimit limit;
  private final UserRepository users;
  public ScreenshotService(GeminiClient gemini, AiRateLimit limit, UserRepository users) {
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
    try { bytes=image.getBytes(); validateImage(bytes,mime); }
    catch(AiException e) { throw e; }
    catch(Exception e) { throw new AiException(BAD_REQUEST,"이미지를 읽을 수 없습니다. 다른 파일을 선택해주세요."); }
    limit.begin(userId);
    try { return gemini.analyzeScreenshot(bytes,mime,type,game.trim()); }
    finally { limit.end(userId); }
  }

  // Check dimensions without expanding compressed pixels into a large raster.
  private void validateImage(byte[] b,String mime) throws Exception {
    int width, height;
    if("image/webp".equals(mime)) {
      if(b.length<30 || !ascii(b,0,"RIFF") || !ascii(b,8,"WEBP") || uint(b,4,4)+8!=b.length) throw invalid();
      int p=12; width=0; height=0;
      while(p+8<=b.length) {
        long length=uint(b,p+4,4); int data=p+8;
        if(length<0 || data+length>b.length) throw invalid();
        if(ascii(b,p,"VP8X") && length>=10) {width=1+(int)uint(b,data+4,3);height=1+(int)uint(b,data+7,3);break;}
        if(ascii(b,p,"VP8L") && length>=5 && (b[data]&255)==0x2f) {
          long bits=uint(b,data+1,4);width=1+(int)(bits&0x3fff);height=1+(int)((bits>>14)&0x3fff);break;
        }
        if(ascii(b,p,"VP8 ") && length>=10 && (b[data+3]&255)==0x9d && (b[data+4]&255)==1 && (b[data+5]&255)==0x2a) {
          width=(int)uint(b,data+6,2)&0x3fff;height=(int)uint(b,data+8,2)&0x3fff;break;
        }
        p=(int)(data+length+(length&1));
      }
    } else {
      try(var stream=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(b))) {
        var readers=ImageIO.getImageReaders(stream);
        if(!readers.hasNext()) throw invalid();
        var reader=readers.next();
        try {
          String format=reader.getFormatName();
          if(("image/png".equals(mime) && !"png".equalsIgnoreCase(format)) || ("image/jpeg".equals(mime) && !"jpeg".equalsIgnoreCase(format))) throw invalid();
          reader.setInput(stream,true,true);width=reader.getWidth(0);height=reader.getHeight(0);
        } finally { reader.dispose(); }
      }
    }
    if(width<=0 || height<=0 || (long)width*height>24_000_000) throw new AiException(BAD_REQUEST,"2400만 화소 이하의 정상적인 이미지를 선택해주세요.");
  }
  private boolean ascii(byte[] b,int offset,String text) {if(offset+text.length()>b.length)return false;for(int i=0;i<text.length();i++)if(b[offset+i]!=(byte)text.charAt(i))return false;return true;}
  private long uint(byte[] b,int offset,int count) {long value=0;for(int i=0;i<count;i++)value|=(long)(b[offset+i]&255)<<(8*i);return value;}
  private AiException invalid(){return new AiException(BAD_REQUEST,"파일 형식과 이미지 내용을 확인해주세요.");}
}

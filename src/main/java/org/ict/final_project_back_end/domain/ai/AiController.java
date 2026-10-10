package org.ict.final_project_back_end.domain.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/ai") @Tag(name="AI 게임 추천·구매 상담")
public class AiController {
  private final AiService service; private final AiCatalog catalog; private final ScreenshotService screenshots;
  public AiController(AiService service,AiCatalog catalog,ScreenshotService screenshots){this.service=service;this.catalog=catalog;this.screenshots=screenshots;}
  @PostMapping(value="/screenshots", consumes="multipart/form-data")
  public ScreenshotResponse screenshot(@AuthenticationPrincipal Long userId,
      @RequestParam("image") org.springframework.web.multipart.MultipartFile image,
      @RequestParam(value="type",defaultValue="play") String type,
      @RequestParam(value="game",defaultValue="") String game) {
    return screenshots.analyze(userId,image,type,game);
  }
  @PostMapping("/chat") public AiResponse chat(@AuthenticationPrincipal Long userId,@Valid @RequestBody AiRequest request){return service.chat(userId,request);}
  @GetMapping("/games") public List<AiResponse.GameCard> search(@RequestParam(name="keyword") @Size(max=100) String keyword){return catalog.search(keyword);}
}

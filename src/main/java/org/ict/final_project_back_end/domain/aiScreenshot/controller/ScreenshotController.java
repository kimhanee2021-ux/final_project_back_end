package org.ict.final_project_back_end.domain.aiScreenshot.controller;
import org.ict.final_project_back_end.domain.aiScreenshot.service.ScreenshotService;
import org.ict.final_project_back_end.domain.aiScreenshot.dto.ScreenshotResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/ai")
public class ScreenshotController {
  private final ScreenshotService service;
  public ScreenshotController(ScreenshotService service){this.service=service;}
  @PostMapping(value="/screenshots",consumes="multipart/form-data")
  public ScreenshotResponse analyze(@AuthenticationPrincipal Long userId,@RequestParam("image") MultipartFile image,@RequestParam(value="type",defaultValue="play") String type,@RequestParam(value="game",defaultValue="") String game){return service.analyze(userId,image,type,game);}
}

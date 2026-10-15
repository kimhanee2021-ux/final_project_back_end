package org.ict.final_project_back_end.domain.aiWorldcup.controller;
import org.ict.final_project_back_end.domain.aiWorldcup.dto.WorldcupResult;
import org.ict.final_project_back_end.domain.aiWorldcup.service.WorldcupService;
import org.ict.final_project_back_end.domain.aiWorldcup.service.WorldcupCatalog;
import org.ict.final_project_back_end.domain.aiWorldcup.dto.WorldcupRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/ai/worldcup")
public class WorldcupController {
  private final WorldcupService service;private final WorldcupCatalog catalog;
  public WorldcupController(WorldcupService s,WorldcupCatalog c){service=s;catalog=c;}
  @GetMapping("/catalog") public Map<String,Object> catalog(@AuthenticationPrincipal Long id){service.authenticated(id);return Map.of("games",catalog.games(),"groups",catalog.groups(),"collectedAt","2026-10-11","count",catalog.games().size());}
  @PostMapping("/next") public WorldcupResult next(@AuthenticationPrincipal Long id,@Valid @RequestBody WorldcupRequest request){return service.next(id,request);}
}

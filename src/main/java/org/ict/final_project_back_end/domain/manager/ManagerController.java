package org.ict.final_project_back_end.domain.manager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ict.final_project_back_end.domain.library.service.LibraryService;
@RestController @RequestMapping("/api/ai/manager")
public class ManagerController {
 public record IdRequest(@Positive long appId){}
 public record WatchRequest(@NotNull @Size(min=1,max=6) List<@Positive Long> appIds,@Min(1) @Max(10000000) long targetWon,@NotBlank @Size(max=100) String label){}
 public record NameRequest(@NotBlank @Size(max=50) String name){}
 public record EnabledRequest(boolean enabled){}
 public record AiRequest(@NotBlank String mode,@NotBlank @Size(max=500)String message){}
 private final ManagerStore store;private final SteamProductClient steam;private final ManagerAiService ai;private final LibraryService library;
 public ManagerController(ManagerStore s,SteamProductClient p,ManagerAiService a,LibraryService l){store=s;steam=p;ai=a;library=l;}
 @GetMapping public Object state(@AuthenticationPrincipal Long user){return store.state(user);}
 @GetMapping("/products/{id}") public Object product(@AuthenticationPrincipal Long user,@PathVariable long id){store.auth(user);return steam.product(id);}
 @PostMapping("/sync") public Object sync(@AuthenticationPrincipal Long user){store.auth(user);library.syncSteamLibrary(user);return store.state(user);}
 @PostMapping("/owned") public Object owned(@AuthenticationPrincipal Long user,@Valid @RequestBody IdRequest r){return store.addOwned(user,r.appId());}
 @PostMapping("/collections") public Object collection(@AuthenticationPrincipal Long user,@RequestBody ManagerStore.CollectionDraft r){return store.collection(user,r);}
 @PatchMapping("/collections/{id}") public Object rename(@AuthenticationPrincipal Long user,@PathVariable Long id,@Valid @RequestBody NameRequest r){return store.rename(user,id,r.name());}
 @PostMapping("/watches") public Object watch(@AuthenticationPrincipal Long user,@Valid @RequestBody WatchRequest r){return store.saveWatch(user,null,r.appIds(),r.targetWon(),r.label());}
 @PutMapping("/watches/{id}") public Object change(@AuthenticationPrincipal Long user,@PathVariable Long id,@Valid @RequestBody WatchRequest r){return store.saveWatch(user,id,r.appIds(),r.targetWon(),r.label());}
 @PostMapping("/watches/{id}/check") public Object check(@AuthenticationPrincipal Long user,@PathVariable Long id){return store.check(user,id);}
 @PatchMapping("/watches/{id}") public Object pause(@AuthenticationPrincipal Long user,@PathVariable Long id,@RequestBody EnabledRequest r){return store.pause(user,id,r.enabled());}
 @PatchMapping("/notifications/{id}/read") public Object read(@AuthenticationPrincipal Long user,@PathVariable Long id){return store.read(user,id);}
 @DeleteMapping("/records/{id}") public void delete(@AuthenticationPrincipal Long user,@PathVariable Long id){store.delete(user,id);}
 @PostMapping("/draft") public Object draft(@AuthenticationPrincipal Long user,@Valid @RequestBody AiRequest r){return ai.draft(user,r.mode(),r.message());}
}

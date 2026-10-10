package org.ict.final_project_back_end.domain.ai;

import java.util.List;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import static org.springframework.http.HttpStatus.*;

@Service
public class AiService {
  private final AiCatalog catalog; private final GeminiClient gemini; private final AiRateLimit limit; private final UserRepository users;
  public AiService(AiCatalog catalog,GeminiClient gemini,AiRateLimit limit,UserRepository users){this.catalog=catalog;this.gemini=gemini;this.limit=limit;this.users=users;}
  public AiResponse chat(Long userId,AiRequest request){
    if(userId==null || !users.existsById(userId))throw new AiException(UNAUTHORIZED,"로그인이 필요합니다.");
    var candidates=catalog.candidates(request);
    if(candidates.isEmpty())return new AiResponse("조건에 맞는 게임 정보가 없습니다. 장르를 바꾸거나 다른 게임을 검색해주세요.",List.of());
    limit.begin(userId);
    try{return gemini.generate(request,candidates);}finally{limit.end(userId);}
  }
}

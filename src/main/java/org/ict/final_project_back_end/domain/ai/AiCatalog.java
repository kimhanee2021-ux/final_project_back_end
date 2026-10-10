package org.ict.final_project_back_end.domain.ai;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.http.HttpStatus.*;

@Component @Transactional(readOnly=true)
public class AiCatalog {
  private final GameRepository games;
  private final EntityManager em;
  public AiCatalog(GameRepository games, EntityManager em){this.games=games;this.em=em;}
  public List<Game> candidates(AiRequest request){
    if("purchase".equals(request.mode())){
      if(request.gameId()==null) throw new AiException(BAD_REQUEST,"상담할 게임을 먼저 선택해주세요.");
      return List.of(games.findById(request.gameId()).orElseThrow(()->new AiException(NOT_FOUND,"게임 정보를 찾을 수 없습니다.")));
    }
    String genre=request.genre()==null ? "" : request.genre().strip();
    return em.createQuery("select g from Game g where (:genre = '' or lower(g.genre) like :pattern escape '!') order by g.reviewCount desc, g.appId asc",Game.class)
        .setParameter("genre",genre).setParameter("pattern","%"+escape(genre.toLowerCase(java.util.Locale.ROOT))+"%")
        .setMaxResults(30).getResultList();
  }
  public List<AiResponse.GameCard> search(String keyword){
    if(keyword==null || keyword.isBlank()) return List.of();
    return em.createQuery("select g from Game g where lower(g.name) like :pattern escape '!' order by g.name, g.appId",Game.class)
        .setParameter("pattern","%"+escape(keyword.strip().toLowerCase(java.util.Locale.ROOT))+"%")
        .setMaxResults(10).getResultList().stream().map(g->AiResponse.GameCard.from(g,"")).toList();
  }
  private String escape(String text){return text.replace("!","!!").replace("%","!%").replace("_","!_");}
}

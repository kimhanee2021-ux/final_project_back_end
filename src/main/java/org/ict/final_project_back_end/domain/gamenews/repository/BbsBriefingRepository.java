package org.ict.final_project_back_end.domain.gamenews.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse.Article;
import org.ict.final_project_back_end.domain.gamenews.service.BriefingContent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Explicit column writes preserve ALL legacy news/briefing values. No duplicate Game mapping or cascade. */
@Repository
public class BbsBriefingRepository {
  private final JdbcTemplate jdbc;
  public BbsBriefingRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public record Saved(long newsId, String payload, String hash, String model, String version, Instant generatedAt, Instant claimUntil) {
    public boolean matches(String sourceHash, String currentModel) {
      return payload != null && generatedAt != null && Objects.equals(hash, sourceHash)
          && Objects.equals(model, currentModel) && BriefingContent.PROMPT_VERSION.equals(version);
    }
  }
  public Optional<Saved> find(long appId, String sourceId) {
    return jdbc.query("""
        SELECT b.news_id,b.gpt_payload,b.gpt_source_hash,b.gpt_model_name,b.gpt_prompt_version,
               b.gpt_generated_at,b.gpt_claim_until
        FROM game_news n JOIN game_news_briefings b ON b.news_id=n.id
        WHERE n.app_id=? AND n.source_news_id=?
        """, (r,i) -> new Saved(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),
            instant(r.getTimestamp(6)),instant(r.getTimestamp(7))), appId, sourceId).stream().findFirst();
  }
  private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

  @Transactional
  public long ensure(long appId, Article a) {
    jdbc.update("""
        INSERT INTO game_news(app_id,source_news_id,title,content,content_hash,source_url,feed_name,
                              published_at,collected_at,last_seen_at,updated_at)
        VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        ON DUPLICATE KEY UPDATE id=id
        """,appId,a.id(),a.title(),a.content(),BriefingContent.hash(a),a.sourceUrl(),a.feedName(),Timestamp.from(a.publishedAt()));
    long id = Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM game_news WHERE app_id=? AND source_news_id=?", Long.class, appId,a.id()));
    jdbc.update("INSERT INTO game_news_briefings(news_id,generation_status,retry_count) VALUES (?,'PENDING',0) ON DUPLICATE KEY UPDATE news_id=news_id", id);
    return id;
  }
  public boolean claim(long newsId, String token) {
    return jdbc.update("""
        UPDATE game_news_briefings SET gpt_claim_token=?, gpt_claim_until=TIMESTAMPADD(SECOND,90,CURRENT_TIMESTAMP)
        WHERE news_id=? AND (gpt_claim_until IS NULL OR gpt_claim_until<CURRENT_TIMESTAMP)
        """, token,newsId) == 1;
  }
  public boolean save(long newsId, String token, String payload, String hash, String model) {
    return jdbc.update("""
        UPDATE game_news_briefings SET gpt_payload=?, gpt_source_hash=?, gpt_model_name=?,
            gpt_prompt_version=?, gpt_generated_at=CURRENT_TIMESTAMP, gpt_claim_token=NULL, gpt_claim_until=NULL
        WHERE news_id=? AND gpt_claim_token=? AND gpt_claim_until>=CURRENT_TIMESTAMP
        """,payload,hash,model,BriefingContent.PROMPT_VERSION,newsId,token) == 1;
  }
  public void release(long newsId, String token) {
    jdbc.update("UPDATE game_news_briefings SET gpt_claim_token=NULL,gpt_claim_until=NULL WHERE news_id=? AND gpt_claim_token=?",newsId,token);
  }
}

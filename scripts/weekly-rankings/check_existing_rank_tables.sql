-- READ ONLY. In Workbench first double-click the intended schema.
-- Execute each statement separately and retain each result.
SELECT DATABASE() AS selected_database, VERSION() AS mysql_version;

SHOW CREATE TABLE rank_weekly_charts;
SHOW CREATE TABLE rank_weekly_entries;

SHOW INDEX FROM rank_weekly_charts;
SHOW INDEX FROM rank_weekly_entries;

SELECT COUNT(*) AS existing_charts FROM rank_weekly_charts;
SELECT COUNT(*) AS existing_entries FROM rank_weekly_entries;

SELECT chart_id, country_code, week_start, week_end_exclusive,
       source_type, verification_status, fetched_at
FROM rank_weekly_charts
ORDER BY week_start DESC, country_code
LIMIT 30;

SELECT country_code, week_start, COUNT(*) AS duplicate_count
FROM rank_weekly_charts
GROUP BY country_code, week_start
HAVING COUNT(*) > 1;

SELECT chart_id, game_rank, COUNT(*) AS duplicate_count
FROM rank_weekly_entries
GROUP BY chart_id, game_rank
HAVING COUNT(*) > 1;

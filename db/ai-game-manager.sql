CREATE TABLE IF NOT EXISTS ai_manager_records (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL, payload LONGTEXT NOT NULL,
 created_at DATETIME(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 INDEX idx_manager_user_kind (user_id,kind),
 CONSTRAINT fk_manager_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS library_games (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, app_id BIGINT NOT NULL,
 name VARCHAR(255) NOT NULL, playtime_forever INT NOT NULL, playtime_2weeks INT NOT NULL,
 last_played_at DATETIME(6), icon_url VARCHAR(500),
 UNIQUE KEY uk_library_user_app(user_id,app_id),
 CONSTRAINT fk_library_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

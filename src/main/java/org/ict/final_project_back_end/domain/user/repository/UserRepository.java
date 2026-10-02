package org.ict.final_project_back_end.domain.user.repository;

import org.ict.final_project_back_end.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  boolean existsByNickname(String nickname);

  Optional<User> findBySteamId(String steamId);
}

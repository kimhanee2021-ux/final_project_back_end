package org.ict.final_project_back_end.domain.game.service;

import java.util.Collection;
import java.util.Set;

// 추천/카테고리 목록에서 빼야 할 성인·노출 콘텐츠 게임을 골라낸다. 판정 방식은 구현체가 정한다.
public interface AdultContentFilter {

  // appIds 중 성인·노출 콘텐츠로 판정된 appId만 돌려준다. 판정할 수 없는 게임은 포함하지 않는다.
  Set<Long> findAdultAppIds(Collection<Long> appIds);
}

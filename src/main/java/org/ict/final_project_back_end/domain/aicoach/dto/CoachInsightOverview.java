package org.ict.final_project_back_end.domain.aicoach.dto;

import java.util.List;

// AI 분석 화면이 열릴 때 가장 먼저 받는 값. AI를 부르지 않고 서버 계산만으로 만들어 바로 돌아온다.
// hasPurchaseHistory: 구매내역을 가져왔는지 (false면 화면이 "구매내역을 가져오면 더 많은 분석이 가능해요" 안내를 보여준다)
// profile: 서버가 규칙으로 정한 소비 유형 (수집가형·몰입형·세일 사냥꾼형·균형형), 같은 데이터면 늘 같은 유형이다
// topics: 이 회원이 지금 고를 수 있는 분석 주제(태그). 구매내역이 있어야 하는 주제는 구매내역이 없으면 빠진다
public record CoachInsightOverview(boolean hasPurchaseHistory, Profile profile, List<Topic> topics) {

  // name: 유형 이름, meaning: 그 유형을 설명하는 한 문장 (숫자 없음)
  public record Profile(String name, String meaning) {
  }

  // key: 결과를 요청할 때 보내는 값 (spending·library·value·activity·budget), label: 태그에 쓸 이름
  public record Topic(String key, String label) {
  }
}

package org.ict.final_project_back_end.domain.wishsales.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// [추가] 학습된 로지스틱 회귀 모델을 불러와서 확률을 계산한다.
// 모델 파일: resources/ml/yoy_model_by_threshold.json (기준 20/30/40/50%별 계수)
// 학습은 이미 끝난 상태이고, 여기서는 계수로 식만 계산한다.
@Component
public class DiscountProbabilityModel {

    // 기준(%) 하나에 해당하는 계수 묶음
    private record Params(Map<String, Double> coef, double intercept) {}

    private final Map<Integer, Params> byThreshold = new HashMap<>();

    public DiscountProbabilityModel() throws Exception {
        // 프로젝트 안의 모델 파일을 읽는다 (외부 호출 없음)
        try (InputStream in = new ClassPathResource("ml/yoy_model_by_threshold.json").getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(in);
            JsonNode thresholds = root.path("thresholds");
            for (String key : new String[]{"20", "30", "40", "50"}) {
                JsonNode node = thresholds.path(key);
                if (node.isMissingNode()) continue;
                Map<String, Double> coef = new HashMap<>();
                node.path("coefficients").properties().forEach(e -> coef.put(e.getKey(), e.getValue().asDouble()));
                byThreshold.put(Integer.parseInt(key), new Params(coef, node.path("intercept").asDouble()));
            }
        }
    }

    public boolean supports(int threshold) {
        return byThreshold.containsKey(threshold);
    }

    // lyMax: 작년 같은 달 최대 할인율(%), moy: 대상 달(1~12)
    // 반환값: 올해 같은 달에 threshold% 이상 할인할 확률(0~100, 정수)
    public int percent(int threshold, int lyMax, int moy) {
        Params p = byThreshold.get(threshold);
        if (p == null) throw new IllegalArgumentException("지원하지 않는 기준 할인율: " + threshold);

        double lyRate = lyMax / 100.0;                     // 작년 최대 할인율을 0~1로
        double lySale = lyMax >= threshold ? 1.0 : 0.0;    // 작년에 기준 이상 할인했는지
        double summer = (moy >= 6 && moy <= 8) ? 1.0 : 0.0;
        double winter = (moy == 12 || moy <= 2) ? 1.0 : 0.0;

        double z = p.intercept()
                + p.coef().getOrDefault("ly_rate", 0.0) * lyRate
                + p.coef().getOrDefault("ly_sale", 0.0) * lySale
                + p.coef().getOrDefault("is_summer", 0.0) * summer
                + p.coef().getOrDefault("is_winter", 0.0) * winter;
        double prob = 1.0 / (1.0 + Math.exp(-z));          // 로지스틱 함수
        return (int) Math.round(prob * 100);
    }
}

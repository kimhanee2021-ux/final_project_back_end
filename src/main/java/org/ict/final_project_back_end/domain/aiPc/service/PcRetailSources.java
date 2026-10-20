package org.ict.final_project_back_end.domain.aiPc.service;

import java.util.Map;

/** 직접 모델·용량을 대조한 판매처 주소만 허용한다. AI/사용자 입력 주소는 요청하지 않는다. */
public final class PcRetailSources {
  private PcRetailSources() {}

  private static final Map<Long, String> COMTRIX =
      Map.of(
          96986270L, "https://comtrix.kr/product/마이크로닉스-classic-ii-풀체인지-600w-80plus실버-atx3-1/",
          69869543L, "https://comtrix.kr/product/삼성전자-990-evo-plus-m-2-nvme-1tb/");

  public static String comtrixUrl(long productCode) {
    return COMTRIX.get(productCode);
  }
}

package org.ict.final_project_back_end.domain.aiPc.service;

import java.util.List;
import org.ict.final_project_back_end.domain.aiPc.dto.PcPart;

/** 조사한 독립 비교 후보. 한 대의 조립 구성이나 사용자에게 장착 가능한 부품 목록을 뜻하지 않는다. */
public final class PcPartsCatalog {
  private PcPartsCatalog() {}

  public static final List<PcPart> PARTS =
      List.of(
          new PcPart(
              "SSD",
              "Western Digital WD BLACK SN7100 M.2 NVMe (1TB)",
              74118179,
              "https://documents.westerndigital.com/content/dam/doc-library/ko_kr/assets/public/western-digital/product/internal-drives/wd-black-ssd/data-sheet-wd-black-sn7100-nvme-ssd.pdf",
              List.of(
                  "M.2 2280 NVMe PCIe 4.0 모델: 보드 슬롯·방열 공간 확인",
                  "현재 저장 공간이 충분하면 구매 필요를 단정하지 않아요. SSD 교체로 FPS 향상을 보장하지 않아요")),
          new PcPart(
              "쿨러",
              "Thermalright AXP90-X47 서린 (그레이)",
              15937622,
              "https://www.thermalright.com/product/axp90-x47/",
              List.of(
                  "총 높이 47mm: 케이스 여유 높이·보드 부품 간섭 확인",
                  "판매 패키지의 LGA1700 브래킷 포함 여부·OEM 백플레이트·CPU 발열 대응 확인. 다른 색상/FULL 모델과 혼동 금지")),
          new PcPart(
              "CPU",
              "인텔 코어i7-14세대 14700 (랩터레이크 리프레시) (정품)",
              32038001,
              "https://www.intel.com/content/www/us/en/products/sku/236781/intel-core-i7-processor-14700-33m-cache-up-to-5-40-ghz/specifications.html",
              List.of(
                  "제조사 i7-14700: LGA1700·기본65W/최대터보219W. 현재 CPU 대비 게임 성능 향상은 실측 자료 확인",
                  "메인보드 CPU 지원 목록·BIOS·전원부·쿨러 확인")),
          new PcPart(
              "GPU",
              "ZOTAC GAMING 지포스 RTX 3050 D6 6GB LP",
              36794303,
              "https://www.amd.com/en/resources/support-articles/faqs/GPU-Install.html",
              List.of("일반 GPU 장착 안내 기준이며 이 제품의 호환 인증이 아니에요", "PCIe 슬롯·LP 브래킷·두께·길이·전원 용량 확인")),
          new PcPart(
              "GPU",
              "ZOTAC GAMING 지포스 RTX 5060 LP D7 8GB",
              96987773,
              "https://www.zotac.com/us/product/graphics_card/zotac-gaming-geforce-rtx-5060-low-profile",
              List.of(
                  "제조사 표기: 권장 파워 550W·8핀 전원 1개·길이 182.5mm·두께 38.2mm",
                  "LP 카드도 모든 슬림 PC에 장착되는 것은 아니에요. 전원·슬롯·내부 공간 확인")),
          new PcPart(
              "RAM",
              "마이크론 Crucial DDR4-3200 CL22 대원씨티에스 (16GB)",
              35805935,
              "https://www.intel.com/content/www/us/en/support/articles/000092149/processors.html",
              List.of("CPU 지원만으로 보드 RAM 호환을 확정할 수 없어요", "DDR 세대·DIMM/SODIMM·남은 슬롯·최대 용량 확인")),
          new PcPart(
              "SSD",
              "삼성전자 990 EVO Plus M.2 NVMe (1TB)",
              69869543,
              "https://download.semiconductor.samsung.com/resources/data-sheet/samsung_nvme_ssd_990_evo_plus_datasheet_rev.1.0_10139908982130.pdf",
              List.of("보드의 NVMe M.2 지원·슬롯 길이·방열 공간 확인", "게임 설치 여유 공간이 충분하면 교체 필요를 단정하지 않아요")),
          new PcPart(
              "메인보드",
              "MSI PRO B760M-A WIFI",
              19241759,
              "https://www.msi.com/Motherboard/PRO-B760M-A-WIFI/Specification",
              List.of("DDR5 모델이며 DDR4 모델과 달라요", "CPU 지원·BIOS·RAM 세대·케이스 규격·OEM 전면 패널 확인")),
          new PcPart(
              "파워",
              "마이크로닉스 Classic II 풀체인지 600W 80PLUS실버 ATX3.1",
              96986270,
              "https://knowledge.seasonic.com/article/68-cable-compatibility",
              List.of(
                  "케이블 안전 일반 안내이며 이 제품 호환 인증이 아니에요",
                  "전체 전력·ATX/SFX·길이·OEM 전원 커넥터 확인; 다른 파워 케이블 재사용 금지")),
          new PcPart(
              "케이스",
              "darkFlash DLX21 RGB MESH 강화유리 (블랙)",
              11884168,
              "https://www.darkflash.com/ko-KR/product/dlx21mesh",
              List.of(
                  "DLX21 Mesh 제품군 공식 안내: 판매 패키지·내부 장착 공간 확인",
                  "보드 규격·GPU 길이·쿨러 높이·파워 길이·OEM 부품 확인")),
          new PcPart(
              "쿨러",
              "DEEPCOOL AG400 ARGB",
              17808380,
              "https://www.deepcool.com/products/Cooling/cpuaircoolers/AG400-ARGB-120mm-CPU-Cooler-1851-1700-AM5/2022/16084.shtml",
              List.of("제조사 높이150mm: 케이스 공간·정확한 패키지 브래킷 확인", "CPU 소켓 브래킷·케이스 높이·RAM 간섭·보드 부품 확인")));
}

package org.ict.final_project_back_end.domain.aiPc.service;
import java.util.*;
import org.ict.final_project_back_end.domain.aiPc.dto.*;
/** 모르는 기기 규격을 호환 확정으로 바꾸지 않는 서버 정책. */
public final class PcHardwarePolicy {
 private PcHardwarePolicy(){}
 public static boolean ambiguousGpu(String model){
  String s=Objects.toString(model,"").toLowerCase(Locale.ROOT);
  int families=0;for(String family:List.of("(?:rtx|gtx)\\s*\\d+","(?:uhd|iris)(?:\\b|[0-9])","radeon\\s+(?:rx|graphics|\\d{3,4}m)","(?:intel\\s+)?arc\\s+[ab]\\d+")){var matcher=java.util.regex.Pattern.compile(family).matcher(s);while(matcher.find())families++;}
  return families>1||s.matches(".*(?:rtx|gtx)\\s*\\d+.*(?:rtx|gtx)\\s*\\d+.*");
 }
 public static String gpuIdentity(PcSpecs pc){
  String gpu=Objects.toString(pc.gpuModel(),"");
  if(gpu.isBlank()||gpu.matches("(?is).*(unknown|미확인|정보 없음|generic).*")||!gpu.matches("(?s).*\\d.*"))return "UNIDENTIFIED";
  return ambiguousGpu(gpu)?"MULTIPLE":"IDENTIFIED_SINGLE";
 }
 public static boolean laptop(PcSpecs pc){return "LAPTOP".equals(pc.deviceType())||Objects.toString(pc.gpuModel(),"").toLowerCase(Locale.ROOT).matches(".*(?:laptop|mobile|max-q).*" );}
 public static String blockedProductReason(PcSpecs pc,String category){
  if("GPU".equals(category)&&(pc.gpuModel()==null||pc.gpuModel().isBlank()||ambiguousGpu(pc.gpuModel())))return "분석 기준 GPU가 확인되지 않아 교체 제품을 선택하지 않아요.";
  if(Set.of("CPU","GPU").contains(category)&&(laptop(pc)||!"DESKTOP".equals(pc.deviceType())))return laptop(pc)?"노트북 CPU·GPU는 일반 데스크톱 부품 교체 대상으로 추천하지 않아요. 제조사 서비스 안내와 전원·발열 설정을 확인하세요.":"데스크톱 여부와 장착 규격이 확인되지 않아 교체 제품 선택을 보류했어요.";
  if("RAM".equals(category))return "DDR 세대·DIMM/SODIMM·슬롯·온보드 여부를 확인한 뒤 호환 제품을 선택해야 해요.";
  if("SSD".equals(category))return "총 저장공간만으로 SSD 종류를 알 수 없어요. NVMe/SATA·슬롯 길이·추가 슬롯을 확인해야 해요.";
  return "";
 }
 public static List<PcComponentAdvice> advice(PcSpecs pc,List<PcComponentAdvice> input){
  return input.stream().map(a->{String status=a.status(),reason=a.reason(),note=a.compatibilityNote();var offer=a.offer();
   if("UPGRADE".equals(status)&&reason.matches("(?s).*(필수|보장|확실).*")){status="CHECK";offer=null;reason="현재 근거로 교체가 필수라고 확정하지 않아요. 게임별 설정과 실제 부품 사용량을 확인한 뒤 교체 필요성을 판단해야 해요.";}
   String block=blockedProductReason(pc,a.category());
   if(!block.isBlank()){if(!("GPU".equals(a.category())&&!laptop(pc)&&"IDENTIFIED_SINGLE".equals(gpuIdentity(pc))))offer=null;note=block;}
   if("SSD".equals(a.category())&&pc.storageFreeGB()!=null&&pc.storageFreeGB()>=256){status="KEEP";offer=null;reason="확인된 남은 공간은 충분해요. 저장장치 교체가 게임 FPS 개선으로 이어진다고 판단할 근거는 없어요.";}
   if("RAM".equals(a.category())&&pc.ramGB()!=null&&pc.ramGB()>=32&&"UPGRADE".equals(a.status())){status="CHECK";offer=null;reason="RAM 용량만으로 추가 증설이 필요하다고 판단하지 않아요. 실제 메모리 사용량과 지원 규격을 확인하세요.";}
   return new PcComponentAdvice(a.category(),status,reason,note,offer);
  }).toList();
 }
 public static List<PcDiagnosis.UpgradeRecommendation> recommendations(PcSpecs pc,List<PcDiagnosis.UpgradeRecommendation> input){return input.stream().filter(r->blockedProductReason(pc,r.offer().part().category()).isBlank()).toList();}
}

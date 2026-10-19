package org.ict.final_project_back_end.domain.aiPc.service;
import java.util.*;
import java.util.regex.*;
import org.ict.final_project_back_end.domain.aiPc.dto.PcSpecs;

public final class PcRules {
 private PcRules(){}
 public record Comparison(String grade,List<String> checks){}
 public static Comparison compare(PcSpecs pc,String minimum){
  var checks=new ArrayList<String>();
  boolean cpu=matches(pc.cpuModel(),minimum),gpu=matches(pc.gpuModel(),minimum),os=matches(pc.os(),minimum);
  checks.add(cpu?"CPU: 공식 최소 모델 직접 일치":"CPU: 직접 일치하는 비교 근거 없음; 성능 서열 판단 보류");
  checks.add(gpu?"GPU: 공식 최소 모델 직접 일치":"GPU: 직접 일치하는 비교 근거 없음; 내장/외장 및 VRAM 확인 필요");
  checks.add(os?"OS: 요구 사양 표기와 직접 일치":"OS: 운영체제 및 API 지원 확인 필요");
  Double ram=amount(minimum,"Memory"),space=amount(minimum,"Storage");
  boolean ramOk=ram!=null&&pc.ramGB()!=null&&pc.ramGB()>=ram;
  boolean diskOk=space!=null&&pc.storageFreeGB()!=null&&pc.storageFreeGB()>=space;
  checks.add(ram==null||pc.ramGB()==null?"RAM: 정보 부족":ramOk?"RAM 용량 충족 (최소 "+ram+" GB)":"RAM 부족 (최소 "+ram+" GB)");
  checks.add(space==null||pc.storageFreeGB()==null?"저장공간: 정보 부족":diskOk?"여유 공간 충족 (최소 "+space+" GB)":"여유 공간 부족 (최소 "+space+" GB)");
  // 모델 직접 일치만으로도 DirectX/드라이버 등의 조건까지 보장할 수 없으므로 경고를 동반한다.
  boolean extra=Pattern.compile("(?i)DirectX|Vulkan|Shader|VRAM|video memory|Additional Notes|Additional Requirements").matcher(minimum==null?"":minimum).find();
  if(extra)checks.add("추가 요구 사항: 그래픽 API·전용 메모리·기타 조건은 사진만으로 확인되지 않아 판정 보류");
  boolean pass=cpu&&gpu&&os&&ramOk&&diskOk&&!extra;
  checks.add("게임 실행/실측 FPS 보장 아님. 드라이버·DirectX·추가 요구 사항은 원문 확인 필요.");
  return new Comparison(pass?"O":"X",List.copyOf(checks));
 }
 private static boolean matches(String model,String source){
  String name=normalize(model);if(name.length()<4||!name.matches(".*[0-9].*"))return false;
  return Pattern.compile("(?<![a-z0-9])"+Pattern.quote(name)+"(?![a-z0-9])").matcher(normalize(source)).find();
 }
 private static String normalize(String value){return value==null?"":value.toLowerCase(Locale.ROOT).replace("(r)","").replace("(tm)","").replaceAll("[^a-z0-9]+"," ").trim();}
 private static Double amount(String text,String label){var m=Pattern.compile("(?i)"+label+"\\s*:?\\s*(\\d+(?:\\.\\d+)?)\\s*(GB|MB)").matcher(text==null?"":text);return m.find()?Double.parseDouble(m.group(1))/(m.group(2).equalsIgnoreCase("MB")?1024:1):null;}
}

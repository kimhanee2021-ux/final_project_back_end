package org.ict.final_project_back_end.domain.aiPc;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.ict.final_project_back_end.domain.aiPc.service.*;
import org.ict.final_project_back_end.domain.aiPc.dto.*;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.global.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class PcApiTest {
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired JwtService jwt;
 @MockitoBean GeminiPcClient gemini;@MockitoBean PcRequirementsClient requirements;@MockitoBean PcPartsService parts;
 String token;
 @BeforeEach void setup(){var user=users.save(User.builder().email(java.util.UUID.randomUUID()+"@example.invalid").nickname(java.util.UUID.randomUUID().toString()).passwordHash("test").build());token="Bearer "+jwt.generateToken(user.getId(),user.getEmail());}
 @Test void unauthenticatedPhotoAndAssessmentAreRejected()throws Exception{
  mvc.perform(multipart("/api/ai/pc/extract").file(photo("image/png"))).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/ai/pc/assess").contentType("application/json").content(body(1))).andExpect(status().isUnauthorized());
  verifyNoInteractions(gemini);
 }
 @Test void wrongMimeAndOversizeDoNotReachAi()throws Exception{
  mvc.perform(multipart("/api/ai/pc/extract").file(photo("image/jpeg")).header("Authorization",token)).andExpect(status().isBadRequest());
  mvc.perform(multipart("/api/ai/pc/extract").file(new MockMultipartFile("image","large.png","image/png",new byte[10485761])).header("Authorization",token)).andExpect(status().isPayloadTooLarge());
  verifyNoInteractions(gemini);
 }
 @Test void extractionReturnsStructuredFields()throws Exception{
  when(gemini.extract(any(),eq("image/png"))).thenReturn(new PcDiagnosis.Extraction(new PcSpecs("CPU 123","","GPU 456","",32.0,"",null,null,"Windows 10"),java.util.List.of("일부 정보 없음")));
  mvc.perform(multipart("/api/ai/pc/extract").file(photo("image/png")).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.specs.ramGB").value(32)).andExpect(jsonPath("$.specs.storageFreeGB").doesNotExist());
 }
 @Test void photoProviderMustMatchDisplayedConsentBeforeSending()throws Exception{
  when(gemini.provider()).thenReturn("Groq");
  mvc.perform(multipart("/api/ai/pc/extract").file(photo("image/png")).param("provider","Gemini").header("Authorization",token)).andExpect(status().isConflict());
  verify(gemini,never()).extract(any(),anyString());
  mvc.perform(get("/api/ai/pc/provider").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("Groq"));
  mvc.perform(get("/api/ai/pc/provider")).andExpect(status().isUnauthorized());
 }
 @Test void impossibleCapacityAndMissingSpecsAreRejected()throws Exception{
  mvc.perform(post("/api/ai/pc/assess").header("Authorization",token).contentType("application/json").content("{\"appId\":1,\"specs\":{\"ramGB\":16,\"storageTotalGB\":10,\"storageFreeGB\":11}}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/ai/pc/assess").header("Authorization",token).contentType("application/json").content("{\"appId\":1,\"specs\":{}}")).andExpect(status().isBadRequest());verifyNoInteractions(gemini,requirements);
 }
 @Test void fiveOwnedGamesUseSameRealAssessmentPathWithoutInventingFps()throws Exception{
  var answer=new ObjectMapper().readTree("{\"grade\":\"X\",\"summary\":\"비교 근거 부족\",\"fhd\":\"실측 자료 없음\",\"qhd\":\"실측 자료 없음\",\"uhd\":\"실측 자료 없음\",\"settings\":[\"설정 확인\"],\"upgradeChecks\":[\"호환 확인\"],\"limitation\":\"평가 불가\",\"recommendations\":[]}");
  when(requirements.get(anyLong())).thenAnswer(i->new PcGameRequirements(i.getArgument(0),"게임 "+i.getArgument(0),"Memory: 8 GB Storage: 5 GB", "",true,"https://store.steampowered.com/app/"+i.getArgument(0)+"/","2026-10-19T00:00:00Z"));
  when(gemini.explain(any(),any(),any(),anyList())).thenReturn(answer);when(gemini.explanation(any(),anyString(),anyInt())).thenAnswer(i->answer.path((String)i.getArgument(1)).asText());when(gemini.list(any(),anyString())).thenReturn(java.util.List.of("확인 필요"));
  for(int id=1;id<=5;id++)mvc.perform(post("/api/ai/pc/assess").header("Authorization",token).contentType("application/json").content(body(id))).andExpect(status().isOk()).andExpect(jsonPath("$.appId").value(id)).andExpect(jsonPath("$.grade").value("X")).andExpect(jsonPath("$.resolutions[0].explanation").value("실측 자료 없음"));
  verify(gemini,times(5)).explain(any(),any(),any(),anyList());
 }
 @Test void externalQuotaKeepsComparisonButMarksRateLimited()throws Exception{
  when(requirements.get(1)).thenReturn(new PcGameRequirements(1,"공식 게임","Memory: 8 GB Storage: 5 GB","",true,"https://store.steampowered.com/app/1/","2026-10-19T00:00:00Z"));
  when(gemini.explain(any(),any(),any(),anyList())).thenThrow(new org.ict.final_project_back_end.domain.aiCommon.exception.AiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"private provider detail"));
  mvc.perform(post("/api/ai/pc/assess").header("Authorization",token).contentType("application/json").content(body(1))).andExpect(status().isOk()).andExpect(jsonPath("$.explanationStatus").value("RATE_LIMITED")).andExpect(jsonPath("$.checks").isNotEmpty()).andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("이용 한도"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private provider detail"))));
 }
 @Test void aiRecommendationIsJoinedToObservedOfferAndSource()throws Exception{
  var offer=new PcPart.Offer(new PcPart("GPU","조사한 후보",123,"https://example.invalid/manufacturer",java.util.List.of("전원 확인")),200000L,"OBSERVED","수집 가격","2026-10-19T00:00:00Z",null,null,null);
  when(parts.offers(anyLong())).thenReturn(java.util.List.of(offer));
  when(requirements.get(1)).thenReturn(new PcGameRequirements(1,"공식 게임","Memory: 8 GB Storage: 5 GB","",true,"https://store.steampowered.com/app/1/","2026-10-19T00:00:00Z"));
  var answer=new ObjectMapper().readTree("{\"grade\":\"X\",\"summary\":\"추가 확인 후 그래픽 후보 검토\",\"fhd\":\"실측 자료 없음\",\"qhd\":\"실측 자료 없음\",\"uhd\":\"실측 자료 없음\",\"limitation\":\"호환 확인 필요\",\"recommendations\":[{\"productCode\":\"123\",\"reason\":\"게임 그래픽 요구 조건과 비교할 후보\",\"compatibilityNote\":\"전원과 장착 공간 확인 필요\"}]}");
  when(gemini.explain(any(),any(),any(),anyList())).thenReturn(answer);when(gemini.explanation(any(),anyString(),anyInt())).thenAnswer(i->answer.path((String)i.getArgument(1)).asText());
  mvc.perform(post("/api/ai/pc/assess").header("Authorization",token).contentType("application/json").content(body(1))).andExpect(status().isOk()).andExpect(jsonPath("$.explanationStatus").value("READY")).andExpect(jsonPath("$.recommendations[0].offer.priceKrw").value(200000)).andExpect(jsonPath("$.recommendations[0].offer.part.productCode").value(123)).andExpect(jsonPath("$.recommendations[0].reason").value("게임 그래픽 요구 조건과 비교할 후보"));
  verify(gemini).explain(any(),any(),any(),eq(java.util.List.of(offer)));
 }
 @Test void officialOnlyComparisonNeverCallsGemini()throws Exception{
  when(requirements.get(1)).thenReturn(new PcGameRequirements(1,"공식 게임","Memory: 8 GB Storage: 5 GB","",true,"https://store.steampowered.com/app/1/","2026-10-19T00:00:00Z"));
  String input=body(1).replaceFirst("\\{","{\"includeAi\":false,");
  for(int n=0;n<7;n++)mvc.perform(post("/api/ai/pc/assess").header("Authorization",token).contentType("application/json").content(input)).andExpect(status().isOk()).andExpect(jsonPath("$.explanationStatus").value("NOT_REQUESTED")).andExpect(jsonPath("$.checks").isNotEmpty());
  verifyNoInteractions(gemini);
 }
 private String body(int id){return "{\"appId\":"+id+",\"specs\":{\"cpuModel\":\"CPU 123\",\"ramGB\":32,\"storageFreeGB\":100}}";}
 @Test void delayedAiKeepsOfficialComparisonAndClearlyMarksMissingExplanation()throws Exception{
  when(requirements.get(1)).thenReturn(new PcGameRequirements(1,"공식 게임","Memory: 8 GB Storage: 5 GB","",true,"https://store.steampowered.com/app/1/?cc=kr","2026-10-19T00:00:00Z"));
  when(gemini.explain(any(),any(),any(),anyList())).thenThrow(new org.ict.final_project_back_end.domain.aiCommon.exception.AiException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,"upstream detail must stay private"));
  mvc.perform(post("/api/ai/pc/assess").header("Authorization",token).contentType("application/json").content(body(1))).andExpect(status().isOk()).andExpect(jsonPath("$.gameName").value("공식 게임")).andExpect(jsonPath("$.grade").value("X")).andExpect(jsonPath("$.explanationStatus").value("UNAVAILABLE")).andExpect(jsonPath("$.requirements.source").value("https://store.steampowered.com/app/1/?cc=kr")).andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("AI 해설"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("upstream detail"))));
 }
 @Test void missingOfficialSourceStillFailsInsteadOfInventingComparison()throws Exception{
  when(requirements.get(1)).thenThrow(new org.ict.final_project_back_end.domain.aiCommon.exception.AiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"공식 자료 조회 실패"));
  mvc.perform(post("/api/ai/pc/assess").header("Authorization",token).contentType("application/json").content(body(1))).andExpect(status().isServiceUnavailable());verifyNoInteractions(gemini);
 }
 private MockMultipartFile photo(String mime)throws Exception{var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,1),"png",bytes);return new MockMultipartFile("image","pc.png",mime,bytes.toByteArray());}
}

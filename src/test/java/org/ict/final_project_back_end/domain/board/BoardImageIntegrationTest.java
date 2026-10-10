package org.ict.final_project_back_end.domain.board;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.Map;
import javax.imageio.ImageIO;
import jakarta.persistence.EntityManager;
import org.ict.final_project_back_end.domain.board.entity.BoardPost;
import org.ict.final_project_back_end.domain.board.repository.BoardPostRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
class BoardImageIntegrationTest {
  private static final Path ROOT;
  static { try { ROOT=Files.createTempDirectory("board-images-test-"); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
  @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
    registry.add("board.images.directory", ROOT::toString);
  }
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired BoardPostRepository posts;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired EntityManager em;
  @Autowired PlatformTransactionManager manager;
  private User author;
  private User guest;
  private BoardPost post;
  private String owner;
  private String other;
  private String url;
  @BeforeEach void setup() {
    author=users.save(User.builder().email("img-a@example.invalid").nickname("이미지작성자").passwordHash("test").build());
    guest=users.save(User.builder().email("img-b@example.invalid").nickname("다른사람").passwordHash("test").build());
    owner="Bearer "+jwt.generateToken(author.getId(),author.getEmail());
    other="Bearer "+jwt.generateToken(guest.getId(),guest.getEmail());
    post=posts.saveAndFlush(BoardPost.builder().category("A").title("이미지").content("본문").author(author).build());
    url="/api/board/"+post.getId()+"/images";
  }
  @AfterEach void cleanup() throws Exception {
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      em.createQuery("delete from BoardPostImage i where i.post.id = :id").setParameter("id",post.getId()).executeUpdate();
      posts.deleteById(post.getId()); users.deleteById(author.getId()); users.deleteById(guest.getId());
    });
    try(var files=Files.list(ROOT)) { for(Path p:files.toList()) Files.deleteIfExists(p); }
  }
  private byte[] image(String format) throws Exception {
    var out=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,out); return out.toByteArray();
  }
  private Map upload() throws Exception {
    var result=mvc.perform(multipart(url).file(new MockMultipartFile("file","photo.png","image/png",image("png")))
        .header("Authorization",owner)).andExpect(status().isCreated()).andReturn();
    return mapper.readValue(result.getResponse().getContentAsString(),Map.class);
  }
  @Test void uploadStoresFileAndUpdatesCoverAndListsMetadata() throws Exception {
    var response=upload();
    assertEquals(response.get("url"),posts.findById(post.getId()).orElseThrow().getImageUrl());
    mvc.perform(get(url).header("Authorization",other)).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(response.get("id")));
    mvc.perform(get((String)response.get("url")).header("Authorization",other))
        .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(image("png")));
    try(var files=Files.list(ROOT)) { assertEquals(1,files.count()); }
  }
  @Test void jpegIsAcceptedAndOriginalFilenameIsNotUsedAsPath() throws Exception {
    var result=mvc.perform(multipart(url).file(new MockMultipartFile("file","../../photo.jpg","image/jpeg",image("jpg")))
        .header("Authorization",owner)).andExpect(status().isCreated()).andReturn();
    var data=mapper.readValue(result.getResponse().getContentAsString(),Map.class);
    mvc.perform(get((String)data.get("url")).header("Authorization",owner)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
    try(var files=Files.list(ROOT)) { assertTrue(files.allMatch(p -> p.getFileName().toString().matches("[a-f0-9-]+\\.jpg"))); }
  }
  @Test void rejectsUnauthorizedAndOtherAuthorsUploadOrDelete() throws Exception {
    var file=new MockMultipartFile("file","p.png","image/png",image("png"));
    mvc.perform(multipart(url).file(file)).andExpect(status().isUnauthorized());
    mvc.perform(multipart(url).file(file).header("Authorization",other)).andExpect(status().isForbidden());
    var data=upload();
    mvc.perform(delete(url+"/"+data.get("id")).header("Authorization",other)).andExpect(status().isForbidden());
    mvc.perform(get((String)data.get("url"))).andExpect(status().isUnauthorized());
  }
  @Test void rejectsFakeEmptyOversizedAndUnsupportedImages() throws Exception {
    for(var f:new MockMultipartFile[]{
      new MockMultipartFile("file","fake.png","image/png","not an image".getBytes()),
      new MockMultipartFile("file","empty.png","image/png",new byte[0]),
      new MockMultipartFile("file","large.png","image/png",new byte[20*1024*1024+1]),
      new MockMultipartFile("file","x.svg","image/svg+xml","<svg/>".getBytes())}) {
      mvc.perform(multipart(url).file(f).header("Authorization",owner)).andExpect(status().isBadRequest());
    }
    try(var files=Files.list(ROOT)) { assertEquals(0,files.count()); }
  }
  @Test void limitsImagesToTen() throws Exception {
    for(int i=0;i<10;i++) upload();
    mvc.perform(multipart(url).file(new MockMultipartFile("file","p.png","image/png",image("png")))
        .header("Authorization",owner)).andExpect(status().isConflict());
  }
  @Test void deletionRemovesFileAndPromotesNextCover() throws Exception {
    var first=upload(); var second=upload();
    mvc.perform(delete(url+"/"+first.get("id")).header("Authorization",owner)).andExpect(status().isNoContent());
    assertEquals(second.get("url"),posts.findById(post.getId()).orElseThrow().getImageUrl());
    mvc.perform(get((String)first.get("url")).header("Authorization",owner)).andExpect(status().isNotFound());
    mvc.perform(delete(url+"/"+second.get("id")).header("Authorization",owner)).andExpect(status().isNoContent());
    assertNull(posts.findById(post.getId()).orElseThrow().getImageUrl());
    try(var files=Files.list(ROOT)) { assertEquals(0,files.count()); }
  }
  @Test void rollbackDoesNotLeaveUploadedFiles() throws Exception {
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      try { upload(); } catch(Exception e) { throw new RuntimeException(e); }
      status.setRollbackOnly();
    });
    try(var files=Files.list(ROOT)) { assertEquals(0,files.count()); }
    assertNull(posts.findById(post.getId()).orElseThrow().getImageUrl());
  }
  @Test void missingPostOrWrongPostImageReturns404() throws Exception {
    mvc.perform(get("/api/board/9223372036854775807/images").header("Authorization",owner)).andExpect(status().isNotFound());
    var data=upload();
    mvc.perform(get("/api/board/9223372036854775807/images/"+data.get("id")+"/file").header("Authorization",owner)).andExpect(status().isNotFound());
  }
  @Test void authorSelectsCoverAndDeletingOtherImagePreservesSelection() throws Exception {
    var first=upload(); var second=upload(); var third=upload();
    mvc.perform(put(url+"/"+third.get("id")+"/cover").header("Authorization",other)).andExpect(status().isForbidden());
    mvc.perform(put(url+"/"+third.get("id")+"/cover").header("Authorization",owner))
        .andExpect(status().isOk()).andExpect(jsonPath("$.url").value(third.get("url")));
    mvc.perform(delete(url+"/"+first.get("id")).header("Authorization",owner)).andExpect(status().isNoContent());
    assertEquals(third.get("url"),posts.findById(post.getId()).orElseThrow().getImageUrl());
    mvc.perform(delete(url+"/"+third.get("id")).header("Authorization",owner)).andExpect(status().isNoContent());
    assertEquals(second.get("url"),posts.findById(post.getId()).orElseThrow().getImageUrl());
  }
  @Test void accepts4kOriginalAndRejectsExcessivePixelCount() throws Exception {
    var out=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(3840,2160,BufferedImage.TYPE_INT_RGB),"png",out);
    var original=out.toByteArray();
    var result=mvc.perform(multipart(url).file(new MockMultipartFile("file","4k.png","image/png",original))
        .header("Authorization",owner)).andExpect(status().isCreated()).andReturn();
    var data=mapper.readValue(result.getResponse().getContentAsString(),Map.class);
    mvc.perform(get((String)data.get("url")).header("Authorization",owner)).andExpect(status().isOk()).andExpect(content().bytes(original));
    var oversized=new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(6401,6400,BufferedImage.TYPE_BYTE_GRAY),"png",oversized);
    mvc.perform(multipart(url).file(new MockMultipartFile("file","too-many-pixels.png","image/png",oversized.toByteArray()))
        .header("Authorization",owner)).andExpect(status().isBadRequest());
  }
}

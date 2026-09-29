package org.ict.final_project_back_end;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FinalProjectBackEndApplication {

  public static void main(String[] args) {
    Dotenv dotenv = Dotenv.configure()
        .ignoreIfMissing() //환경변수 파일이 없을 시 시스템 환경변수를 읽어오는 메소드 입니다.
        .load();
    // 읽어온 환경변수를 System Property로 등록
    dotenv.entries().forEach(entry -> System.setProperty(
        entry.getKey(),
        entry.getValue()));

    SpringApplication.run(FinalProjectBackEndApplication.class, args);
  }

}

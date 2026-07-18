package spring.nexusfuture.word_diary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("spring.nexusfuture.word_diary.mapper")
public class WordDiaryApplication {

	public static void main(String[] args) {
		SpringApplication.run(WordDiaryApplication.class, args);
	}

}

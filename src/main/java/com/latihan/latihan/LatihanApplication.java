package com.latihan.latihan;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LatihanApplication {

	public static void main(String[] args) {
		Dotenv dotenv =Dotenv.configure().directory("src/main/resources").ignoreIfMissing().load();
		System.setProperty("GOOGLE_API_KEY", System.getenv("GOOGLE_API_KEY") != null ? System.getenv("GOOGLE_API_KEY") : dotenv.get("GOOGLE_API_KEY"));
		System.setProperty("DB_PASSWORD", System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : dotenv.get("DB_PASSWORD"));
		System.setProperty("GOOGLE_AI_ENDPOINT", System.getenv("GOOGLE_AI_ENDPOINT") != null ? System.getenv("GOOGLE_AI_ENDPOINT") : dotenv.get("GOOGLE_AI_ENDPOINT"));

		SpringApplication.run(LatihanApplication.class, args);
	}

}

package jymusic.jym_catalog_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JymCatalogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(JymCatalogServiceApplication.class, args);
	}

}

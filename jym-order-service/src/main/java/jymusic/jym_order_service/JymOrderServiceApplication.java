package jymusic.jym_order_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JymOrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(JymOrderServiceApplication.class, args);
	}

}

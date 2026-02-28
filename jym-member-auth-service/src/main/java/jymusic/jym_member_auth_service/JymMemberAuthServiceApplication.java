package jymusic.jym_member_auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class JymMemberAuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(JymMemberAuthServiceApplication.class, args);
	}

}

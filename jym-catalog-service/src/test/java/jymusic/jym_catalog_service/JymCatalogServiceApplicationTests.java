package jymusic.jym_catalog_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
		properties = {
				"spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
				"spring.datasource.driver-class-name=org.h2.Driver",
				"spring.datasource.username=sa",
				"spring.datasource.password=",
				"spring.jpa.hibernate.ddl-auto=create-drop",
				"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
				"spring.cloud.aws.s3.base-url=https://example.com",
				"spring.cloud.aws.s3.bucket=test-bucket",
				"spring.cloud.aws.region.static=ap-northeast-2",
				"spring.cloud.aws.credentials.accessKey=test",
				"spring.cloud.aws.credentials.secretKey=test"
		}
)
class JymCatalogServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

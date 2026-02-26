plugins {
	java
	id("org.springframework.boot") version "3.5.11"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "org.cardanofoundation"
version = "0.0.1-SNAPSHOT"
description = "Demo Implementation for a reeve ipfs publisher"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://jitpack.io") }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.kafka:spring-kafka")
	implementation("org.springframework.boot:spring-boot-starter-web")
	// IPFS client library
	implementation("com.github.ipfs:java-ipfs-http-client:v1.3.3")
	// Yaci store
	implementation("com.bloxbean.cardano:cardano-client-lib:0.7.0")
	implementation("com.bloxbean.cardano:cardano-client-backend-blockfrost:0.7.0")
	implementation("com.bloxbean.cardano:cardano-client-crypto:0.7.0")


	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.15")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	compileOnly("org.projectlombok:lombok:1.18.32")
	annotationProcessor("org.projectlombok:lombok:1.18.32")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

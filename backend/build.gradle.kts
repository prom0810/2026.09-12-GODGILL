plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    // JPA Entity를 추가할 다음 단계에서 바로 쓸 수 있도록 미리 포함
    // (Kotlin 클래스는 기본이 final이라 Hibernate 프록시 생성이 안 되는데, 이 플러그인이 @Entity 클래스를 자동으로 open 처리해줌)
    kotlin("plugin.jpa") version "1.9.25"
}

group = "com.safewalk"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // 비밀번호 해시(BCrypt)용. 아직 Spring Security 필터 체인은 쓰지 않아 starter 대신 최소 의존성만 추가.
    implementation("org.springframework.security:spring-security-crypto")

    // 로그인/회원가입 성공 시 발급하는 JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ── .env 로컬 환경변수 로딩 ──────────────────────────────────────────
// backend/.env 파일(팀원마다 각자 로컬에만 두는 파일, .gitignore에서 제외됨)을 읽어서
// ./gradlew bootRun 실행 시 DB_URL/DB_USERNAME/DB_PASSWORD 등을 환경변수로 주입한다.
// .env가 없으면 그냥 무시하고 기존처럼 OS 환경변수/IntelliJ Run Configuration 값을 쓴다.
val dotenv: Map<String, String> = run {
    val envFile = file(".env")
    if (!envFile.exists()) {
        emptyMap()
    } else {
        envFile.readLines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx < 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dotenv.forEach { (key, value) -> environment(key, value) }
}

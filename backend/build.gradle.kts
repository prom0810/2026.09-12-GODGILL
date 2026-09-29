plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
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

    // 비밀번호 해시(BCrypt)용. 아직 Spring Security 필터 체인은 쓰지 않아 starter 대신 최소 의존성만 추가.
    implementation("org.springframework.security:spring-security-crypto")

    // 로그인/회원가입 성공 시 발급하는 JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // 생성자 파라미터 이름을 바이트코드에 남겨 Spring/Jackson이 이름 기반 바인딩을 할 수 있게 한다.
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ── .env 로컬 환경변수 로딩 ──────────────────────────────────────────
// backend/.env 파일(팀원마다 각자 로컬에만 두는 파일, .gitignore에서 제외됨)을 읽어서
// ./gradlew bootRun 실행 시 DB_URL/DB_USERNAME/DB_PASSWORD 등을 환경변수로 주입한다.
// .env가 없으면 그냥 무시하고 기존처럼 OS 환경변수/IntelliJ Run Configuration 값을 쓴다.
// (이 파일은 Gradle 빌드 스크립트일 뿐이며, 백엔드 애플리케이션 코드는 모두 Java다.)
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

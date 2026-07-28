import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    checkstyle
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.2.1"
}

group = "io.hookscope"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val integrationTest by sourceSets.creating {
    java.srcDir("src/integrationTest/java")
    compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += output + compileClasspath
}

configurations[integrationTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    add(integrationTest.implementationConfigurationName, "org.springframework.boot:spring-boot-testcontainers")
    add(integrationTest.implementationConfigurationName, "org.testcontainers:junit-jupiter")
    add(integrationTest.implementationConfigurationName, "org.testcontainers:postgresql")
}

checkstyle {
    toolVersion = "10.21.4"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

spotless {
    java {
        googleJavaFormat("1.25.0")
        target("src/*/java/**/*.java")
    }
    kotlinGradle {
        ktlint("1.5.0")
        target("*.gradle.kts")
    }
    format("repositoryText") {
        target("**/*.md", ".gitignore", ".gitattributes", ".env.example", "**/*.yaml", "**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    reports {
        html.required = true
        junitXml.required = true
    }
    testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    }
}

val integrationTestTask =
    tasks.register<Test>("integrationTest") {
        var rootSuiteTestCount: Long? = null

        description = "Runs integration tests against disposable infrastructure."
        group = "verification"
        testClassesDirs = integrationTest.output.classesDirs
        classpath = integrationTest.runtimeClasspath
        shouldRunAfter(tasks.test)
        testLogging {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        }
        // Gradle 8.14.5 predates failOnNoDiscoveredTests; require executed integration tests explicitly.
        addTestListener(
            object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) = Unit

                override fun afterSuite(
                    suite: TestDescriptor,
                    result: TestResult,
                ) {
                    if (suite.parent == null) {
                        rootSuiteTestCount = result.testCount
                    }
                }

                override fun beforeTest(testDescriptor: TestDescriptor) = Unit

                override fun afterTest(
                    testDescriptor: TestDescriptor,
                    result: TestResult,
                ) = Unit
            },
        )
        doLast {
            val executedTestCount = rootSuiteTestCount
            if (executedTestCount == null || executedTestCount == 0L) {
                throw GradleException("integrationTest executed zero tests; at least one integration test is required.")
            }
        }
    }

tasks.named<BootJar>("bootJar") {
    archiveFileName = "hookscope.jar"
}

tasks.named("check") {
    dependsOn(
        "spotlessCheck",
        "checkstyleMain",
        "checkstyleTest",
        "checkstyleIntegrationTest",
        tasks.test,
        integrationTestTask,
    )
}

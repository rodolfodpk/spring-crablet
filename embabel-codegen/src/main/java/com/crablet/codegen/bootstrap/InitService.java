package com.crablet.codegen.bootstrap;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class InitService {

    public void createProject(String artifactId, String basePackage, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            String javaDir = basePackage.replace('.', '/');
            Path srcMain = targetDir.resolve("src/main/java/" + javaDir);
            Path srcResources = targetDir.resolve("src/main/resources");
            Files.createDirectories(srcMain);
            Files.createDirectories(srcResources);

            writePom(targetDir, artifactId, basePackage);
            writeMainClass(srcMain, basePackage, artifactId);
            writeApplicationYml(srcResources);

            System.out.println("  created " + targetDir + "/pom.xml");
            System.out.println("  created src/main/java/" + javaDir + "/" + mainClassName(artifactId) + ".java");
            System.out.println("  created src/main/resources/application.yml");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create project at " + targetDir, e);
        }
    }

    private void writePom(Path targetDir, String artifactId, String basePackage) throws IOException {
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.0.5</version>
                    </parent>

                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>0.1.0-SNAPSHOT</version>
                    <name>%s</name>

                    <properties>
                        <java.version>25</java.version>
                        <crablet.version>1.0.0-SNAPSHOT</crablet.version>
                    </properties>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.crablet</groupId>
                                <artifactId>crablet</artifactId>
                                <version>${crablet.version}</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-jdbc</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.flywaydb</groupId>
                            <artifactId>flyway-core</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.flywaydb</groupId>
                            <artifactId>flyway-database-postgresql</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.crablet</groupId>
                            <artifactId>crablet-eventstore</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.crablet</groupId>
                            <artifactId>crablet-commands</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.crablet</groupId>
                            <artifactId>crablet-commands-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.crablet</groupId>
                            <artifactId>crablet-views</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.crablet</groupId>
                            <artifactId>crablet-automations</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.crablet</groupId>
                            <artifactId>crablet-outbox</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>am.ik.yavi</groupId>
                            <artifactId>yavi</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>com.crablet</groupId>
                            <artifactId>crablet-test-support</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(basePackage, artifactId, artifactId);
        Files.writeString(targetDir.resolve("pom.xml"), pom);
    }

    private void writeMainClass(Path srcMain, String basePackage, String artifactId) throws IOException {
        String className = mainClassName(artifactId);
        String content = """
                package %s;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class %s {
                    public static void main(String[] args) {
                        SpringApplication.run(%s.class, args);
                    }
                }
                """.formatted(basePackage, className, className);
        Files.writeString(srcMain.resolve(className + ".java"), content);
    }

    private void writeApplicationYml(Path resourcesDir) throws IOException {
        String yml = """
                spring:
                  datasource:
                    url: jdbc:postgresql://localhost:5432/mydb
                    username: postgres
                    password: postgres
                  flyway:
                    enabled: true
                """;
        Files.writeString(resourcesDir.resolve("application.yml"), yml);
    }

    private String mainClassName(String artifactId) {
        String[] parts = artifactId.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb + "App";
    }
}

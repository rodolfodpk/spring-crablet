package com.crablet.codegen;

import com.crablet.codegen.cli.CodegenCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
        "com.embabel.agent.autoconfigure.platform.AgentPlatformAutoConfiguration",
        "com.embabel.agent.autoconfigure.models.anthropic.AgentAnthropicAutoConfiguration",
        "com.embabel.agent.autoconfigure.models.openai.AgentOpenAiAutoConfiguration"
})
public class CodegenApp implements CommandLineRunner {

    private final CodegenCommand command;

    public CodegenApp(CodegenCommand command) {
        this.command = command;
    }

    public static void main(String[] args) {
        SpringApplication.run(CodegenApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        command.run(args);
    }
}

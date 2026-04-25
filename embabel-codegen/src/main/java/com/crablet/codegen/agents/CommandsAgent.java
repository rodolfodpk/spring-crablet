package com.crablet.codegen.agents;

import com.crablet.codegen.model.CommandSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.FieldSpec;
import com.crablet.codegen.tools.FileWriterTool;
import com.crablet.codegen.tools.TemplateLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.stream.Collectors;

@Component
public class CommandsAgent {

    private final AnthropicService anthropic;
    private final FileWriterTool fileWriter;
    private final TemplateLoader templates;

    public CommandsAgent(AnthropicService anthropic, FileWriterTool fileWriter, TemplateLoader templates) {
        this.anthropic = anthropic;
        this.fileWriter = fileWriter;
        this.templates = templates;
    }

    public void generate(EventModel model, Path outputDir) {
        System.out.println("[CommandsAgent] Generating state record, StateProjector, and command handlers...");
        String commandHandlerTemplate = templates.load("Command Handler Interface");

        String system = """
                You are a Java code generator for the spring-crablet event sourcing framework.
                Generate idiomatic Java 25 code. Never use fully qualified class names inline — use imports.

                Framework patterns to follow:
                %s

                Command append strategy:
                - idempotent   → IdempotentCommandHandler — entity creation (first event)
                - commutative  → CommutativeCommandHandler — two variants, distinguished by guardEvents:
                                  guardEvents non-empty → CommutativeGuarded.withLifecycleGuard
                                  guardEvents empty    → Commutative.of(appendEvent) (pure, no guard)
                - non-commutative → NonCommutativeCommandHandler — order-matters operations

                For YAVI validation, use:
                  _string(name, c -> c.notNull().notBlank())
                  _integer(name, c -> c.greaterThan(0))
                  _integer(name, c -> c.between(300, 850))

                Command handler files are Java INTERFACES — empty interface body, no @Component, no handle() method,
                and no decide() declaration. Inherit decide() from the selected sub-interface.
                Do not generate implementation classes or any additional @Component handler files.
                Include a Javadoc comment with:
                  - one-line description of the command
                  - the append strategy name and its rationale
                  - a <pre> structural sketch showing a complete decide() implementation
                  - a note to create a @Component class implementing this interface in a separate file
                In <pre> blocks inside Javadoc, write */ as *&#47;.

                Output ONLY file blocks — no prose, no markdown:
                ===FILE: relative/path/to/ClassName.java===
                <complete file content>
                ===END FILE===
                """.formatted(commandHandlerTemplate);

        String commandPkg = model.basePackage() + ".command";
        String domainPkg = model.basePackage() + ".domain";

        String user = """
                Domain: %s
                Base package: %s

                Events (already generated in package %s):
                %s

                Generate in package %s:
                1. %sState record — boolean flags (isExisting, isAlreadyDecided, etc.) and numeric fields derived from the events below
                2. %sStateProjector — implements StateProjector<Optional<%sState>, %sEvent>; handles each event
                3. %sQueryPatterns — static factory methods for decision model queries and lifecycle guards
                4. One CommandHandler Java INTERFACE per command — empty interface body, no @Component.
                   Extend IdempotentCommandHandler, NonCommutativeCommandHandler, or CommutativeCommandHandler
                   based on the append strategy. CommutativeCommandHandler covers both pure commutative and
                   commutative-with-lifecycle-guard; choose the CommandDecision factory from the [guard=...] hint.
                   The interface file must compile with only necessary imports. Javadoc sketch uses framework
                   type names (EventType.type, AppendEvent.builder, CommandDecision factories).
                   Write */ inside <pre> blocks as *&#47;.

                Commands:
                %s
                """.formatted(
                model.domain(),
                commandPkg,
                domainPkg,
                model.events().stream().map(e -> "  - " + e.name()).collect(Collectors.joining("\n")),
                commandPkg,
                model.domain(), model.domain(), model.domain(), model.domain(), model.domain(),
                model.commands().stream()
                        .map(c -> "  - " + c.name()
                                + " [pattern=" + c.pattern()
                                + ", produces=" + c.produces()
                                + (c.hasGuard() ? ", guard=" + c.guardEvents() : "")
                                + ", fields=" + describeFields(c) + "]")
                        .collect(Collectors.joining("\n"))
        );

        String response = anthropic.complete(system, user);
        fileWriter.writeGeneratedFiles(response, outputDir);
    }

    private String describeFields(CommandSpec c) {
        return c.fields().stream()
                .map(f -> f.name() + ":" + f.displayType()
                        + (f.hasConstraints() ? f.yaviConstraints() : ""))
                .collect(Collectors.joining(", "));
    }
}

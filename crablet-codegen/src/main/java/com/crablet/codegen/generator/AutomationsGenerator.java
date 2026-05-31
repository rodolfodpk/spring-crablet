package com.crablet.codegen.generator;

import com.crablet.codegen.model.AutomationSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.tools.FileWriterTool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class AutomationsGenerator {

    private final FileWriterTool fileWriter;

    public AutomationsGenerator(FileWriterTool fileWriter) {
        this.fileWriter = fileWriter;
    }

    public void generate(EventModel model, Path outputDir) {
        if (model.automations().isEmpty()) return;
        System.out.println("[AutomationsGenerator] Generating automation contracts...");

        String automationPkg = model.basePackage() + ".automation";
        String domainPkg = model.basePackage() + ".domain";

        for (AutomationSpec automation : model.automations()) {
            String className = toJavaIdentifier(automation.name()) + "AutomationHandler";
            fileWriter.writeGeneratedFile(
                    JavaRenderSupport.packagePath(automationPkg, className),
                    renderAutomation(automationPkg, domainPkg, className, automation),
                    outputDir
            );
        }
    }

    private String renderAutomation(String automationPkg, String domainPkg, String className, AutomationSpec automation) {
        return """
                package %s;

                import com.crablet.automations.AutomationHandler;
                import com.crablet.eventstore.EventType;
                import %s.%s;

                import java.util.Set;

                %s/**
                 * Create a {@code @Component} class implementing this interface to provide decide() logic.
                 */
                public interface %s extends AutomationHandler {

                    @Override
                    default String getAutomationName() {
                        return "%s";
                    }

                    @Override
                    default Set<String> getEventTypes() {
                        return Set.of(EventType.type(%s.class));
                    }

                    @Override
                    default Set<String> getRequiredTags() {
                        return Set.of();
                    }
                }
                """.formatted(
                automationPkg,
                domainPkg,
                automation.triggeredBy(),
                JavaRenderSupport.GENERATED_HEADER,
                className,
                automation.name(),
                automation.triggeredBy()
        );
    }

    private String toJavaIdentifier(String value) {
        if (value == null || value.isBlank()) return "Generated";
        StringBuilder result = new StringBuilder();
        boolean upperNext = true;
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                result.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            } else {
                upperNext = true;
            }
        }
        if (result.isEmpty()) return "Generated";
        if (!Character.isJavaIdentifierStart(result.charAt(0))) result.insert(0, "Generated");
        return result.toString();
    }
}

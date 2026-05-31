package com.crablet.codegen.generator;

import com.crablet.codegen.model.CommandSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.FieldSpec;
import com.crablet.codegen.tools.FileWriterTool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CommandsGenerator {

    private final FileWriterTool fileWriter;

    public CommandsGenerator(FileWriterTool fileWriter) {
        this.fileWriter = fileWriter;
    }

    public void generate(EventModel model, Path outputDir) {
        String commandPackage = model.basePackage() + ".command";
        for (CommandSpec command : model.commands()) {
            fileWriter.writeGeneratedFile(
                    JavaRenderSupport.packagePath(commandPackage, command.name()),
                    renderCommandRecord(commandPackage, command),
                    outputDir
            );
            String contractName = command.name() + "CommandHandler";
            fileWriter.writeGeneratedFile(
                    JavaRenderSupport.packagePath(commandPackage, contractName),
                    renderHandlerContract(commandPackage, contractName, command),
                    outputDir
            );
        }
    }

    private String renderCommandRecord(String packageName, CommandSpec command) {
        Set<String> imports = new LinkedHashSet<>();
        String fields = command.fields().stream()
                .map(field -> "        " + JavaRenderSupport.simpleJavaType(field, imports) + " " + field.name())
                .collect(Collectors.joining(",\n"));

        String validation = renderValidation(command);
        return """
                package %s;

                %s%spublic record %s(
                %s
                ) {
                %s}
                """.formatted(
                packageName,
                JavaRenderSupport.importsBlock(imports),
                JavaRenderSupport.GENERATED_HEADER,
                command.name(),
                fields,
                validation
        );
    }

    private String renderValidation(CommandSpec command) {
        List<FieldSpec> constrained = command.fields().stream()
                .filter(field -> field.hasConstraints() || "string".equals(field.type()))
                .toList();
        if (constrained.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        out.append("    public ").append(command.name()).append(" {\n");
        for (FieldSpec field : constrained) {
            renderFieldValidation(out, command.name(), field);
        }
        out.append("    }\n");
        return out.append("\n").toString();
    }

    private void renderFieldValidation(StringBuilder out, String commandName, FieldSpec field) {
        String label = commandName + "." + field.name();
        switch (field.type()) {
            case "string" -> {
                if (field.minLength() != null && field.minLength() > 0) {
                    out.append("        if (").append(field.name()).append(" == null || ").append(field.name()).append(".isBlank()) {\n")
                            .append("            throw new IllegalArgumentException(\"").append(label).append(" must not be blank\");\n")
                            .append("        }\n");
                }
                if (field.maxLength() != null) {
                    out.append("        if (").append(field.name()).append(" != null && ").append(field.name()).append(".length() > ")
                            .append(field.maxLength()).append(") {\n")
                            .append("            throw new IllegalArgumentException(\"").append(label).append(" must be at most ")
                            .append(field.maxLength()).append(" characters\");\n")
                            .append("        }\n");
                }
            }
            case "number", "BigDecimal" -> renderBigDecimalValidation(out, label, field);
            case "integer", "int", "long" -> renderNumericValidation(out, label, field);
            case "array" -> renderCollectionValidation(out, label, field, "size()");
            case "map" -> renderCollectionValidation(out, label, field, "size()");
            default -> {
            }
        }
    }

    private void renderNumericValidation(StringBuilder out, String label, FieldSpec field) {
        if (field.exclusiveMinimum() != null) {
            out.append("        if (").append(field.name()).append(" <= ").append(field.exclusiveMinimum()).append(") {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must be greater than ")
                    .append(field.exclusiveMinimum()).append("\");\n")
                    .append("        }\n");
        }
        if (field.minimum() != null) {
            out.append("        if (").append(field.name()).append(" < ").append(field.minimum()).append(") {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must be greater than or equal to ")
                    .append(field.minimum()).append("\");\n")
                    .append("        }\n");
        }
        if (field.exclusiveMaximum() != null) {
            out.append("        if (").append(field.name()).append(" >= ").append(field.exclusiveMaximum()).append(") {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must be less than ")
                    .append(field.exclusiveMaximum()).append("\");\n")
                    .append("        }\n");
        }
        if (field.maximum() != null) {
            out.append("        if (").append(field.name()).append(" > ").append(field.maximum()).append(") {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must be less than or equal to ")
                    .append(field.maximum()).append("\");\n")
                    .append("        }\n");
        }
    }

    private void renderBigDecimalValidation(StringBuilder out, String label, FieldSpec field) {
        if (field.exclusiveMinimum() != null) {
            out.append("        if (").append(field.name()).append(" == null || ").append(field.name())
                    .append(".compareTo(new BigDecimal(\"").append(field.exclusiveMinimum()).append("\")) <= 0) {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must be greater than ")
                    .append(field.exclusiveMinimum()).append("\");\n")
                    .append("        }\n");
        }
        if (field.minimum() != null) {
            out.append("        if (").append(field.name()).append(" == null || ").append(field.name())
                    .append(".compareTo(new BigDecimal(\"").append(field.minimum()).append("\")) < 0) {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must be greater than or equal to ")
                    .append(field.minimum()).append("\");\n")
                    .append("        }\n");
        }
        if (field.exclusiveMaximum() != null) {
            out.append("        if (").append(field.name()).append(" == null || ").append(field.name())
                    .append(".compareTo(new BigDecimal(\"").append(field.exclusiveMaximum()).append("\")) >= 0) {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must be less than ")
                    .append(field.exclusiveMaximum()).append("\");\n")
                    .append("        }\n");
        }
        if (field.maximum() != null) {
            out.append("        if (").append(field.name()).append(" == null || ").append(field.name())
                    .append(".compareTo(new BigDecimal(\"").append(field.maximum()).append("\")) > 0) {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must be less than or equal to ")
                    .append(field.maximum()).append("\");\n")
                    .append("        }\n");
        }
    }

    private void renderCollectionValidation(StringBuilder out, String label, FieldSpec field, String sizeAccessor) {
        if (field.minItems() != null) {
            out.append("        if (").append(field.name()).append(" == null || ").append(field.name()).append(".")
                    .append(sizeAccessor).append(" < ").append(field.minItems()).append(") {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must contain at least ")
                    .append(field.minItems()).append(" item(s)\");\n")
                    .append("        }\n");
        }
        if (field.maxItems() != null) {
            out.append("        if (").append(field.name()).append(" != null && ").append(field.name()).append(".")
                    .append(sizeAccessor).append(" > ").append(field.maxItems()).append(") {\n")
                    .append("            throw new IllegalArgumentException(\"").append(label).append(" must contain at most ")
                    .append(field.maxItems()).append(" item(s)\");\n")
                    .append("        }\n");
        }
    }

    private String renderHandlerContract(String packageName, String contractName, CommandSpec command) {
        String handlerType = handlerType(command);
        String rationale = strategyRationale(command);
        return """
                package %s;

                import com.crablet.command.%s;

                %s/**
                 * Handles {@link %s}.
                 *
                 * <p>Append strategy: %s - %s.
                 *
                 * <p>Create a {@code @Component} class implementing this interface and provide {@code decide()}.
                 */
                public interface %s extends %s<%s> {
                }
                """.formatted(
                packageName,
                handlerType,
                JavaRenderSupport.GENERATED_HEADER,
                command.name(),
                command.pattern(),
                rationale,
                contractName,
                handlerType,
                command.name()
        );
    }

    private String handlerType(CommandSpec command) {
        if (command.isCommutative()) {
            return "CommutativeCommandHandler";
        }
        if (command.isNonCommutative()) {
            return "NonCommutativeCommandHandler";
        }
        return "IdempotentCommandHandler";
    }

    private String strategyRationale(CommandSpec command) {
        if (command.isCommutative()) {
            return command.hasGuard()
                    ? "order-independent append with lifecycle guard events " + command.guardEvents()
                    : "order-independent append";
        }
        if (command.isNonCommutative()) {
            return command.hasGuard()
                    ? "order-sensitive decision guarded by " + command.guardEvents()
                    : "order-sensitive decision";
        }
        return "entity creation or duplicate-safe first write";
    }
}

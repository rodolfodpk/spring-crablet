package com.crablet.codegen.generator;

import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.EventSpec;
import com.crablet.codegen.model.FieldSpec;
import com.crablet.codegen.tools.FileWriterTool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EventsGenerator {

    private final FileWriterTool fileWriter;

    public EventsGenerator(FileWriterTool fileWriter) {
        this.fileWriter = fileWriter;
    }

    public void generate(EventModel model, Path outputDir) {
        String domainPackage = model.basePackage() + ".domain";
        String interfaceName = model.domain() + "Event";
        String permits = model.events().stream()
                .map(EventSpec::name)
                .collect(Collectors.joining(", "));

        String interfaceContent = """
                package %s;

                %spublic sealed interface %s permits %s {
                }
                """.formatted(domainPackage, JavaRenderSupport.GENERATED_HEADER, interfaceName, permits);
        fileWriter.writeGeneratedFile(JavaRenderSupport.packagePath(domainPackage, interfaceName), interfaceContent, outputDir);

        for (EventSpec event : model.events()) {
            fileWriter.writeGeneratedFile(
                    JavaRenderSupport.packagePath(domainPackage, event.name()),
                    renderEventRecord(domainPackage, interfaceName, event),
                    outputDir
            );
        }
    }

    private String renderEventRecord(String packageName, String interfaceName, EventSpec event) {
        Set<String> imports = new LinkedHashSet<>();
        String fields = event.fields().stream()
                .map(field -> "        " + JavaRenderSupport.simpleJavaType(field, imports) + " " + field.name())
                .collect(Collectors.joining(",\n"));

        return """
                package %s;

                %s%spublic record %s(
                %s
                ) implements %s {
                }
                """.formatted(
                packageName,
                JavaRenderSupport.importsBlock(imports),
                JavaRenderSupport.GENERATED_HEADER,
                event.name(),
                fields,
                interfaceName
        );
    }
}

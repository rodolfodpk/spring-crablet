package com.crablet.codegen.k8s;

import com.crablet.codegen.model.EventModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class K8sGeneratorTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final K8sGenerator generator = new K8sGenerator();

    @Test
    void distributedKedaOutboxAndViewsWritesExpectedFiles(@TempDir Path out) throws Exception {
        EventModel model = yaml.readValue("""
                domain: Zeta
                basePackage: com.example
                events: []
                commands: []
                views:
                  - name: V
                    reads: [E1, E2]
                    tag: t
                    fields: []
                outbox:
                  - name: pub1
                    topic: t1
                    handles: [E1]
                deployment:
                  topology: distributed
                  commandReplicas: 3
                  keda:
                    enabled: true
                    minReplicas: 1
                    pollingInterval: 15
                """, EventModel.class);

        K8sTopology t = K8sTopology.from(model);
        generator.generate(t, out);

        Path base = out.resolve("k8s").resolve("base");
        assertThat(base.resolve("namespace.yaml")).exists();
        String api = Files.readString(base.resolve("deployment-api.yaml"));
        assertThat(api)
                .contains("CRABLET_VIEWS_ENABLED")
                .contains("SPRING_DATASOURCE_URL")
                .contains("false");
        String views = Files.readString(base.resolve("scaled-object-views-worker.yaml"));
        assertThat(views)
                .contains("view_progress")
                .contains("connectionFromEnv")
                .contains("KEDA_DATABASE_URL")
                .contains("minReplicaCount: 1")
                .contains("maxReplicaCount: 1")
                .contains("E1");
        String outbox = Files.readString(base.resolve("scaled-object-outbox-worker.yaml"));
        assertThat(outbox)
                .contains("outbox_topic_progress")
                .contains("topic = 't1'")
                .contains("publisher = 'pub1'")
                .contains("AND type = ANY(ARRAY['E1']::text[])")
                .doesNotContain("ANY(ARRAY[ARRAY");

        String k = Files.readString(base.resolve("kustomization.yaml"));
        assertThat(k).contains("namespace.yaml").contains("deployment-api.yaml");
        assertThat(Files.readString(base.resolve("pdb-views-worker.yaml"))).isNotEmpty();
    }

    @Test
    void noPdbWhenMinReplicasZero(@TempDir Path out) throws Exception {
        EventModel model = yaml.readValue("""
                domain: Z
                basePackage: p
                events: []
                commands: []
                views:
                  - name: V
                    reads: [E]
                    tag: t
                    fields: []
                deployment:
                  topology: distributed
                  keda:
                    enabled: true
                    minReplicas: 0
                """, EventModel.class);
        generator.generate(K8sTopology.from(model), out);
        Path base = out.resolve("k8s").resolve("base");
        assertThat(base.resolve("pdb-views-worker.yaml")).doesNotExist();
    }

    @Test
    void monolithKedaEmitsMonolithScaledObjectOnly(@TempDir Path out) throws Exception {
        EventModel model = yaml.readValue("""
                domain: Mono
                basePackage: p
                events: []
                commands: []
                automations:
                  - name: a
                    triggeredBy: E
                    emitsCommand: C
                deployment:
                  topology: monolith
                  keda:
                    enabled: true
                """, EventModel.class);
        generator.generate(K8sTopology.from(model), out);
        Path base = out.resolve("k8s").resolve("base");
        assertThat(base.resolve("scaled-object-monolith.yaml")).exists();
        assertThat(base.resolve("deployment-views-worker.yaml")).doesNotExist();
        assertThat(base.resolve("scaled-object-views-worker.yaml")).doesNotExist();
    }

    @Test
    void secretTemplateHasReplaceComment(@TempDir Path out) throws Exception {
        EventModel model = yaml.readValue("""
                domain: S
                basePackage: p
                events: []
                commands: []
                """, EventModel.class);
        generator.generate(K8sTopology.from(model), out);
        String s = Files.readString(out.resolve("k8s/base/secret-template.yaml"));
        assertThat(s)
                .contains("spring-datasource-url")
                .contains("keda-connection-string");
        assertThat(s.toLowerCase()).contains("replace");
    }
}

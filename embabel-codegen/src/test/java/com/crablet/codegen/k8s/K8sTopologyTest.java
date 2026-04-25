package com.crablet.codegen.k8s;

import com.crablet.codegen.model.EventModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class K8sTopologyTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void commandOnlyMonolith() throws Exception {
        EventModel m = yaml.readValue("""
                domain: X
                basePackage: p
                events: []
                commands: []
                """, EventModel.class);
        K8sTopology t = K8sTopology.from(m);
        assertThat(t.hasViews()).isFalse();
        assertThat(t.hasAutomations()).isFalse();
        assertThat(t.hasOutbox()).isFalse();
        assertThat(t.commandReplicas()).isEqualTo(2);
    }

    @Test
    void monolithWithViewsForcesCommandReplicasOne() throws Exception {
        EventModel m = yaml.readValue("""
                domain: X
                basePackage: p
                events: []
                commands: []
                views:
                  - name: V
                    reads: [E]
                    tag: t
                    fields: []
                """, EventModel.class);
        K8sTopology t = K8sTopology.from(m);
        assertThat(t.hasViews()).isTrue();
        assertThat(t.commandReplicas()).isEqualTo(1);
    }

    @Test
    void distributedKedaMinReplicasZero() throws Exception {
        EventModel m = yaml.readValue("""
                domain: X
                basePackage: p
                events: []
                commands: []
                deployment:
                  topology: distributed
                  commandReplicas: 2
                  keda:
                    enabled: true
                    minReplicas: 0
                    pollingInterval: 10
                """, EventModel.class);
        K8sTopology t = K8sTopology.from(m);
        assertThat(t.distributed()).isTrue();
        assertThat(t.kedaMinReplicas()).isZero();
    }

    @Test
    void monolithKedaMinReplicasEnforcedToAtLeastOne() throws Exception {
        EventModel m = yaml.readValue("""
                domain: X
                basePackage: p
                events: []
                commands: []
                deployment:
                  topology: monolith
                  keda:
                    minReplicas: 0
                """, EventModel.class);
        K8sTopology t = K8sTopology.from(m);
        assertThat(t.kedaMinReplicas()).isEqualTo(1);
    }

    @Test
    void viewEventTypesUnionDedupedAndSorted() throws Exception {
        EventModel m = yaml.readValue("""
                domain: X
                basePackage: p
                events: []
                commands: []
                views:
                  - name: A
                    reads: [B, C]
                    tag: t
                    fields: []
                  - name: B
                    reads: [A, B]
                    tag: t
                    fields: []
                """, EventModel.class);
        K8sTopology t = K8sTopology.from(m);
        assertThat(t.viewEventTypes())
                .containsExactly("A", "B", "C");
    }
}

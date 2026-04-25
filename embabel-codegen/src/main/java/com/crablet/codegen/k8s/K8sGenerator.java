package com.crablet.codegen.k8s;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes Kubernetes manifests under {@code k8s/base/} from a resolved {@link K8sTopology}.
 */
@Component
public class K8sGenerator {

    private static final String PLACEHOLDER_IMAGE = "your-registry/crablet-app:latest";

    private final ObjectMapper yamlMapper;

    public K8sGenerator() {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build();
        this.yamlMapper = new ObjectMapper(factory);
    }

    public void generate(K8sTopology t, Path outputDir) throws IOException {
        Path base = outputDir.resolve("k8s").resolve("base");
        Files.createDirectories(base);

        List<String> resources = new ArrayList<>();

        writeFile(base, "namespace.yaml", toYaml(namespace(t.appName())));
        resources.add("namespace.yaml");

        writeFile(base, "deployment-api.yaml", toYaml(deploymentApi(t)));
        resources.add("deployment-api.yaml");

        writeFile(base, "service-api.yaml", toYaml(serviceApi(t)));
        resources.add("service-api.yaml");

        String secret = secretTemplateStatic(t.appName());
        Files.writeString(base.resolve("secret-template.yaml"), secret, StandardCharsets.UTF_8);
        resources.add("secret-template.yaml");

        if (t.distributed() && t.hasViews()) {
            writeFile(base, "deployment-views-worker.yaml", toYaml(deploymentWorker(t, "views-worker", ModuleEnv.viewsOnly())));
            resources.add("deployment-views-worker.yaml");
        }
        if (t.distributed() && t.hasAutomations()) {
            writeFile(base, "deployment-automations-worker.yaml",
                    toYaml(deploymentWorker(t, "automations-worker", ModuleEnv.automationsOnly())));
            resources.add("deployment-automations-worker.yaml");
        }
        if (t.distributed() && t.hasOutbox()) {
            writeFile(base, "deployment-outbox-worker.yaml",
                    toYaml(deploymentWorker(t, "outbox-worker", ModuleEnv.outboxOnly())));
            resources.add("deployment-outbox-worker.yaml");
        }

        if (t.kedaEnabled() && t.distributed() && t.hasViews()) {
            writeFile(base, "scaled-object-views-worker.yaml",
                    toYaml(scaledObject(t, "views-worker", "views-worker", viewsTrigger(t))));
            resources.add("scaled-object-views-worker.yaml");
        }
        if (t.kedaEnabled() && t.distributed() && t.hasAutomations()) {
            writeFile(base, "scaled-object-automations-worker.yaml",
                    toYaml(scaledObject(t, "automations-worker", "automations-worker", automationsTrigger(t))));
            resources.add("scaled-object-automations-worker.yaml");
        }
        if (t.kedaEnabled() && t.distributed() && t.hasOutbox()) {
            writeFile(base, "scaled-object-outbox-worker.yaml",
                    toYaml(scaledObjectMultiTrigger(t, "outbox-worker", "outbox-worker", outboxTriggers(t))));
            resources.add("scaled-object-outbox-worker.yaml");
        }
        if (!t.distributed() && t.kedaEnabled() && t.hasPollerModules()) {
            writeFile(base, "scaled-object-monolith.yaml",
                    toYaml(scaledObjectMonolith(t)));
            resources.add("scaled-object-monolith.yaml");
        }

        if (t.distributed() && t.hasViews() && t.kedaMinReplicas() >= 1) {
            writeFile(base, "pdb-views-worker.yaml", toYaml(pdb(t, "views-worker")));
            resources.add("pdb-views-worker.yaml");
        }
        if (t.distributed() && t.hasAutomations() && t.kedaMinReplicas() >= 1) {
            writeFile(base, "pdb-automations-worker.yaml", toYaml(pdb(t, "automations-worker")));
            resources.add("pdb-automations-worker.yaml");
        }
        if (t.distributed() && t.hasOutbox() && t.kedaMinReplicas() >= 1) {
            writeFile(base, "pdb-outbox-worker.yaml", toYaml(pdb(t, "outbox-worker")));
            resources.add("pdb-outbox-worker.yaml");
        }

        resources.sort(String::compareTo);
        writeFile(base, "kustomization.yaml", toYaml(kustomization(t.appName(), resources)));

        Files.writeString(base.resolve("README-k8s.md"), readmeK8s(t), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> namespace(String appName) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", "v1");
        root.put("kind", "Namespace");
        root.put("metadata", Map.of("name", appName));
        return root;
    }

    private Map<String, Object> deploymentApi(K8sTopology t) {
        return deployment(t, "command-api", t.commandReplicas(), commandApiEnv(t));
    }

    private Map<String, Object> deploymentWorker(K8sTopology t, String component, ModuleEnv env) {
        return deployment(t, component, 1, workerEnv(t, env));
    }

    private Map<String, Object> deployment(K8sTopology t, String component, int replicas, List<Map<String, Object>> env) {
        String app = t.appName();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", "apps/v1");
        root.put("kind", "Deployment");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", component);
        meta.put("namespace", app);
        meta.put("labels", Map.of("app", app, "component", component));
        root.put("metadata", meta);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("replicas", replicas);
        spec.put("selector", Map.of("matchLabels", Map.of("app", app, "component", component)));
        Map<String, Object> tpl = new LinkedHashMap<>();
        tpl.put("metadata", Map.of("labels", Map.of("app", app, "component", component)));
        Map<String, Object> podSpec = new LinkedHashMap<>();
        List<Map<String, Object>> containers = new ArrayList<>();
        Map<String, Object> container = new LinkedHashMap<>();
        container.put("name", "app");
        container.put("image", PLACEHOLDER_IMAGE);
        container.put("ports", List.of(Map.of("containerPort", 8080)));
        container.put("env", env);
        containers.add(container);
        podSpec.put("containers", containers);
        tpl.put("spec", podSpec);
        spec.put("template", tpl);
        root.put("spec", spec);
        return root;
    }

    private List<Map<String, Object>> commandApiEnv(K8sTopology t) {
        List<Map<String, Object>> env = new ArrayList<>(dbEnv(t.appName()));
        if (t.distributed()) {
            env.add(envVar("CRABLET_VIEWS_ENABLED", "false"));
            env.add(envVar("CRABLET_AUTOMATIONS_ENABLED", "false"));
            env.add(envVar("CRABLET_OUTBOX_ENABLED", "false"));
        } else {
            env.add(envVar("CRABLET_VIEWS_ENABLED", t.hasViews() ? "true" : "false"));
            env.add(envVar("CRABLET_AUTOMATIONS_ENABLED", t.hasAutomations() ? "true" : "false"));
            env.add(envVar("CRABLET_OUTBOX_ENABLED", t.hasOutbox() ? "true" : "false"));
        }
        return env;
    }

    private List<Map<String, Object>> workerEnv(K8sTopology t, ModuleEnv m) {
        List<Map<String, Object>> env = new ArrayList<>(dbEnv(t.appName()));
        env.add(envVar("CRABLET_VIEWS_ENABLED", m.views));
        env.add(envVar("CRABLET_AUTOMATIONS_ENABLED", m.automations));
        env.add(envVar("CRABLET_OUTBOX_ENABLED", m.outbox));
        return env;
    }

    private List<Map<String, Object>> dbEnv(String appName) {
        String secret = appName + "-db-secret";
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(secretEnv("SPRING_DATASOURCE_URL", secret, "spring-datasource-url"));
        list.add(secretEnv("SPRING_DATASOURCE_USERNAME", secret, "datasource-username"));
        list.add(secretEnv("SPRING_DATASOURCE_PASSWORD", secret, "datasource-password"));
        list.add(secretEnv("KEDA_DATABASE_URL", secret, "keda-connection-string"));
        return list;
    }

    private static Map<String, Object> secretEnv(String name, String secretName, String key) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", name);
        Map<String, Object> from = new LinkedHashMap<>();
        from.put("name", secretName);
        from.put("key", key);
        root.put("valueFrom", Map.of("secretKeyRef", from));
        return root;
    }

    private static Map<String, Object> envVar(String name, String value) {
        return Map.of("name", name, "value", value);
    }

    private Map<String, Object> serviceApi(K8sTopology t) {
        String app = t.appName();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", "v1");
        root.put("kind", "Service");
        root.put("metadata", Map.of(
                "name", "command-api",
                "namespace", app,
                "labels", Map.of("app", app, "component", "command-api")));
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("selector", Map.of("app", app, "component", "command-api"));
        spec.put("ports", List.of(Map.of("port", 8080, "targetPort", 8080)));
        root.put("spec", spec);
        return root;
    }

    private Map<String, Object> scaledObject(
            K8sTopology t, String name, String scaleTargetName, String query) {
        return scaledObjectInternal(t, name, scaleTargetName, List.of(triggerBlock(query)));
    }

    private Map<String, Object> scaledObjectMultiTrigger(
            K8sTopology t, String name, String scaleTargetName, List<String> queries) {
        List<Map<String, Object>> triggers = new ArrayList<>();
        for (String q : queries) {
            triggers.add(triggerBlock(q));
        }
        return scaledObjectInternal(t, name, scaleTargetName, triggers);
    }

    private Map<String, Object> scaledObjectInternal(
            K8sTopology t, String name, String scaleTargetName, List<Map<String, Object>> triggers) {
        String app = t.appName();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", "keda.sh/v1alpha1");
        root.put("kind", "ScaledObject");
        root.put("metadata", Map.of(
                "name", name,
                "namespace", app,
                "labels", Map.of("app", app, "component", scaleTargetName)));

        Map<String, Object> spec = new LinkedHashMap<>();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("name", scaleTargetName);
        ref.put("kind", "Deployment");
        ref.put("apiVersion", "apps/v1");
        spec.put("scaleTargetRef", ref);
        spec.put("minReplicaCount", t.kedaMinReplicas());
        spec.put("maxReplicaCount", 1);
        spec.put("pollingInterval", t.kedaPollingInterval());
        spec.put("triggers", triggers);
        root.put("spec", spec);
        return root;
    }

    private Map<String, Object> scaledObjectMonolith(K8sTopology t) {
        List<Map<String, Object>> triggers = new ArrayList<>();
        if (t.hasViews()) {
            triggers.add(triggerBlock(viewsTrigger(t)));
        }
        if (t.hasAutomations()) {
            triggers.add(triggerBlock(automationsTrigger(t)));
        }
        if (t.hasOutbox()) {
            for (String q : outboxTriggers(t)) {
                triggers.add(triggerBlock(q));
            }
        }
        return scaledObjectInternal(t, "monolith-command-api", "command-api", triggers);
    }

    private static Map<String, Object> triggerBlock(String query) {
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("type", "postgresql");
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("query", query);
        meta.put("targetQueryValue", "1");
        meta.put("activationTargetQueryValue", "0");
        meta.put("connectionFromEnv", "KEDA_DATABASE_URL");
        trigger.put("metadata", meta);
        return trigger;
    }

    private String viewsTrigger(K8sTopology t) {
        return """
                SELECT COUNT(*) FROM events
                WHERE position > COALESCE((SELECT MIN(last_position) FROM view_progress), 0)
                AND type = ANY(%s)""".formatted(pgTextArray(t.viewEventTypes()));
    }

    private String automationsTrigger(K8sTopology t) {
        return """
                SELECT COUNT(*) FROM events
                WHERE position > COALESCE((SELECT MIN(last_position) FROM automation_progress), 0)
                AND type = ANY(%s)""".formatted(pgTextArray(t.automationEventTypes()));
    }

    private List<String> outboxTriggers(K8sTopology t) {
        List<String> list = new ArrayList<>();
        for (OutboxWorker w : t.outboxWorkers()) {
            String topic = escapeSqlString(w.topic());
            String publisher = escapeSqlString(w.publisher());
            String q = """
                    SELECT COUNT(*) FROM events
                    WHERE position > COALESCE(
                      (SELECT last_position FROM outbox_topic_progress WHERE topic = '%s' AND publisher = '%s'),
                      0)
                    AND type = ANY(%s)"""
                    .formatted(topic, publisher, pgTextArray(w.handles()));
            list.add(q);
        }
        return list;
    }

    private static String pgTextArray(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return "ARRAY[]::text[]";
        }
        StringBuilder sb = new StringBuilder("ARRAY[");
        for (int i = 0; i < eventTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("'").append(escapeSqlString(eventTypes.get(i))).append("'");
        }
        sb.append("]::text[]");
        return sb.toString();
    }

    private static String escapeSqlString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("'", "''");
    }

    private Map<String, Object> pdb(K8sTopology t, String component) {
        String app = t.appName();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", "policy/v1");
        root.put("kind", "PodDisruptionBudget");
        root.put("metadata", Map.of(
                "name", component,
                "namespace", app,
                "labels", Map.of("app", app, "component", component)));
        root.put("spec", Map.of(
                "minAvailable", 1,
                "selector", Map.of("matchLabels", Map.of("app", app, "component", component))));
        return root;
    }

    private Map<String, Object> kustomization(String appName, List<String> resources) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("apiVersion", "kustomize.config.k8s.io/v1beta1");
        k.put("kind", "Kustomization");
        k.put("namespace", appName);
        k.put("resources", resources);
        return k;
    }

    private String toYaml(Object value) throws IOException {
        return yamlMapper.writeValueAsString(value);
    }

    private static void writeFile(Path base, String name, String content) throws IOException {
        Files.writeString(base.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static String secretTemplateStatic(String appName) {
        return """
                apiVersion: v1
                kind: Secret
                metadata:
                  name: %s-db-secret
                  namespace: %s
                type: Opaque
                stringData:
                  spring-datasource-url: "jdbc:postgresql://CHANGE_ME:5432/CHANGE_ME"
                  datasource-username: "CHANGE_ME"
                  datasource-password: "CHANGE_ME"
                  keda-connection-string: "host=CHANGE_ME port=5432 user=CHANGE_ME password=CHANGE_ME dbname=CHANGE_ME sslmode=require"
                # Replace values before production; see README-k8s.md
                """.formatted(appName, appName);
    }

    private String readmeK8s(K8sTopology t) {
        return """
                # Kubernetes manifests (generated)

                Generated under `k8s/base` from your `event-model.yaml` `deployment` block and domain modules.

                ## Before you deploy

                1. Fill in `secret-template.yaml` (or use Sealed Secrets / external secrets). Spring uses `spring-datasource-*`; KEDA's PostgreSQL scaler uses the libpq `keda-connection-string` key (referenced as `KEDA_DATABASE_URL` on pods).
                2. Set a real container image in each Deployment (placeholder: `%s`).
                3. See [Deployment Topology](../../docs/DEPLOYMENT_TOPOLOGY.md) for Crablet's poller and singleton-worker rules (from `k8s/base`, adjust path if your repo layout differs).

                ## KEDA (optional)

                If you enabled `deployment.keda.enabled` in the model, install KEDA in the cluster, e.g.:

                `helm install keda kedacore/keda --namespace keda --create-namespace`

                - `minReplicas: 0` in the model is only effective in **distributed** topology; monolith mode forces at least 1 so the command API is not scaled to zero.
                - PodDisruptionBudgets for workers are only emitted when `keda.minReplicas >= 1` (and distributed workers exist).

                ## Monolith vs distributed

                - **Monolith:** one `command-api` Deployment; module flags follow which slices exist in the model. With KEDA and poller modules, a single `scaled-object-monolith.yaml` carries all PostgreSQL triggers.
                - **Distributed:** `command-api` has writers disabled; separate worker Deployments per enabled module, each with the appropriate `CRABLET_*_ENABLED` flags.

                ## Empty progress tables

                If `view_progress` (or other progress tables) have no rows yet, the COALESCE in generated queries treats missing positions as 0, which can make backlog look large on first deploy—expected until processors advance progress.

                """.formatted(PLACEHOLDER_IMAGE);
    }

    private record ModuleEnv(String views, String automations, String outbox) {
        static ModuleEnv viewsOnly() {
            return new ModuleEnv("true", "false", "false");
        }

        static ModuleEnv automationsOnly() {
            return new ModuleEnv("false", "true", "false");
        }

        static ModuleEnv outboxOnly() {
            return new ModuleEnv("false", "false", "true");
        }
    }
}

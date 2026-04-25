package com.crablet.codegen.k8s;

import com.crablet.codegen.model.AutomationSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.KedaSpec;
import com.crablet.codegen.model.OutboxSpec;
import com.crablet.codegen.model.ViewSpec;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;

/**
 * Resolved Kubernetes deployment shape from an event model (pure data, no I/O).
 */
public record K8sTopology(
        String appName,
        boolean distributed,
        int commandReplicas,
        boolean kedaEnabled,
        int kedaMinReplicas,
        int kedaPollingInterval,
        boolean hasViews,
        boolean hasAutomations,
        boolean hasOutbox,
        List<String> viewEventTypes,
        List<String> automationEventTypes,
        List<OutboxWorker> outboxWorkers) {

    public static K8sTopology from(EventModel model) {
        var deployment = model.deployment();
        KedaSpec keda = deployment.keda();
        boolean distributed = deployment.isDistributed();
        boolean hasViews = !model.views().isEmpty();
        boolean hasAutomations = !model.automations().isEmpty();
        boolean hasOutbox = !model.outbox().isEmpty();

        int commandReplicas;
        if (!distributed) {
            boolean anyPoller = hasViews || hasAutomations || hasOutbox;
            commandReplicas = anyPoller ? 1 : deployment.commandReplicas();
        } else {
            commandReplicas = deployment.commandReplicas();
        }

        int kedaMin = distributed
                ? keda.minReplicas()
                : Math.max(1, keda.minReplicas());

        Set<String> viewTypes = new HashSet<>();
        for (ViewSpec v : model.views()) {
            viewTypes.addAll(v.reads());
        }
        List<String> viewEventTypes = viewTypes.stream()
                .sorted(naturalOrder())
                .collect(toList());

        Set<String> autoTypes = new HashSet<>();
        for (AutomationSpec a : model.automations()) {
            if (a.triggeredBy() != null && !a.triggeredBy().isBlank()) {
                autoTypes.add(a.triggeredBy().trim());
            }
        }
        List<String> automationEventTypes = autoTypes.stream()
                .sorted(naturalOrder())
                .collect(toList());

        List<OutboxWorker> outboxWorkers = model.outbox().stream()
                .map(K8sTopology::outboxFromSpec)
                .toList();

        return new K8sTopology(
                Dns1123.sanitize(model.domain()),
                distributed,
                commandReplicas,
                keda.enabled(),
                kedaMin,
                keda.pollingInterval(),
                hasViews,
                hasAutomations,
                hasOutbox,
                viewEventTypes,
                automationEventTypes,
                outboxWorkers);
    }

    private static OutboxWorker outboxFromSpec(OutboxSpec spec) {
        return new OutboxWorker(
                spec.topic() != null ? spec.topic() : "",
                spec.name() != null ? spec.name() : "outbox",
                spec.handles().stream()
                        .sorted(Comparator.naturalOrder())
                        .toList());
    }

    public boolean hasPollerModules() {
        return hasViews || hasAutomations || hasOutbox;
    }
}

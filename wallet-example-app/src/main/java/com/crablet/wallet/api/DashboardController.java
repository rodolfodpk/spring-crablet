package com.crablet.wallet.api;

import com.crablet.automations.management.AutomationManagementService;
import com.crablet.outbox.TopicPublisherPair;
import com.crablet.outbox.management.OutboxManagementService;
import com.crablet.views.service.ViewManagementService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private final ViewManagementService viewManagementService;
    private final ObjectProvider<OutboxManagementService> outboxManagementServiceProvider;
    private final ObjectProvider<AutomationManagementService> automationManagementServiceProvider;

    public DashboardController(ViewManagementService viewManagementService,
                               ObjectProvider<OutboxManagementService> outboxManagementServiceProvider,
                               ObjectProvider<AutomationManagementService> automationManagementServiceProvider) {
        this.viewManagementService = viewManagementService;
        this.outboxManagementServiceProvider = outboxManagementServiceProvider;
        this.automationManagementServiceProvider = automationManagementServiceProvider;
    }

    // ========== Full Page ==========

    @GetMapping
    public String dashboard(Model model) {
        populateViews(model);
        populateOutbox(model);
        populateAutomations(model);
        return "dashboard";
    }

    // ========== Partial Refresh Fragments ==========

    @GetMapping("/views")
    public String viewsSection(Model model) {
        populateViews(model);
        return "dashboard :: views-section";
    }

    @GetMapping("/outbox")
    public String outboxSection(Model model) {
        populateOutbox(model);
        return "dashboard :: outbox-section";
    }

    @GetMapping("/automations")
    public String automationsSection(Model model) {
        populateAutomations(model);
        return "dashboard :: automations-section";
    }

    // ========== View Actions ==========

    @PostMapping("/views/{name}/pause")
    public String pauseView(@PathVariable String name, Model model) {
        viewManagementService.pause(name);
        populateViews(model);
        return "dashboard :: views-section";
    }

    @PostMapping("/views/{name}/resume")
    public String resumeView(@PathVariable String name, Model model) {
        viewManagementService.resume(name);
        populateViews(model);
        return "dashboard :: views-section";
    }

    @PostMapping("/views/{name}/reset")
    public String resetView(@PathVariable String name, Model model) {
        viewManagementService.reset(name);
        populateViews(model);
        return "dashboard :: views-section";
    }

    // ========== Outbox Actions ==========

    @PostMapping("/outbox/{topic}/publishers/{publisher}/pause")
    public String pauseOutbox(@PathVariable String topic, @PathVariable String publisher, Model model) {
        requireOutboxManagementService().pause(new TopicPublisherPair(topic, publisher));
        populateOutbox(model);
        return "dashboard :: outbox-section";
    }

    @PostMapping("/outbox/{topic}/publishers/{publisher}/resume")
    public String resumeOutbox(@PathVariable String topic, @PathVariable String publisher, Model model) {
        requireOutboxManagementService().resume(new TopicPublisherPair(topic, publisher));
        populateOutbox(model);
        return "dashboard :: outbox-section";
    }

    @PostMapping("/outbox/{topic}/publishers/{publisher}/reset")
    public String resetOutbox(@PathVariable String topic, @PathVariable String publisher, Model model) {
        requireOutboxManagementService().reset(new TopicPublisherPair(topic, publisher));
        populateOutbox(model);
        return "dashboard :: outbox-section";
    }

    // ========== Automation Actions ==========

    @PostMapping("/automations/{name}/pause")
    public String pauseAutomation(@PathVariable String name, Model model) {
        requireAutomationManagementService().pause(name);
        populateAutomations(model);
        return "dashboard :: automations-section";
    }

    @PostMapping("/automations/{name}/resume")
    public String resumeAutomation(@PathVariable String name, Model model) {
        requireAutomationManagementService().resume(name);
        populateAutomations(model);
        return "dashboard :: automations-section";
    }

    @PostMapping("/automations/{name}/reset")
    public String resetAutomation(@PathVariable String name, Model model) {
        requireAutomationManagementService().reset(name);
        populateAutomations(model);
        return "dashboard :: automations-section";
    }

    // ========== Helpers ==========

    private void populateViews(Model model) {
        model.addAttribute("viewsEnabled", true);
        model.addAttribute("views", viewManagementService.getAllProgressDetails().values());
    }

    private void populateOutbox(Model model) {
        OutboxManagementService outboxManagementService = outboxManagementServiceProvider.getIfAvailable();
        boolean outboxEnabled = outboxManagementService != null;
        model.addAttribute("outboxEnabled", outboxEnabled);
        model.addAttribute(
                "outboxList",
                outboxEnabled ? outboxManagementService.getAllProgressDetails() : List.of()
        );
    }

    private void populateAutomations(Model model) {
        AutomationManagementService automationManagementService = automationManagementServiceProvider.getIfAvailable();
        boolean automationsEnabled = automationManagementService != null;
        model.addAttribute("automationsEnabled", automationsEnabled);
        model.addAttribute(
                "automations",
                automationsEnabled ? automationManagementService.getAllProgressDetails().values() : List.of()
        );
    }

    private OutboxManagementService requireOutboxManagementService() {
        OutboxManagementService outboxManagementService = outboxManagementServiceProvider.getIfAvailable();
        if (outboxManagementService == null) {
            throw new IllegalStateException("Outbox management is not enabled");
        }
        return outboxManagementService;
    }

    private AutomationManagementService requireAutomationManagementService() {
        AutomationManagementService automationManagementService = automationManagementServiceProvider.getIfAvailable();
        if (automationManagementService == null) {
            throw new IllegalStateException("Automation management is not enabled");
        }
        return automationManagementService;
    }
}

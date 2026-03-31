package com.crablet.automations.adapter;

import com.crablet.automations.AutomationHandler;
import com.crablet.command.CommandExecutor;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventstore.store.StoredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches events to the appropriate {@link AutomationHandler} implementation.
 * Injects {@link CommandExecutor} into each automation invocation.
 */
public class AutomationDispatcher implements EventHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(AutomationDispatcher.class);

    private final Map<String, AutomationHandler> handlers;
    private final CommandExecutor commandExecutor;

    public AutomationDispatcher(List<AutomationHandler> handlers, CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
        this.handlers = new HashMap<>();
        for (AutomationHandler handler : handlers) {
            this.handlers.put(handler.getAutomationName(), handler);
            log.debug("Registered automation handler {} for name {}", handler.getClass().getSimpleName(), handler.getAutomationName());
        }
    }

    @Override
    public int handle(String automationName, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
        AutomationHandler handler = handlers.get(automationName);
        if (handler == null) {
            log.warn("No automation handler registered for name: {}", automationName);
            return 0;
        }

        int count = 0;
        for (StoredEvent event : events) {
            try {
                handler.react(event, commandExecutor);
                count++;
            } catch (Exception e) {
                log.error("Automation handler {} failed on event type={} position={}: {}",
                    automationName, event.type(), event.position(), e.getMessage(), e);
                throw e;
            }
        }

        log.debug("Automation handler {} processed {} events", automationName, count);
        return count;
    }
}

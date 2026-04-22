# Changelog

## Unreleased

### Breaking Changes

- Changed the `AutomationHandler` API from imperative `react(StoredEvent, CommandExecutor)` handling to declarative `decide(StoredEvent)` decisions. Automation handlers must now return `List<AutomationDecision>` values, such as `AutomationDecision.ExecuteCommand`, and the automation dispatcher owns command execution semantics.
- Removed webhook delivery methods from `AutomationHandler`, including `getWebhookUrl()`, `getWebhookHeaders()`, and `getWebhookTimeoutMs()`. Use an `OutboxPublisher` for external HTTP, Kafka, analytics, or CRM integrations.

package com.crablet.codegen.scaffold;

import java.util.List;

public record ScenarioSyncReport(
        String testPackage,
        List<MissingScenario> inModelNotOnDisk,
        List<String> onDiskNotInModel
) {

    public boolean isClean() {
        return inModelNotOnDisk.isEmpty() && onDiskNotInModel.isEmpty();
    }

    public String render() {
        if (isClean()) {
            return "All scenario test scaffolds are in sync. Scope: " + testPackage;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Scenario sync report — drift detected\n");
        sb.append("Scope: ").append(testPackage).append("\n");
        if (!inModelNotOnDisk.isEmpty()) {
            sb.append("\nIn model, not on disk (").append(inModelNotOnDisk.size()).append("):\n");
            for (MissingScenario m : inModelNotOnDisk) {
                sb.append("  - ").append(m.scenarioName())
                        .append("  →  ").append(m.expectedFileName()).append("\n");
            }
        }
        if (!onDiskNotInModel.isEmpty()) {
            sb.append("\nOn disk, not in model (").append(onDiskNotInModel.size()).append("):\n");
            for (String stem : onDiskNotInModel) {
                sb.append("  - ").append(stem).append(".java\n");
            }
        }
        sb.append("\nRun 'make generate' to write missing scaffolds. Delete stale test files manually.");
        return sb.toString();
    }
}

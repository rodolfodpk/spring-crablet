package com.crablet.codegen.planning;

public record PlannedArtifact(
        String section,
        String kind,
        String displayName,
        String detail
) {

    public static PlannedArtifact javaClass(String section, String packageName, String className) {
        return new PlannedArtifact(section, "java", packageName + "." + className, "Java class");
    }

    public static PlannedArtifact migration(String section, String fileName, String tableName) {
        return new PlannedArtifact(section, "migration", fileName, "Flyway migration for " + tableName);
    }
}

package com.crablet.observability;

/**
 * Low-cardinality observation tag names shared by Crablet modules.
 */
public final class CrabletObservationTags {

    public static final String AUTOMATION = "automation";
    public static final String COMMAND_TYPE = "command.type";
    public static final String ERROR_TYPE = "error.type";
    public static final String EVENT_TYPE = "event.type";
    public static final String INSTANCE_ID = "instance.id";
    public static final String OPERATION_TYPE = "operation.type";
    public static final String OUTCOME = "outcome";
    public static final String PROCESSOR = "processor";
    public static final String PUBLISHER = "publisher";
    public static final String VIEW = "view";

    private CrabletObservationTags() {
    }
}

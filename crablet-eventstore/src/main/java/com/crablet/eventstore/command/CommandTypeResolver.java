package com.crablet.eventstore.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Utility to extract command type from handler's generic type parameter.
 * Reads @JsonSubTypes annotation to get the type name, ensuring consistency.
 * 
 * Flow:
 * 1. Extract T from CommandHandler<T>
 * 2. Find @JsonSubTypes on T or its parent interfaces (e.g., WalletCommand, CourseCommand)
 * 3. Find entry where value = T
 * 4. Return name from that entry
 */
public class CommandTypeResolver {
    
    private static final Logger log = LoggerFactory.getLogger(CommandTypeResolver.class);
    
    /**
     * Extract command type string from handler's generic type parameter.
     * 
     * @param handlerClass the handler class implementing CommandHandler<T>
     * @return the command type name from @JsonSubTypes
     * @throws InvalidCommandException if type cannot be extracted
     */
    public static String extractCommandTypeFromHandler(Class<?> handlerClass) {
        // Validate that it's actually a CommandHandler
        if (!CommandHandler.class.isAssignableFrom(handlerClass)) {
            throw new InvalidCommandException(
                "Class " + handlerClass.getName() + " does not implement CommandHandler",
                handlerClass.getName()
            );
        }
        Class<?> commandClass = getCommandClassFromHandler(handlerClass);
        JsonSubTypes jsonSubTypes = findJsonSubTypesAnnotation(commandClass);
        
        if (jsonSubTypes == null) {
            throw new InvalidCommandException(
                "Command class " + commandClass.getName() + " is not part of a @JsonSubTypes hierarchy. " +
                "Ensure the command implements an interface with @JsonSubTypes annotation.",
                handlerClass.getName()
            );
        }
        
        for (JsonSubTypes.Type type : jsonSubTypes.value()) {
            if (type.value().equals(commandClass)) {
                log.debug("Extracted command type '{}' from handler {} for command class {}",
                    type.name(), handlerClass.getSimpleName(), commandClass.getSimpleName());
                return type.name();
            }
        }
        
        throw new InvalidCommandException(
            "Command class " + commandClass.getName() + " not found in @JsonSubTypes for handler " + 
            handlerClass.getName() + ". Check that @JsonSubTypes includes this command class.",
            handlerClass.getName()
        );
    }
    
    /**
     * Extract the command class T from CommandHandler<T>.
     * Checks implemented interfaces first, then superclass (for abstract base handlers).
     */
    private static Class<?> getCommandClassFromHandler(Class<?> handlerClass) {
        // Check implemented interfaces
        for (Type genericInterface : handlerClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) genericInterface;
                if (isCommandHandler(paramType)) {
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                        return (Class<?>) typeArgs[0];
                    }
                }
            }
        }
        
        // Check superclass (for abstract base handlers)
        Type genericSuperclass = handlerClass.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) genericSuperclass;
            if (isCommandHandler(paramType)) {
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    return (Class<?>) typeArgs[0];
                }
            }
        }
        
        throw new InvalidCommandException(
            "Cannot extract command type from handler " + handlerClass.getName() + 
            " - CommandHandler<T> not found. Ensure handler implements CommandHandler<T> with a concrete type.",
            handlerClass.getName()
        );
    }
    
    /**
     * Check if ParameterizedType represents CommandHandler<T>.
     */
    private static boolean isCommandHandler(ParameterizedType paramType) {
        Type rawType = paramType.getRawType();
        return rawType instanceof Class && CommandHandler.class.isAssignableFrom((Class<?>) rawType);
    }
    
    /**
     * Find @JsonSubTypes annotation on command class or its parent interfaces.
     * 
     * Checks:
     * 1. Command class itself (e.g., DepositCommand)
     * 2. Parent interfaces (e.g., WalletCommand, CourseCommand)
     */
    private static JsonSubTypes findJsonSubTypesAnnotation(Class<?> commandClass) {
        // Check command class itself
        JsonSubTypes annotation = commandClass.getAnnotation(JsonSubTypes.class);
        if (annotation != null) {
            return annotation;
        }
        
        // Check parent interfaces (WalletCommand, CourseCommand, etc.)
        for (Class<?> iface : commandClass.getInterfaces()) {
            annotation = iface.getAnnotation(JsonSubTypes.class);
            if (annotation != null) {
                return annotation;
            }
        }
        
        return null;
    }
}


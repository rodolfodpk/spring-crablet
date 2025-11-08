package com.crablet.command.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.integration.AbstractCrabletTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base class for command module integration tests.
 * Extends AbstractCrabletTest to get Testcontainers setup and provides CommandExecutor.
 */
@SpringBootTest(
    classes = com.crablet.command.integration.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.profiles.active=test"
)
public abstract class AbstractCommandTest extends AbstractCrabletTest {
    
    @Autowired
    protected CommandExecutor commandExecutor;
}


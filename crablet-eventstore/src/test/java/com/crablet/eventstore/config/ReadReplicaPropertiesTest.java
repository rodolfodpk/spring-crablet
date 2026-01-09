package com.crablet.eventstore.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for ReadReplicaProperties configuration binding.
 */
class ReadReplicaPropertiesTest {
    
    @Test
    void testDefaultValues() {
        ReadReplicaProperties properties = new ReadReplicaProperties();
        
        assertFalse(properties.isEnabled(), "Default should be disabled");
        assertNull(properties.getUrl(), "URL should be null by default");
        assertNotNull(properties.getHikari(), "Hikari properties should be initialized");
    }
    
    @Test
    void testEnabledGetterSetter() {
        ReadReplicaProperties properties = new ReadReplicaProperties();
        
        properties.setEnabled(true);
        assertTrue(properties.isEnabled());
        
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
    }
    
    @Test
    void testUrlGetterSetter() {
        ReadReplicaProperties properties = new ReadReplicaProperties();
        
        properties.setUrl("jdbc:postgresql://read-replica:5432/db");
        assertEquals("jdbc:postgresql://read-replica:5432/db", properties.getUrl());
        
        properties.setUrl(null);
        assertNull(properties.getUrl());
    }
    
    @Test
    void testHikariProperties() {
        ReadReplicaProperties properties = new ReadReplicaProperties();
        ReadReplicaProperties.HikariProperties hikari = properties.getHikari();
        
        assertNotNull(hikari);
        assertEquals(50, hikari.getMaximumPoolSize(), "Default max pool size should be 50");
        assertEquals(10, hikari.getMinimumIdle(), "Default min idle should be 10");
    }
    
    @Test
    void testHikariUsernameAndPassword() {
        ReadReplicaProperties properties = new ReadReplicaProperties();
        ReadReplicaProperties.HikariProperties hikari = properties.getHikari();
        
        hikari.setUsername("replica_user");
        assertEquals("replica_user", hikari.getUsername());
        
        hikari.setPassword("replica_pass");
        assertEquals("replica_pass", hikari.getPassword());
    }
    
    @Test
    void testHikariPoolSizeSetters() {
        ReadReplicaProperties properties = new ReadReplicaProperties();
        ReadReplicaProperties.HikariProperties hikari = properties.getHikari();
        
        hikari.setMaximumPoolSize(100);
        assertEquals(100, hikari.getMaximumPoolSize());
        
        hikari.setMinimumIdle(20);
        assertEquals(20, hikari.getMinimumIdle());
    }
    
    @Test
    void testSetterReplacesHikariObject() {
        ReadReplicaProperties properties = new ReadReplicaProperties();
        ReadReplicaProperties.HikariProperties customHikari = new ReadReplicaProperties.HikariProperties();
        customHikari.setMaximumPoolSize(200);
        
        properties.setHikari(customHikari);
        assertEquals(200, properties.getHikari().getMaximumPoolSize());
    }
}

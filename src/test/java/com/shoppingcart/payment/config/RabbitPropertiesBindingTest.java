package com.shoppingcart.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RabbitPropertiesBindingTest {

    @Test
    void keepsSpringAndCustomRabbitProperties() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application", new ClassPathResource("application.yml"));
        PropertySource<?> properties = sources.get(0);

        assertEquals("${RABBITMQ_HOST:rabbitmq.shopping-cart-data.svc.cluster.local}",
                properties.getProperty("spring.rabbitmq.host"));
        assertEquals("${RABBITMQ_PORT:5672}", properties.getProperty("spring.rabbitmq.port"));
        assertEquals("${RABBITMQ_VHOST:/}", properties.getProperty("spring.rabbitmq.virtual-host"));
        assertEquals("${RABBITMQ_USERNAME:guest}", properties.getProperty("spring.rabbitmq.username"));
        assertEquals("${RABBITMQ_PASSWORD:guest}", properties.getProperty("spring.rabbitmq.password"));
        assertNotNull(properties.getProperty("rabbitmq.host"));
        assertNotNull(properties.getProperty("rabbitmq.vault.enabled"));
    }
}

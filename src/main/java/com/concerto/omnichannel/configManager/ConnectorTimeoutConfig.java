package com.concerto.omnichannel.configManager;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "connector.timeouts")
public class ConnectorTimeoutConfig {

    private Map<String, Integer> timeouts;

    public Map<String, Integer> getTimeouts() {
        return timeouts;
    }

    public void setTimeouts(Map<String, Integer> timeouts) {
        this.timeouts = timeouts;
    }

    public int getTimeoutFor(String channel) {
        return timeouts.getOrDefault(channel, 3000); // default 3 sec
    }
}


package com.concerto.omnichannel.connector;

public interface Connector {
    String process(String payload) throws Exception;
    boolean supports(String channel);
    String getConnectorType();
}
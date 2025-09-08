package com.concerto.omnichannel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BBPSConfigurationService {

    @Value("${bbps.original-institution-id:OU01}")
    private String originalInstitutionId;

    @Value("${bbps.default-agent-institution-id:AI34}")
    private String defaultAgentInstitutionId;

    public String getOriginalInstitutionId() {
        return originalInstitutionId;
    }

    public String getDefaultAgentInstitutionId() {
        return defaultAgentInstitutionId;
    }
}

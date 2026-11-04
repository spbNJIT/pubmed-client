package com.imohealth.lifescience.axon.pubmed_client;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the NCBI E-utilities PubMed API.
 *
 * @param baseUrl E-utilities base URL, e.g. {@code https://eutils.ncbi.nlm.nih.gov/entrez/eutils}
 * @param apiKey  NCBI API key sent as {@code api_key}; required
 * @param search  ESearch defaults
 */
@ConfigurationProperties("pubmed")
public record PubMedProperties(String baseUrl, String apiKey, @DefaultValue Search search) {

    public PubMedProperties {
        // Fail at startup rather than sending api_key= on every request
        if (StringUtils.isBlank(apiKey)) {
            throw new IllegalArgumentException("pubmed.api-key must be set");
        }
    }

    /**
     * @param defaultRetmax number of PMIDs ESearch returns when the caller does not pass {@code retmax}
     */
    public record Search(@DefaultValue("10") int defaultRetmax) {
    }
}

package com.imohealth.lifescience.axon.pubmed_client.pmc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

/**
 * One converted identifier. Identifiers PMC does not know come back with {@code status: "error"} and no PMCID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdConverterRecord(
        Long pmid,
        String pmcid,
        String doi,
        String status,
        @JsonProperty("errmsg") String errorMessage) {

    public boolean isResolved() {
        return pmid != null && StringUtils.isNotBlank(pmcid) && !"error".equals(status);
    }
}

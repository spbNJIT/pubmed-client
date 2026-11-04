package com.imohealth.lifescience.axon.pubmed_client.pmc.model;

import org.apache.commons.lang3.StringUtils;

/**
 * A requested PMID and the PMCID the ID Converter mapped it to, or {@code null} when PMC has no record.
 */
public record ResolvedPmid(long pmid, String pmcid) {

    public boolean isResolved() {
        return StringUtils.isNotBlank(pmcid);
    }
}

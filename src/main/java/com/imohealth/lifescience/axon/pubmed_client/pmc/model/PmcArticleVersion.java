package com.imohealth.lifescience.axon.pubmed_client.pmc.model;

import org.apache.commons.lang3.StringUtils;

/**
 * An article version in the PMC bucket, e.g. PMCID {@code PMC10009416} version {@code 1} under prefix {@code PMC10009416.1/}.
 */
public record PmcArticleVersion(String pmcid, int version) {

    /**
     * Parses a bucket prefix such as {@code PMC10009416.1/}.
     */
    public static PmcArticleVersion fromPrefix(String prefix) {
        String key = StringUtils.removeEnd(prefix, "/");
        int separator = key.lastIndexOf('.');
        if (separator <= 0) {
            throw new IllegalArgumentException("Not an article version prefix: " + prefix);
        }
        return new PmcArticleVersion(key.substring(0, separator), Integer.parseInt(key.substring(separator + 1)));
    }

    /**
     * The version key used for prefixes and file names, e.g. {@code PMC10009416.1}.
     */
    public String key() {
        return pmcid + "." + version;
    }
}

package com.imohealth.lifescience.axon.pubmed_client.pmc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

/**
 * Per-version metadata JSON from the PMC bucket ({@code metadata/PMC<id>.<version>.json}).
 * {@code pdfUrl} is absent when the publisher license does not permit distributing the PDF.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PmcArticleMetadata(
        String pmcid,
        int version,
        Long pmid,
        String doi,
        String title,
        @JsonProperty("is_manuscript") boolean manuscript,
        @JsonProperty("is_retracted") boolean retracted,
        @JsonProperty("license_code") String licenseCode,
        @JsonProperty("pdf_url") String pdfUrl) {

    public boolean hasPdf() {
        return StringUtils.isNotBlank(pdfUrl);
    }

    public String key() {
        return pmcid + "." + version;
    }
}

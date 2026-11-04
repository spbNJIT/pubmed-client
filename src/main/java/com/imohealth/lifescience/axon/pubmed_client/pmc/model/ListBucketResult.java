package com.imohealth.lifescience.axon.pubmed_client.pmc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * S3 ListObjectsV2 response, limited to the fields needed to discover article-version prefixes.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListBucketResult {

    @JacksonXmlProperty(localName = "IsTruncated")
    private boolean truncated;

    @JacksonXmlProperty(localName = "NextContinuationToken")
    private String nextContinuationToken;

    // S3 repeats <CommonPrefixes> once per prefix without a wrapping element
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "CommonPrefixes")
    private List<CommonPrefix> commonPrefixes = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommonPrefix {

        @JacksonXmlProperty(localName = "Prefix")
        private String prefix;
    }
}

package com.imohealth.lifescience.axon.pubmed_client.pmc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response of the PMC ID Converter API ({@code /tools/idconv/api/v1/articles/?format=json}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdConverterResponse(String status, List<IdConverterRecord> records) {

    public List<IdConverterRecord> records() {
        return records != null ? records : List.of();
    }
}

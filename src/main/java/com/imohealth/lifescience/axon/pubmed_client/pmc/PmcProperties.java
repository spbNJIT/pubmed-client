package com.imohealth.lifescience.axon.pubmed_client.pmc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the PubMed Central (PMC) Article Datasets bucket and the PMC ID Converter API.
 *
 * @param bucketUrl            HTTPS base URL of the public {@code pmc-oa-opendata} S3 bucket
 * @param idConverterUrl       PMC ID Converter API endpoint used to map PMIDs to PMCIDs
 * @param tool                 tool name NCBI asks ID Converter callers to send
 * @param email                contact email NCBI asks ID Converter callers to send; omitted when blank
 * @param idConverterBatchSize maximum IDs per ID Converter call (the API accepts up to 200)
 * @param maxPmids             maximum distinct PMIDs accepted per PDF download request
 * @param connectTimeout       HTTP connect timeout for PMC calls
 * @param readTimeout          HTTP read timeout for PMC calls
 */
@ConfigurationProperties("pmc")
public record PmcProperties(
        String bucketUrl,
        String idConverterUrl,
        String tool,
        String email,
        int idConverterBatchSize,
        int maxPmids,
        Duration connectTimeout,
        Duration readTimeout) {
}

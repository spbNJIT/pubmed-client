package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.IdConverterRecord;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.IdConverterResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps PubMed IDs to PubMed Central IDs with the PMC ID Converter API. The PMC bucket is keyed by PMCID only.
 */
@Slf4j
public class PmcIdConverterClient {

    private final RestClient restClient;
    private final PmcProperties properties;
    private final ObjectMapper jsonMapper = PmcMappers.jsonMapper();

    public PmcIdConverterClient(RestClient restClient, PmcProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * Resolves PMIDs in batches of {@code pmc.id-converter-batch-size}.
     *
     * @return PMCID by PMID; PMIDs PMC has no record for are absent
     */
    public Map<Long, String> resolve(List<Long> pmids) {
        Map<Long, String> pmcidsByPmid = new HashMap<>();
        int batchSize = properties.idConverterBatchSize();
        for (int start = 0; start < pmids.size(); start += batchSize) {
            List<Long> batch = pmids.subList(start, Math.min(start + batchSize, pmids.size()));
            for (IdConverterRecord idRecord : convert(batch).records()) {
                if (idRecord.isResolved()) {
                    pmcidsByPmid.put(idRecord.pmid(), idRecord.pmcid());
                }
            }
        }
        log.info("Resolved {} of {} PMIDs to PMCIDs", pmcidsByPmid.size(), pmids.size());
        return pmcidsByPmid;
    }

    private IdConverterResponse convert(List<Long> batch) {
        String ids = batch.stream().map(String::valueOf).collect(Collectors.joining(","));
        String json = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.queryParam("ids", "{ids}")
                            .queryParam("format", "json")
                            .queryParam("tool", "{tool}");
                    // NCBI asks callers to identify themselves, but the API still answers without an email
                    if (StringUtils.isNotBlank(properties.email())) {
                        uriBuilder.queryParam("email", "{email}");
                    }
                    return uriBuilder.build(Map.of(
                            "ids", ids,
                            "tool", properties.tool(),
                            "email", StringUtils.defaultString(properties.email())));
                })
                .retrieve()
                .body(String.class);
        try {
            IdConverterResponse response = jsonMapper.readValue(json, IdConverterResponse.class);
            if (!"ok".equals(response.status())) {
                throw new IllegalStateException("PMC ID Converter returned status " + response.status());
            }
            return response;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse PMC ID Converter response", exception);
        }
    }
}

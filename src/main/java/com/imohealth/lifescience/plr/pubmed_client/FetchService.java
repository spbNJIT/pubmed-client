package com.imohealth.lifescience.plr.pubmed_client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.imohealth.lifescience.plr.pubmed_client.model.PubMedArticle;
import com.imohealth.lifescience.plr.pubmed_client.model.PubMedArticleSet;
import com.imohealth.lifescience.plr.pubmed_client.model.PubMedSummaryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.xml.stream.XMLInputFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@Service
public class FetchService {

    @Value("${pubmed.api-key}") String pubMedApiKey;
    @Value("${pubmed.base-url}") String pubMedBaseUrl;

    private final XmlMapper xmlMapper;
    private final ObjectMapper jsonMapper;

    public FetchService() {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        this.xmlMapper = XmlMapper.builder(new XmlFactory(inputFactory))
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .build();
        this.jsonMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<PubMedArticle> fetchArticles(List<Long> pmids) {
        String ids = pmids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
        log.info("Fetching articles for PMIDs: {}", ids);

        var client = RestClient.builder()
                .baseUrl(pubMedBaseUrl + "/efetch.fcgi")
                .build();

        String xml = client.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("db", "pubmed")
                        .queryParam("id", ids)
                        .queryParam("retmode", "xml")
                        .queryParam("api_key", pubMedApiKey)
                        .build())
                .retrieve()
                .body(String.class);

        log.info("Received efetch response, parsing XML:\n{}", xml);
        try {
            PubMedArticleSet articleSet = xmlMapper.readValue(xml, PubMedArticleSet.class);
            return articleSet.getPubmedArticles() != null ? articleSet.getPubmedArticles() : List.of();
        } catch (Exception e) {
            log.error("Failed to parse efetch XML response", e);
            throw new RuntimeException("Failed to parse PubMed efetch response", e);
        }
    }

    public PubMedSummaryResult fetchSummaries(List<Long> pmids) {
        String ids = pmids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
        log.info("Fetching summaries for PMIDs: {}", ids);

        var client = RestClient.builder()
                .baseUrl(pubMedBaseUrl + "/esummary.fcgi")
                .defaultHeader(HttpHeaders.ACCEPT, APPLICATION_JSON_VALUE)
                .build();

        String json = client.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("db", "pubmed")
                        .queryParam("id", ids)
                        .queryParam("retmode", "json")
                        .queryParam("api_key", pubMedApiKey)
                        .build())
                .retrieve()
                .body(String.class);

        log.info("Received esummary response, parsing JSON");
        try {
            return jsonMapper.readValue(json, PubMedSummaryResult.class);
        } catch (Exception e) {
            log.error("Failed to parse esummary JSON response", e);
            throw new RuntimeException("Failed to parse PubMed esummary response", e);
        }
    }

    @SuppressWarnings("unchecked")
    public String fetchHistoryDatesAsCsv(List<Long> pmids) {
        PubMedSummaryResult summaryResult = fetchSummaries(pmids);
        var sb = new StringBuilder();
        for (var docSum : summaryResult.getResult().getDocSums()) {
            String uid = docSum.getUid();
            String pubmedDate = extractPubmedHistoryDate(docSum.getProperties());
            sb.append(uid).append(",").append(pubmedDate != null ? pubmedDate : "").append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractPubmedHistoryDate(Map<String, Object> properties) {
        Object history = properties.get("history");
        if (history instanceof List<?> historyList) {
            for (Object entry : historyList) {
                if (entry instanceof Map<?, ?> historyEntry) {
                    Object pubstatus = historyEntry.get("pubstatus");
                    if ("pubmed".equals(pubstatus)) {
                        Object date = historyEntry.get("date");
                        if (date == null) return null;
                        String dateStr = date.toString();
                        int spaceIndex = dateStr.indexOf(' ');
                        return spaceIndex > 0 ? dateStr.substring(0, spaceIndex) : dateStr;
                    }
                }
            }
        }
        return null;
    }
}

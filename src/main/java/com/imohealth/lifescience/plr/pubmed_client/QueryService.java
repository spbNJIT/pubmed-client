package com.imohealth.lifescience.plr.pubmed_client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.nio.file.FileSystems;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryService {

    @Value("${pubmed.api-key}") String pubMedApiKey;

    public ResponseEntity<String> callPubMedSearch(String queryTerm) {
        logKeystoreLocation();
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("retmode", "json");
        queryParams.add("api_key", pubMedApiKey);
        queryParams.add("usehistory", "y");
        queryParams.add("retstart", "0");
        queryParams.add("retmax", "10");
        queryParams.add("db", "pubmed");
        log.info("sending parameters: {}", queryParams);
        var client = RestClient.builder()
                .baseUrl("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
                .defaultHeader(HttpHeaders.HOST,"www.ncbi.nlm.nih.gov")
                .build();
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("term", queryTerm);
        log.info("formData:\n{}", formData);
        try {
            return client.post()
                    .uri(uriBuilder -> uriBuilder.queryParams(queryParams).build())
                    .body(formData)
                    .retrieve()
                    .toEntity(String.class);
        } catch (Exception e) {
            log.error("Exception occurred while executing query ", e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.FAILED_DEPENDENCY);
        }
    }

    void logKeystoreLocation() {
        String javaHome = System.getProperty("java.home");
        String separator = FileSystems.getDefault().getSeparator();

        String cacertsPath = javaHome + separator + "lib" + separator
                + "security" + separator + "cacerts";

        log.info("Java Home: {}", javaHome);
        log.info("Expected cacerts location: {}", cacertsPath);

        File cacertsFile = new File(cacertsPath);
        if (cacertsFile.exists()) {
            log.info("Cacerts file exists: YES");
            log.info("Absolute path: {}", cacertsFile.getAbsolutePath());
        }

        String customTrustStore = System.getProperty("javax.net.ssl.trustStore");
        if (customTrustStore != null) {
            log.info("Custom trustStore is specified: {}", customTrustStore);
        } else {
            log.info("No custom trustStore specified, using default");
        }

        String userHome = System.getProperty("user.home");
        String userKeystore = userHome + separator + ".keystore";
        log.info("User keystore location: {}", userKeystore);
    }
}

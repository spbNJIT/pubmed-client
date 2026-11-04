package com.imohealth.lifescience.axon.pubmed_client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.File;
import java.nio.file.FileSystems;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryService {

    // PubMed ESearch only returns the first 9,999 records of a query; retstart above 9998 is an NCBI error
    static final int ESEARCH_MAX_RECORDS = 9_999;

    static final String API_KEY_MASK = "****";

    private final PubMedProperties properties;
    private final RestClient.Builder restClientBuilder;

    /**
     * Runs an ESearch query and returns one page of matching PMIDs.
     *
     * @param retstart index of the first PMID to return (0-based)
     * @param retmax   number of PMIDs to return, or {@code null} for {@code pubmed.search.default-retmax}
     */
    public ResponseEntity<String> callPubMedSearch(String queryTerm, int retstart, Integer retmax) {
        logKeystoreLocation();
        var queryParams = searchParams(retstart, retmax != null ? retmax : properties.search().defaultRetmax());
        log.info("sending parameters: {}", queryParams);
        var client = restClientBuilder.clone()
                .baseUrl(properties.baseUrl() + "/esearch.fcgi")
                .defaultHeader(HttpHeaders.ACCEPT, APPLICATION_JSON_VALUE)
                .build();
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("term", queryTerm);
        log.info("formData:\n{}", formData);
        // Sent in the POST body, after logging, so the key never appears in logs or in request URLs
        // (Spring's I/O error messages include the full URL)
        formData.add("api_key", properties.apiKey());
        try {
            return client.post()
                    .uri(uriBuilder -> uriBuilder.queryParams(queryParams).build())
                    .body(formData)
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientResponseException exception) {
            // The message includes NCBI's response body, which echoes the key when NCBI rejects it
            log.error("ESearch returned an error: {}", maskApiKey(exception.getMessage()));
            return new ResponseEntity<>(maskApiKey(exception.getMessage()), HttpStatus.FAILED_DEPENDENCY);
        } catch (Exception exception) {
            log.error("Exception occurred while executing query ", exception);
            return new ResponseEntity<>(maskApiKey(exception.getMessage()), HttpStatus.FAILED_DEPENDENCY);
        }
    }

    private MultiValueMap<String, String> searchParams(int retstart, int retmax) {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("retmode", "json");
        queryParams.add("usehistory", "y");
        queryParams.add("retstart", String.valueOf(retstart));
        queryParams.add("retmax", String.valueOf(retmax));
        queryParams.add("db", "pubmed");
        return queryParams;
    }

    /**
     * Hides the API key in text returned to callers - NCBI echoes the key back when it rejects one.
     */
    String maskApiKey(String text) {
        return StringUtils.replace(text, properties.apiKey(), API_KEY_MASK);
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

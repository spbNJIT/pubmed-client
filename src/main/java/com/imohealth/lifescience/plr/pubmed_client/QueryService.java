package com.imohealth.lifescience.plr.pubmed_client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueryService {

    public ResponseEntity<String> callPubMedSearch(String queryTerm) {
        logKeystoreLocation();
        var client = RestClient.builder()
                .baseUrl("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi")
                .defaultUriVariables(
                        Map.of("retmode","json",
                                "api_key","720f9b6f598f09919b4ec905b2482be71008",
                                "usehistory","y",
                                "retstart",0,
                                "retmax",10,
                                "db","pubmed"
                        ))
                .build();
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        headers.set(HttpHeaders.HOST, "www.ncbi.nlm.nih.gov");
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
        map.add("term", queryTerm);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        try {
            return client.post().body(request).retrieve().toEntity(String.class);
        } catch (Exception e) {
            log.error("Exception occurred while executing query ", e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.FAILED_DEPENDENCY);
        }
    }

    void logKeystoreLocation() {
        String javaHome = System.getProperty("java.home");
        String separator = System.getProperty("file.separator");

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

package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.ListBucketResult;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PmcArticleMetadata;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PmcArticleVersion;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Anonymous HTTPS access to the PMC Article Datasets bucket ({@code pmc-oa-opendata}).
 * See https://pmc-oa-opendata.s3.amazonaws.com/README.txt for the bucket layout.
 */
@Slf4j
public class PmcOpenDataClient {

    // pdf_url values in the metadata JSON use the s3:// form, e.g. s3://pmc-oa-opendata/PMC1.1/PMC1.1.pdf?md5=...
    static final String S3_URL_PREFIX = "s3://pmc-oa-opendata/";

    // Only article-version PDFs may be fetched, so a tampered pdf_url cannot point the client elsewhere
    private static final Pattern PDF_OBJECT_KEY = Pattern.compile("PMC\\d+\\.\\d+/[A-Za-z0-9._-]+\\.pdf");

    private final RestClient restClient;
    private final PmcProperties properties;
    private final XmlMapper xmlMapper = PmcMappers.xmlMapper();
    private final ObjectMapper jsonMapper = PmcMappers.jsonMapper();

    public PmcOpenDataClient(RestClient restClient, PmcProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * Lists the versions stored for a PMCID, lowest first. Versions are not contiguous - {@code PMC1.2} can exist
     * without {@code PMC1.1} - so they are discovered from the bucket listing rather than guessed.
     */
    public List<PmcArticleVersion> listVersions(String pmcid) {
        // The trailing dot keeps PMC123. from also matching PMC1234.
        String prefix = pmcid + ".";
        List<PmcArticleVersion> versions = new ArrayList<>();
        String continuationToken = null;
        do {
            ListBucketResult page = listPage(prefix, continuationToken);
            page.getCommonPrefixes().stream()
                    .map(commonPrefix -> PmcArticleVersion.fromPrefix(commonPrefix.getPrefix()))
                    .forEach(versions::add);
            continuationToken = page.isTruncated() ? page.getNextContinuationToken() : null;
        } while (StringUtils.isNotEmpty(continuationToken));
        versions.sort(Comparator.comparingInt(PmcArticleVersion::version));
        return versions;
    }

    /**
     * Reads the metadata JSON for an article version, or empty when the bucket has none.
     */
    public Optional<PmcArticleMetadata> getMetadata(PmcArticleVersion version) {
        String json;
        try {
            json = restClient.get()
                    .uri("/metadata/{key}.json", version.key())
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound exception) {
            log.warn("No metadata found for {}", version.key());
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(json, PmcArticleMetadata.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse PMC metadata for " + version.key(), exception);
        }
    }

    /**
     * Streams the PDF at {@code pdfUrl} (s3:// or bucket HTTPS form) to {@code target} without buffering it in memory.
     *
     * @throws IllegalArgumentException if the URL is not an article PDF in the configured bucket
     */
    public void downloadTo(String pdfUrl, Path target) {
        String key = toPdfObjectKey(pdfUrl);
        restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/" + key).build())
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("HTTP " + response.getStatusCode().value() + " downloading " + key);
                    }
                    try (InputStream body = response.getBody()) {
                        Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return target;
                });
    }

    String toPdfObjectKey(String pdfUrl) {
        // Drop the ?md5=... parameter - it is a checksum hint, not part of the object key
        String url = StringUtils.substringBefore(pdfUrl, "?");
        String bucketUrlPrefix = StringUtils.appendIfMissing(properties.bucketUrl(), "/");
        String key;
        if (StringUtils.startsWith(url, S3_URL_PREFIX)) {
            key = StringUtils.removeStart(url, S3_URL_PREFIX);
        } else if (StringUtils.startsWith(url, bucketUrlPrefix)) {
            key = StringUtils.removeStart(url, bucketUrlPrefix);
        } else {
            throw new IllegalArgumentException("PDF URL is not in the PMC bucket: " + pdfUrl);
        }
        if (!PDF_OBJECT_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Unexpected PMC PDF object key: " + key);
        }
        return key;
    }

    private ListBucketResult listPage(String prefix, String continuationToken) {
        String xml = restClient.get()
                .uri(uriBuilder -> {
                    // delimiter=/ collapses each version's files into one CommonPrefixes entry
                    uriBuilder.path("/")
                            .queryParam("list-type", "2")
                            .queryParam("prefix", "{prefix}")
                            .queryParam("delimiter", "/");
                    if (StringUtils.isNotEmpty(continuationToken)) {
                        // Passed as a URI variable so it is fully encoded - S3 tokens contain '+', '/' and '='
                        uriBuilder.queryParam("continuation-token", "{token}");
                    }
                    return uriBuilder.build(Map.of(
                            "prefix", prefix,
                            "token", StringUtils.defaultString(continuationToken)));
                })
                .retrieve()
                .body(String.class);
        try {
            return xmlMapper.readValue(xml, ListBucketResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse PMC bucket listing for " + prefix, exception);
        }
    }
}

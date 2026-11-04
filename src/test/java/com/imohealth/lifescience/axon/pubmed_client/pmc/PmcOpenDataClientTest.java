package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PmcArticleMetadata;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PmcArticleVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PmcOpenDataClientTest {

    private static final String BUCKET_URL = "https://bucket.test";
    private static final String LIST_URL = BUCKET_URL + "/?list-type=2&prefix=PMC111.&delimiter=/";

    private MockRestServiceServer server;
    private PmcOpenDataClient client;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BUCKET_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new PmcProperties(BUCKET_URL, "https://idconv.test/articles/", "pubmed-client", "",
                200, 500, Duration.ofSeconds(1), Duration.ofSeconds(1));
        client = new PmcOpenDataClient(builder.build(), properties);
    }

    private static String listing(boolean truncated, String nextToken, String... prefixes) {
        var xml = new StringBuilder("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">")
                .append("<IsTruncated>").append(truncated).append("</IsTruncated>");
        for (String prefix : prefixes) {
            xml.append("<CommonPrefixes><Prefix>").append(prefix).append("</Prefix></CommonPrefixes>");
        }
        if (nextToken != null) {
            xml.append("<NextContinuationToken>").append(nextToken).append("</NextContinuationToken>");
        }
        return xml.append("</ListBucketResult>").toString();
    }

    @Test
    void listsNonContiguousVersionsInOrder() {
        server.expect(requestTo(LIST_URL))
                .andRespond(withSuccess(listing(false, null, "PMC111.3/", "PMC111.2/"), MediaType.APPLICATION_XML));

        List<PmcArticleVersion> versions = client.listVersions("PMC111");

        assertThat(versions).containsExactly(new PmcArticleVersion("PMC111", 2), new PmcArticleVersion("PMC111", 3));
        server.verify();
    }

    @Test
    void followsContinuationTokens() {
        server.expect(requestTo(LIST_URL))
                .andRespond(withSuccess(listing(true, "a+b/c=", "PMC111.1/"), MediaType.APPLICATION_XML));
        server.expect(requestTo(LIST_URL + "&continuation-token=a%2Bb%2Fc%3D"))
                .andRespond(withSuccess(listing(false, null, "PMC111.2/"), MediaType.APPLICATION_XML));

        List<PmcArticleVersion> versions = client.listVersions("PMC111");

        assertThat(versions).extracting(PmcArticleVersion::version).containsExactly(1, 2);
        server.verify();
    }

    @Test
    void returnsNoVersionsWhenPmcidIsNotInBucket() {
        server.expect(requestTo(LIST_URL))
                .andRespond(withSuccess(listing(false, null), MediaType.APPLICATION_XML));

        assertThat(client.listVersions("PMC111")).isEmpty();
    }

    @Test
    void readsVersionMetadata() {
        server.expect(requestTo(BUCKET_URL + "/metadata/PMC111.2.json"))
                .andRespond(withSuccess("""
                        {"pmcid":"PMC111","version":2,"pmid":42,"is_manuscript":false,"is_retracted":false,
                         "license_code":"CC BY","pdf_url":"s3://pmc-oa-opendata/PMC111.2/PMC111.2.pdf?md5=abc"}
                        """, MediaType.APPLICATION_JSON));

        Optional<PmcArticleMetadata> metadata = client.getMetadata(new PmcArticleVersion("PMC111", 2));

        assertThat(metadata).hasValueSatisfying(value -> {
            assertThat(value.pmid()).isEqualTo(42L);
            assertThat(value.hasPdf()).isTrue();
        });
    }

    @Test
    void missingMetadataIsEmpty() {
        server.expect(requestTo(BUCKET_URL + "/metadata/PMC111.2.json"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getMetadata(new PmcArticleVersion("PMC111", 2))).isEmpty();
    }

    @Test
    void downloadsS3PdfUrlOverHttps() throws Exception {
        server.expect(requestTo(BUCKET_URL + "/PMC10009416.1/PMC10009416.1.pdf"))
                .andRespond(withSuccess("%PDF-1.7 test", MediaType.APPLICATION_PDF));
        Path target = tempDir.resolve("article.pdf");

        client.downloadTo("s3://pmc-oa-opendata/PMC10009416.1/PMC10009416.1.pdf?md5=648db0ce6ce56688995af0450090db28",
                target);

        assertThat(Files.readString(target)).isEqualTo("%PDF-1.7 test");
        server.verify();
    }

    @Test
    void acceptsHttpsBucketUrls() {
        assertThat(client.toPdfObjectKey(BUCKET_URL + "/PMC1.2/PMC1.2.pdf")).isEqualTo("PMC1.2/PMC1.2.pdf");
    }

    @Test
    void rejectsPdfUrlOnAnotherHost() {
        server.expect(never(), anything());

        assertThatThrownBy(() -> client.downloadTo("https://evil.test/PMC1.1/PMC1.1.pdf", tempDir.resolve("a.pdf")))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void rejectsKeysOutsideArticleVersions() {
        assertThatThrownBy(() -> client.toPdfObjectKey("s3://pmc-oa-opendata/../secret.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.toPdfObjectKey("s3://pmc-oa-opendata/inventory-reports/report.csv"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsOnErrorStatus() {
        server.expect(requestTo(BUCKET_URL + "/PMC1.1/PMC1.1.pdf"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.downloadTo("s3://pmc-oa-opendata/PMC1.1/PMC1.1.pdf", tempDir.resolve("a.pdf")))
                .hasMessageContaining("HTTP 500");
    }
}

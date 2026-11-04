package com.imohealth.lifescience.axon.pubmed_client.pmc.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PmcModelTest {

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final XmlMapper xmlMapper = XmlMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .build();

    @Test
    void parsesIdConverterResponseWithErrorRecord() throws Exception {
        String json = """
                {"status":"ok","records":[
                  {"doi":"10.1371/journal.pone.0280342","pmcid":"PMC10004557","pmid":36897886,"requested-id":"36897886"},
                  {"pmid":12345,"requested-id":"12345","status":"error","errmsg":"Identifier not found in PMC"}]}
                """;

        IdConverterResponse response = jsonMapper.readValue(json, IdConverterResponse.class);

        assertThat(response.records()).hasSize(2);
        assertThat(response.records().get(0).isResolved()).isTrue();
        assertThat(response.records().get(0).pmcid()).isEqualTo("PMC10004557");
        assertThat(response.records().get(1).isResolved()).isFalse();
        assertThat(response.records().get(1).errorMessage()).isEqualTo("Identifier not found in PMC");
    }

    @Test
    void idConverterResponseWithoutRecordsIsEmpty() throws Exception {
        IdConverterResponse response = jsonMapper.readValue("{\"status\":\"ok\"}", IdConverterResponse.class);

        assertThat(response.records()).isEmpty();
    }

    @Test
    void parsesArticleMetadata() throws Exception {
        String json = """
                {"pmcid": "PMC10009416", "version": 1, "pmid": 36537061, "doi": "10.1002/npr2.12307",
                 "title": "Risk factors", "is_pmc_openaccess": true, "is_manuscript": false,
                 "is_historical_ocr": false, "is_retracted": false, "license_code": "CC BY",
                 "pdf_url": "s3://pmc-oa-opendata/PMC10009416.1/PMC10009416.1.pdf?md5=648db0ce6ce56688995af0450090db28",
                 "media_urls": []}
                """;

        PmcArticleMetadata metadata = jsonMapper.readValue(json, PmcArticleMetadata.class);

        assertThat(metadata.pmcid()).isEqualTo("PMC10009416");
        assertThat(metadata.version()).isEqualTo(1);
        assertThat(metadata.pmid()).isEqualTo(36537061L);
        assertThat(metadata.manuscript()).isFalse();
        assertThat(metadata.retracted()).isFalse();
        assertThat(metadata.licenseCode()).isEqualTo("CC BY");
        assertThat(metadata.hasPdf()).isTrue();
        assertThat(metadata.key()).isEqualTo("PMC10009416.1");
    }

    @Test
    void metadataWithoutPdfUrlHasNoPdf() throws Exception {
        PmcArticleMetadata metadata = jsonMapper.readValue(
                "{\"pmcid\":\"PMC1\",\"version\":2,\"is_manuscript\":true}", PmcArticleMetadata.class);

        assertThat(metadata.hasPdf()).isFalse();
        assertThat(metadata.manuscript()).isTrue();
    }

    @Test
    void parsesNamespacedListBucketResult() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <Name>pmc-oa-opendata</Name><Prefix>PMC11370360.</Prefix><KeyCount>2</KeyCount>
                  <MaxKeys>1000</MaxKeys><Delimiter>/</Delimiter><IsTruncated>true</IsTruncated>
                  <CommonPrefixes><Prefix>PMC11370360.1/</Prefix></CommonPrefixes>
                  <CommonPrefixes><Prefix>PMC11370360.2/</Prefix></CommonPrefixes>
                  <NextContinuationToken>abc+/=</NextContinuationToken>
                </ListBucketResult>
                """;

        ListBucketResult result = xmlMapper.readValue(xml, ListBucketResult.class);

        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getNextContinuationToken()).isEqualTo("abc+/=");
        assertThat(result.getCommonPrefixes())
                .extracting(ListBucketResult.CommonPrefix::getPrefix)
                .containsExactly("PMC11370360.1/", "PMC11370360.2/");
    }

    @Test
    void parsesListBucketResultWithSinglePrefix() throws Exception {
        String xml = """
                <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <IsTruncated>false</IsTruncated>
                  <CommonPrefixes><Prefix>PMC1.1/</Prefix></CommonPrefixes>
                </ListBucketResult>
                """;

        ListBucketResult result = xmlMapper.readValue(xml, ListBucketResult.class);

        assertThat(result.getCommonPrefixes()).extracting(ListBucketResult.CommonPrefix::getPrefix)
                .containsExactly("PMC1.1/");
    }

    @Test
    void parsesListBucketResultWithoutPrefixes() throws Exception {
        String xml = """
                <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <KeyCount>0</KeyCount><IsTruncated>false</IsTruncated>
                </ListBucketResult>
                """;

        ListBucketResult result = xmlMapper.readValue(xml, ListBucketResult.class);

        assertThat(result.getCommonPrefixes()).isEmpty();
    }

    @Test
    void parsesArticleVersionPrefix() {
        PmcArticleVersion version = PmcArticleVersion.fromPrefix("PMC10009416.12/");

        assertThat(version.pmcid()).isEqualTo("PMC10009416");
        assertThat(version.version()).isEqualTo(12);
        assertThat(version.key()).isEqualTo("PMC10009416.12");
    }

    @Test
    void rejectsPrefixWithoutVersion() {
        assertThatThrownBy(() -> PmcArticleVersion.fromPrefix("metadata/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manifestRowForDownloadedPdf() {
        var metadata = new PmcArticleMetadata("PMC10009416", 1, 36537061L, null, null, false, true, "CC BY", "s3://x");

        PdfManifestEntry entry = PdfManifestEntry.downloaded(36537061L, metadata);

        assertThat(entry.fileName()).isEqualTo("36537061_PMC10009416.1.pdf");
        assertThat(entry.toCsvRow())
                .isEqualTo("36537061,PMC10009416,1,DOWNLOADED,36537061_PMC10009416.1.pdf,false,true,CC BY,");
    }

    @Test
    void manifestRowLeavesUnknownColumnsBlank() {
        PdfManifestEntry entry = PdfManifestEntry.unavailable(
                new ResolvedPmid(12345L, null), PdfStatus.NOT_IN_PMC, "PMID has no PMC record");

        assertThat(entry.toCsvRow()).isEqualTo("12345,,,NOT_IN_PMC,,,,,PMID has no PMC record");
    }

    @Test
    void manifestRowQuotesCsvUnsafeValues() {
        PdfManifestEntry entry = PdfManifestEntry.unavailable(
                new ResolvedPmid(1L, "PMC1"), PdfStatus.ERROR, "HTTP 500, body \"oops\"");

        assertThat(entry.toCsvRow()).isEqualTo("1,PMC1,,ERROR,,,,,\"HTTP 500, body \"\"oops\"\"\"");
    }
}

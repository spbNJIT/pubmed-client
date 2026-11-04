package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PmcArticleMetadata;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PmcArticleVersion;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.ResolvedPmid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmcPdfServiceTest {

    @Mock
    PmcIdConverterClient idConverterClient;

    @Mock
    PmcOpenDataClient openDataClient;

    @InjectMocks
    PmcPdfService service;

    private static PmcArticleMetadata metadata(String pmcid, int version, boolean manuscript, boolean hasPdf) {
        String pdfUrl = hasPdf ? "s3://pmc-oa-opendata/%s.%d/%s.%d.pdf".formatted(pmcid, version, pmcid, version) : null;
        return new PmcArticleMetadata(pmcid, version, null, null, null, manuscript, false, "CC BY", pdfUrl);
    }

    private void givenPdf(String pmcid, PmcArticleMetadata... versions) {
        when(openDataClient.listVersions(pmcid)).thenReturn(
                List.of(versions).stream().map(version -> new PmcArticleVersion(pmcid, version.version())).toList());
        for (PmcArticleMetadata version : versions) {
            when(openDataClient.getMetadata(new PmcArticleVersion(pmcid, version.version())))
                    .thenReturn(Optional.of(version));
        }
    }

    private void givenDownloadWrites(String pdfUrl, String content) {
        doAnswer(invocation -> Files.writeString(invocation.getArgument(1, Path.class), content))
                .when(openDataClient).downloadTo(eq(pdfUrl), any(Path.class));
    }

    private static Map<String, String> unzip(byte[] archive) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private Map<String, String> writeArchive(ResolvedPmid... articles) throws IOException {
        var output = new ByteArrayOutputStream();
        service.writePdfArchive(List.of(articles), output);
        return unzip(output.toByteArray());
    }

    @Test
    void selectsHighestPublishedVersionWithPdf() {
        var selected = PmcPdfService.selectPdfVersion(List.of(
                metadata("PMC1", 1, true, true),
                metadata("PMC1", 2, false, true),
                metadata("PMC1", 3, false, false)));

        assertThat(selected).map(PmcArticleMetadata::version).contains(2);
    }

    @Test
    void fallsBackToManuscriptWhenPublishedVersionHasNoPdf() {
        var selected = PmcPdfService.selectPdfVersion(List.of(
                metadata("PMC1", 1, true, true),
                metadata("PMC1", 2, false, false)));

        assertThat(selected).map(PmcArticleMetadata::version).contains(1);
    }

    @Test
    void selectsNothingWhenNoVersionHasPdf() {
        assertThat(PmcPdfService.selectPdfVersion(List.of(metadata("PMC1", 1, false, false)))).isEmpty();
    }

    @Test
    void resolveKeepsRequestOrderAndMarksUnknownPmids() {
        when(idConverterClient.resolve(List.of(3L, 1L))).thenReturn(Map.of(1L, "PMC100"));

        assertThat(service.resolve(List.of(3L, 1L)))
                .containsExactly(new ResolvedPmid(3L, null), new ResolvedPmid(1L, "PMC100"));
    }

    @Test
    void writesPdfsManifestAndNotice() throws IOException {
        var published = metadata("PMC111", 2, false, true);
        givenPdf("PMC111", metadata("PMC111", 1, true, true), published);
        givenDownloadWrites(published.pdfUrl(), "%PDF published");

        Map<String, String> entries = writeArchive(new ResolvedPmid(42L, "PMC111"));

        assertThat(entries).containsOnlyKeys("42_PMC111.2.pdf", "manifest.csv", "NOTICE.txt");
        assertThat(entries.get("42_PMC111.2.pdf")).isEqualTo("%PDF published");
        assertThat(entries.get("manifest.csv")).isEqualTo("""
                pmid,pmcid,version,status,file_name,is_manuscript,is_retracted,license_code,message
                42,PMC111,2,DOWNLOADED,42_PMC111.2.pdf,false,false,CC BY,
                """);
        assertThat(entries.get("NOTICE.txt")).contains("National Library of Medicine")
                .contains("https://registry.opendata.aws/ncbi-pmc");
    }

    @Test
    void recordsEveryUnavailableOutcome() throws IOException {
        when(openDataClient.listVersions("PMC200")).thenReturn(List.of());
        givenPdf("PMC300", metadata("PMC300", 1, false, false));

        Map<String, String> entries = writeArchive(
                new ResolvedPmid(1L, null),
                new ResolvedPmid(2L, "PMC200"),
                new ResolvedPmid(3L, "PMC300"));

        assertThat(entries).containsOnlyKeys("manifest.csv", "NOTICE.txt");
        assertThat(entries.get("manifest.csv")).isEqualTo("""
                pmid,pmcid,version,status,file_name,is_manuscript,is_retracted,license_code,message
                1,,,NOT_IN_PMC,,,,,PMID has no PMC record
                2,PMC200,,NOT_IN_BUCKET,,,,,No article versions in the PMC bucket
                3,PMC300,,NO_PDF,,,,,No article version has a PDF
                """);
    }

    @Test
    void flagsRetractedArticlesButStillDownloadsThem() throws IOException {
        var retracted = new PmcArticleMetadata("PMC400", 1, 4L, null, null, false, true, "CC0",
                "s3://pmc-oa-opendata/PMC400.1/PMC400.1.pdf");
        givenPdf("PMC400", retracted);
        givenDownloadWrites(retracted.pdfUrl(), "%PDF retracted");

        Map<String, String> entries = writeArchive(new ResolvedPmid(4L, "PMC400"));

        assertThat(entries).containsKey("4_PMC400.1.pdf");
        assertThat(entries.get("manifest.csv")).contains("4,PMC400,1,DOWNLOADED,4_PMC400.1.pdf,false,true,CC0,");
    }

    @Test
    void isolatesOneFailedDownload() throws IOException {
        var failing = metadata("PMC500", 1, false, true);
        var working = metadata("PMC600", 1, false, true);
        givenPdf("PMC500", failing);
        givenPdf("PMC600", working);
        doThrow(new IllegalStateException("HTTP 500 downloading PMC500.1/PMC500.1.pdf"))
                .when(openDataClient).downloadTo(eq(failing.pdfUrl()), any(Path.class));
        givenDownloadWrites(working.pdfUrl(), "%PDF ok");

        Map<String, String> entries = writeArchive(new ResolvedPmid(5L, "PMC500"), new ResolvedPmid(6L, "PMC600"));

        assertThat(entries).containsOnlyKeys("6_PMC600.1.pdf", "manifest.csv", "NOTICE.txt");
        assertThat(entries.get("manifest.csv"))
                .contains("5,PMC500,,ERROR,,,,,HTTP 500 downloading PMC500.1/PMC500.1.pdf")
                .contains("6,PMC600,1,DOWNLOADED");
    }

    @Test
    void stopsWhenClientDisconnects() {
        var article = metadata("PMC700", 1, false, true);
        givenPdf("PMC700", article);
        givenDownloadWrites(article.pdfUrl(), "%PDF");
        OutputStream disconnected = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("Broken pipe");
            }
        };

        assertThatThrownBy(() -> service.writePdfArchive(List.of(new ResolvedPmid(7L, "PMC700")), disconnected))
                .isInstanceOf(IOException.class);
    }
}

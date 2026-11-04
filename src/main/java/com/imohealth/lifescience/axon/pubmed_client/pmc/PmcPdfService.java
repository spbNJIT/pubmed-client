package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PdfManifestEntry;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PdfStatus;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PmcArticleMetadata;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.PmcArticleVersion;
import com.imohealth.lifescience.axon.pubmed_client.pmc.model.ResolvedPmid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a ZIP of PubMed Central PDFs for a list of PubMed IDs, plus a manifest explaining every PMID's outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmcPdfService {

    static final String MANIFEST_ENTRY = "manifest.csv";
    static final String NOTICE_ENTRY = "NOTICE.txt";

    // NLM's terms require acknowledging NLM as the data source; see https://pmc-oa-opendata.s3.amazonaws.com/README.txt
    private static final String NOTICE = """
            NIH NLM NCBI PubMed Central (PMC) Article Datasets - Full-Text Biomedical and Life Sciences Journal \
            Articles on AWS was accessed on %s from https://registry.opendata.aws/ncbi-pmc.

            The articles are provided by the National Library of Medicine (NLM) and their respective publishers \
            or authors. NLM does not endorse this product or service.

            Reuse and redistribution of each article is subject to its license (license_code in manifest.csv) and \
            copyright statement. See https://pmc.ncbi.nlm.nih.gov/about/copyright/.
            """;

    private final PmcIdConverterClient idConverterClient;
    private final PmcOpenDataClient openDataClient;

    /**
     * Maps PMIDs to PMCIDs, keeping the request order. Called before streaming starts so an ID Converter failure
     * can still be reported with an HTTP error status.
     */
    public List<ResolvedPmid> resolve(List<Long> pmids) {
        Map<Long, String> pmcidsByPmid = idConverterClient.resolve(pmids);
        return pmids.stream()
                .map(pmid -> new ResolvedPmid(pmid, pmcidsByPmid.get(pmid)))
                .toList();
    }

    /**
     * Writes one PDF per resolved PMID (where available), then {@code manifest.csv} and {@code NOTICE.txt}.
     * Upstream failures are recorded per PMID; failures writing to {@code output} (e.g. the client disconnected)
     * propagate and stop the archive.
     */
    public void writePdfArchive(List<ResolvedPmid> articles, OutputStream output) throws IOException {
        var zip = new ZipOutputStream(output);
        List<PdfManifestEntry> manifest = new ArrayList<>();
        for (ResolvedPmid article : articles) {
            manifest.add(writeArticle(article, zip));
        }
        writeTextEntry(zip, MANIFEST_ENTRY, manifestCsv(manifest));
        writeTextEntry(zip, NOTICE_ENTRY, NOTICE.formatted(LocalDate.now(ZoneOffset.UTC)));
        // finish rather than close - the servlet container owns the response stream
        zip.finish();
        log.info("Wrote PDF archive: {} of {} PMIDs downloaded", manifest.stream()
                .filter(entry -> entry.status() == PdfStatus.DOWNLOADED).count(), manifest.size());
    }

    /**
     * Picks the version to download: the highest-numbered published version with a PDF, otherwise the
     * highest-numbered author manuscript with a PDF.
     */
    static Optional<PmcArticleMetadata> selectPdfVersion(List<PmcArticleMetadata> versions) {
        Comparator<PmcArticleMetadata> byVersion = Comparator.comparingInt(PmcArticleMetadata::version);
        List<PmcArticleMetadata> withPdf = versions.stream().filter(PmcArticleMetadata::hasPdf).toList();
        return withPdf.stream()
                .filter(metadata -> !metadata.manuscript())
                .max(byVersion)
                .or(() -> withPdf.stream().max(byVersion));
    }

    static String manifestCsv(List<PdfManifestEntry> manifest) {
        return manifest.stream()
                .map(PdfManifestEntry::toCsvRow)
                .collect(Collectors.joining("\n", PdfManifestEntry.CSV_HEADER + "\n", "\n"));
    }

    private PdfManifestEntry writeArticle(ResolvedPmid article, ZipOutputStream zip) throws IOException {
        if (!article.isResolved()) {
            return PdfManifestEntry.unavailable(article, PdfStatus.NOT_IN_PMC, "PMID has no PMC record");
        }
        // Spool to a temp file first so a download failing part-way never leaves a truncated ZIP entry
        Path pdfFile = Files.createTempFile("pmc-", ".pdf");
        try {
            PdfManifestEntry entry = downloadPdf(article, pdfFile);
            if (entry.status() == PdfStatus.DOWNLOADED) {
                zip.putNextEntry(new ZipEntry(entry.fileName()));
                Files.copy(pdfFile, zip);
                zip.closeEntry();
            }
            return entry;
        } finally {
            Files.deleteIfExists(pdfFile);
        }
    }

    private PdfManifestEntry downloadPdf(ResolvedPmid article, Path pdfFile) {
        try {
            List<PmcArticleVersion> versions = openDataClient.listVersions(article.pmcid());
            if (versions.isEmpty()) {
                return PdfManifestEntry.unavailable(article, PdfStatus.NOT_IN_BUCKET,
                        "No article versions in the PMC bucket");
            }
            List<PmcArticleMetadata> metadata = versions.stream()
                    .map(openDataClient::getMetadata)
                    .flatMap(Optional::stream)
                    .toList();
            Optional<PmcArticleMetadata> selected = selectPdfVersion(metadata);
            if (selected.isEmpty()) {
                return PdfManifestEntry.unavailable(article, PdfStatus.NO_PDF, "No article version has a PDF");
            }
            openDataClient.downloadTo(selected.get().pdfUrl(), pdfFile);
            return PdfManifestEntry.downloaded(article.pmid(), selected.get());
        } catch (RuntimeException exception) {
            log.warn("Failed to download PDF for PMID {} ({})", article.pmid(), article.pmcid(), exception);
            return PdfManifestEntry.unavailable(article, PdfStatus.ERROR,
                    StringUtils.defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName()));
        }
    }

    private static void writeTextEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

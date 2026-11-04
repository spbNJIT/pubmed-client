package com.imohealth.lifescience.axon.pubmed_client.pmc.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One row of the {@code manifest.csv} written into each PDF archive.
 */
public record PdfManifestEntry(
        long pmid,
        String pmcid,
        Integer version,
        PdfStatus status,
        String fileName,
        Boolean manuscript,
        Boolean retracted,
        String licenseCode,
        String message) {

    public static final String CSV_HEADER =
            "pmid,pmcid,version,status,file_name,is_manuscript,is_retracted,license_code,message";

    public static PdfManifestEntry downloaded(long pmid, PmcArticleMetadata metadata) {
        return new PdfManifestEntry(pmid, metadata.pmcid(), metadata.version(), PdfStatus.DOWNLOADED,
                fileName(pmid, metadata), metadata.manuscript(), metadata.retracted(), metadata.licenseCode(), null);
    }

    public static PdfManifestEntry unavailable(ResolvedPmid article, PdfStatus status, String message) {
        return new PdfManifestEntry(article.pmid(), article.pmcid(), null, status, null, null, null, null, message);
    }

    /**
     * ZIP entry name for a PDF, e.g. {@code 36537061_PMC10009416.1.pdf}.
     */
    public static String fileName(long pmid, PmcArticleMetadata metadata) {
        return pmid + "_" + metadata.key() + ".pdf";
    }

    public String toCsvRow() {
        return Stream.of(pmid, pmcid, version, status, fileName, manuscript, retracted, licenseCode, message)
                .map(value -> escapeCsv(Objects.toString(value, "")))
                .collect(Collectors.joining(","));
    }

    private static String escapeCsv(String value) {
        if (StringUtils.containsAny(value, ',', '"', '\n', '\r')) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}

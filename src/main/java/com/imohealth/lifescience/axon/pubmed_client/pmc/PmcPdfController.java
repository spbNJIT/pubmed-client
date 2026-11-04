package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.imohealth.lifescience.axon.pubmed_client.pmc.model.ResolvedPmid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PmcPdfController {

    static final String ARCHIVE_FILE_NAME = "pmc-pdfs.zip";
    private static final String APPLICATION_ZIP = "application/zip";

    private final PmcPdfService pmcPdfService;
    private final PmcProperties properties;

    /**
     * Streams a ZIP with the PubMed Central PDF of each PMID that has one, plus {@code manifest.csv} and
     * {@code NOTICE.txt}. PMIDs without a PDF are listed in the manifest with the reason.
     */
    @PostMapping(value = "/pmc/pdf", produces = APPLICATION_ZIP)
    ResponseEntity<StreamingResponseBody> downloadPdfs(@RequestBody List<Long> pmids) {
        List<Long> distinctPmids = ObjectUtils.isEmpty(pmids) ? List.of()
                : pmids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctPmids.isEmpty() || distinctPmids.size() > properties.maxPmids()) {
            return ResponseEntity.badRequest().build();
        }
        List<ResolvedPmid> articles;
        try {
            articles = pmcPdfService.resolve(distinctPmids);
        } catch (Exception exception) {
            log.error("Error resolving PMIDs to PMCIDs", exception);
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).build();
        }
        StreamingResponseBody archive = output -> pmcPdfService.writePdfArchive(articles, output);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(APPLICATION_ZIP))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ARCHIVE_FILE_NAME).build().toString())
                .body(archive);
    }
}

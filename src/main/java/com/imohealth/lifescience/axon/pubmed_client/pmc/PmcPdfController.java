package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.imohealth.lifescience.axon.pubmed_client.pmc.model.ResolvedPmid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @Operation(
            summary = "Download PMC PDFs",
            description = """
                    Resolves the PMIDs to PubMed Central IDs and streams a ZIP containing one PDF per PMID that has \
                    one (the latest published version, else the author manuscript), named \
                    `<pmid>_PMC<id>.<version>.pdf`. The ZIP always ends with `manifest.csv` - one row per distinct \
                    PMID with status DOWNLOADED, NOT_IN_PMC, NOT_IN_BUCKET, NO_PDF or ERROR - and `NOTICE.txt` \
                    (NLM source acknowledgement). Duplicate PMIDs are processed once.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PubMed IDs (at most `pmc.max-pmids` distinct IDs, default 500)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(type = "integer", format = "int64")),
                            examples = @ExampleObject(value = "[36537061, 36897886, 12345]"))),
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "ZIP archive, returned even when no PMID has a PDF",
                            content = @Content(mediaType = APPLICATION_ZIP,
                                    schema = @Schema(type = "string", format = "binary"))),
                    @ApiResponse(responseCode = "400",
                            description = "Body missing or empty, or more than `pmc.max-pmids` distinct PMIDs"),
                    @ApiResponse(responseCode = "424", description = "The PMC ID Converter API failed")
            })
    @Tag(name = "PMC", description = "Full-text downloads from the PubMed Central Article Datasets on AWS")
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

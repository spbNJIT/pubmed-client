package com.imohealth.lifescience.axon.pubmed_client;

import com.imohealth.lifescience.axon.pubmed_client.model.PubMedArticle;
import com.imohealth.lifescience.axon.pubmed_client.model.PubMedSummaryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@Tag(name = "PubMed", description = "NCBI E-utilities pass-through for PubMed")
public class FetchController {

    static final String PMID_LIST_EXAMPLE = "[36537061, 36897886]";

    private final FetchService fetchService;

    @Operation(
            summary = "Fetch PubMed articles",
            description = "Runs EFetch for the PMIDs and returns each article's MedlineCitation and PubmedData.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PubMed IDs",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(type = "integer", format = "int64")),
                            examples = @ExampleObject(value = PMID_LIST_EXAMPLE))),
            responses = @ApiResponse(responseCode = "200", description = "Articles, in EFetch order",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = PubMedArticle.class)))))
    @PostMapping("/pubmed/fetch")
    ResponseEntity<List<PubMedArticle>> fetchArticles(@RequestBody List<Long> pmids) {
        if (pmids == null || pmids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var articles = fetchService.fetchArticles(pmids);
            return ResponseEntity.ok(articles);
        } catch (Exception exception) {
            log.error("Error fetching articles", exception);
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).build();
        }
    }

    @Operation(
            summary = "Fetch PubMed summaries",
            description = "Runs ESummary for the PMIDs and returns one document summary per PMID.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PubMed IDs",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(type = "integer", format = "int64")),
                            examples = @ExampleObject(value = PMID_LIST_EXAMPLE))),
            responses = @ApiResponse(responseCode = "200", description = "Document summaries",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PubMedSummaryResult.class))))
    @PostMapping("/pubmed/summary")
    ResponseEntity<PubMedSummaryResult> fetchSummaries(@RequestBody List<Long> pmids) {
        if (pmids == null || pmids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var summaries = fetchService.fetchSummaries(pmids);
            return ResponseEntity.ok(summaries);
        } catch (Exception exception) {
            log.error("Error fetching summaries", exception);
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).build();
        }
    }

    @Operation(
            summary = "Fetch PubMed history dates",
            description = "Returns the 'pubmed' history date of each PMID as CSV rows of pmid,date (no header).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PubMed IDs",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(type = "integer", format = "int64")),
                            examples = @ExampleObject(value = PMID_LIST_EXAMPLE))),
            responses = @ApiResponse(responseCode = "200", description = "One pmid,date row per PMID",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "36537061,2022/12/21\n36897886,2023/03/10\n"))))
    @PostMapping(value = "/pubmed/history/date", produces = "text/csv")
    ResponseEntity<String> fetchHistoryDates(@RequestBody List<Long> pmids) {
        if (pmids == null || pmids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var csv = fetchService.fetchHistoryDatesAsCsv(pmids);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv")).body(csv);
        } catch (Exception exception) {
            log.error("Error fetching history dates", exception);
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).build();
        }
    }
}

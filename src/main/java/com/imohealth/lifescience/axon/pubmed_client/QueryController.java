package com.imohealth.lifescience.axon.pubmed_client;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@Tag(name = "PubMed", description = "NCBI E-utilities pass-through for PubMed")
public class QueryController {

    final QueryService queryService;

    @Operation(
            summary = "Search PubMed",
            description = """
                    Runs an ESearch query and returns the raw E-utilities JSON (history enabled). Page through \
                    results with `retstart` and `retmax`; `esearchresult.count` is the total number of matches. \
                    PubMed only returns the first 9,999 records of a query.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PubMed query term in Entrez syntax",
                    content = @Content(mediaType = "text/plain",
                            examples = @ExampleObject(value = "(HPV vaccine OR gardasil) AND drug therapy[mesh]"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "ESearch result",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "400",
                            description = "Body missing, retstart outside 0-9998, or retmax outside 1-9999")
            })
    @PostMapping("/pubmed/query")
    ResponseEntity<String> searchPubMed(
            @RequestBody String queryTerm,
            @Parameter(description = "Index of the first PMID to return (0-based)")
            @RequestParam(defaultValue = "0") int retstart,
            @Parameter(description = "Number of PMIDs to return (defaults to `pubmed.search.default-retmax`, 10)")
            @RequestParam(required = false) Integer retmax) {
        if (retstart < 0 || retstart >= QueryService.ESEARCH_MAX_RECORDS
                || (retmax != null && (retmax < 1 || retmax > QueryService.ESEARCH_MAX_RECORDS))) {
            return ResponseEntity.badRequest().build();
        }
        var response = queryService.callPubMedSearch(queryTerm, retstart, retmax);
        var responseEntity = new ResponseEntity<>(response.getBody(), response.getStatusCode());
        return responseEntity;
    }
}

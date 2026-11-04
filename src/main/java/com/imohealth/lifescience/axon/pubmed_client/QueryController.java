package com.imohealth.lifescience.axon.pubmed_client;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@Tag(name = "PubMed", description = "NCBI E-utilities pass-through for PubMed")
public class QueryController {

    final QueryService queryService;

    @Operation(
            summary = "Search PubMed",
            description = "Runs an ESearch query and returns the raw E-utilities JSON (first 10 PMIDs, history enabled).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PubMed query term in Entrez syntax",
                    content = @Content(mediaType = "text/plain",
                            examples = @ExampleObject(value = "(HPV vaccine OR gardasil) AND drug therapy[mesh]"))),
            responses = @ApiResponse(responseCode = "200", description = "ESearch result",
                    content = @Content(mediaType = "application/json")))
    @PostMapping("/pubmed/query")
    ResponseEntity<String> searchPubMed(@RequestBody String queryTerm) {
        var response = queryService.callPubMedSearch(queryTerm);
        var responseEntity = new ResponseEntity<>(response.getBody(), response.getStatusCode());
        return responseEntity;
    }
}

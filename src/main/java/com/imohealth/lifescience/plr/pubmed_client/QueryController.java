package com.imohealth.lifescience.plr.pubmed_client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
public class QueryController {

    private final QueryService queryService;

    @PostMapping("/pubmed/query")
    ResponseEntity<String> searchPubMed(@RequestBody String queryTerm) {
        var response = queryService.callPubMedSearch(queryTerm);
        var responseEntity = new ResponseEntity<>(response.getBody(), response.getStatusCode());
        return responseEntity;
    }
}

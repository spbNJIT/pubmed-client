package com.imohealth.lifescience.plr.pubmed_client;

import com.imohealth.lifescience.plr.pubmed_client.model.PubMedArticle;
import com.imohealth.lifescience.plr.pubmed_client.model.PubMedSummaryResult;
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
public class FetchController {

    private final FetchService fetchService;

    @PostMapping("/pubmed/fetch")
    ResponseEntity<List<PubMedArticle>> fetchArticles(@RequestBody List<Long> pmids) {
        if (pmids == null || pmids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var articles = fetchService.fetchArticles(pmids);
            return ResponseEntity.ok(articles);
        } catch (Exception e) {
            log.error("Error fetching articles", e);
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).build();
        }
    }

    @PostMapping("/pubmed/summary")
    ResponseEntity<PubMedSummaryResult> fetchSummaries(@RequestBody List<Long> pmids) {
        if (pmids == null || pmids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var summaries = fetchService.fetchSummaries(pmids);
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            log.error("Error fetching summaries", e);
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).build();
        }
    }

    @PostMapping(value = "/pubmed/history/date", produces = "text/csv")
    ResponseEntity<String> fetchHistoryDates(@RequestBody List<Long> pmids) {
        if (pmids == null || pmids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var csv = fetchService.fetchHistoryDatesAsCsv(pmids);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv")).body(csv);
        } catch (Exception e) {
            log.error("Error fetching history dates", e);
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).build();
        }
    }
}

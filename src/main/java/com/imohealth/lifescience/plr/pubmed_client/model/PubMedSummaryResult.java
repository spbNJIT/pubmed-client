package com.imohealth.lifescience.plr.pubmed_client.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PubMedSummaryResult {

    @JsonProperty("result")
    private ResultContainer result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultContainer {
        @JsonProperty("uids")
        private List<String> uids;

        private List<DocSum> docSums = new ArrayList<>();

        @JsonAnySetter
        public void setDocSum(String uid, Map<String, Object> value) {
            DocSum docSum = new DocSum();
            docSum.setUid(uid);
            docSum.setProperties(value);
            docSums.add(docSum);
        }
    }

    @Data
    public static class DocSum {
        private String uid;
        private Map<String, Object> properties;
    }
}

package com.imohealth.lifescience.axon.pubmed_client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QueryServiceTest {

    private static final String ESEARCH_URL = "https://eutils.test/esearch.fcgi";

    private MockRestServiceServer server;
    private QueryService service;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new PubMedProperties("https://eutils.test", "test-key", new PubMedProperties.Search(10));
        service = new QueryService(properties, builder);
    }

    @Test
    void sendsRequestedPage() {
        server.expect(requestTo(startsWith(ESEARCH_URL)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("retstart", "20"))
                .andExpect(queryParam("retmax", "50"))
                .andExpect(queryParam("db", "pubmed"))
                .andExpect(queryParam("api_key", "test-key"))
                .andExpect(content().formDataContains(Map.of("term", "asthma")))
                .andRespond(withSuccess("{\"esearchresult\":{\"count\":\"100\"}}", MediaType.APPLICATION_JSON));

        var response = service.callPubMedSearch("asthma", 20, 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"count\":\"100\"");
        server.verify();
    }

    @Test
    void usesConfiguredDefaultRetmax() {
        server.expect(requestTo(startsWith(ESEARCH_URL)))
                .andExpect(queryParam("retstart", "0"))
                .andExpect(queryParam("retmax", "10"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.callPubMedSearch("asthma", 0, null);

        server.verify();
    }

    @Test
    void returnsFailedDependencyWhenEsearchFails() {
        server.expect(requestTo(startsWith(ESEARCH_URL))).andRespond(withServerError());

        var response = service.callPubMedSearch("asthma", 0, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FAILED_DEPENDENCY);
    }
}

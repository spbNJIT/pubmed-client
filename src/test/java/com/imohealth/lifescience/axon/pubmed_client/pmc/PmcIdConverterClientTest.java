package com.imohealth.lifescience.axon.pubmed_client.pmc;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PmcIdConverterClientTest {

    private static final String ID_CONVERTER_URL = "https://idconv.test/articles/";

    private MockRestServiceServer server;

    private PmcIdConverterClient client(int batchSize, String email) {
        var builder = RestClient.builder().baseUrl(ID_CONVERTER_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new PmcProperties("https://bucket.test", ID_CONVERTER_URL, "pubmed-client", email,
                batchSize, 500, Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new PmcIdConverterClient(builder.build(), properties);
    }

    @Test
    void resolvesPmidsAndSkipsErrorRecords() {
        var client = client(200, "dev@example.com");
        server.expect(requestTo(ID_CONVERTER_URL
                        + "?ids=36897886%2C12345&format=json&tool=pubmed-client&email=dev%40example.com"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":"ok","records":[
                          {"pmcid":"PMC10004557","pmid":36897886,"requested-id":"36897886"},
                          {"pmid":12345,"requested-id":"12345","status":"error","errmsg":"Identifier not found in PMC"}]}
                        """, MediaType.APPLICATION_JSON));

        Map<Long, String> pmcids = client.resolve(List.of(36897886L, 12345L));

        assertThat(pmcids).containsExactly(Map.entry(36897886L, "PMC10004557"));
        server.verify();
    }

    @Test
    void splitsRequestsIntoBatches() {
        var client = client(2, "");
        server.expect(requestTo(ID_CONVERTER_URL + "?ids=1%2C2&format=json&tool=pubmed-client"))
                .andRespond(withSuccess("""
                        {"status":"ok","records":[{"pmcid":"PMC1","pmid":1},{"pmcid":"PMC2","pmid":2}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(ID_CONVERTER_URL + "?ids=3&format=json&tool=pubmed-client"))
                .andRespond(withSuccess("""
                        {"status":"ok","records":[{"pmcid":"PMC3","pmid":3}]}
                        """, MediaType.APPLICATION_JSON));

        Map<Long, String> pmcids = client.resolve(List.of(1L, 2L, 3L));

        assertThat(pmcids).containsOnlyKeys(1L, 2L, 3L);
        server.verify();
    }

    @Test
    void omitsBlankEmail() {
        var client = client(200, " ");
        server.expect(requestTo(ID_CONVERTER_URL + "?ids=1&format=json&tool=pubmed-client"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"records\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.resolve(List.of(1L))).isEmpty();
        server.verify();
    }

    @Test
    void propagatesServerErrors() {
        var client = client(200, "");
        server.expect(requestTo(ID_CONVERTER_URL + "?ids=1&format=json&tool=pubmed-client"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.resolve(List.of(1L))).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void rejectsNonOkStatus() {
        var client = client(200, "");
        server.expect(requestTo(ID_CONVERTER_URL + "?ids=1&format=json&tool=pubmed-client"))
                .andRespond(withSuccess("{\"status\":\"error\",\"message\":\"bad request\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.resolve(List.of(1L))).isInstanceOf(IllegalStateException.class);
    }
}

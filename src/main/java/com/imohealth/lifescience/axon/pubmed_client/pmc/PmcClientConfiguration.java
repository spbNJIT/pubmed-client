package com.imohealth.lifescience.axon.pubmed_client.pmc;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(PmcProperties.class)
public class PmcClientConfiguration {

    @Bean
    PmcIdConverterClient pmcIdConverterClient(RestClient.Builder builder, PmcProperties properties) {
        return new PmcIdConverterClient(pmcRestClient(builder, properties.idConverterUrl(), properties), properties);
    }

    @Bean
    PmcOpenDataClient pmcOpenDataClient(RestClient.Builder builder, PmcProperties properties) {
        return new PmcOpenDataClient(pmcRestClient(builder, properties.bucketUrl(), properties), properties);
    }

    /**
     * Builds a {@link RestClient} for a PMC endpoint from the auto-configured builder, applying the PMC timeouts.
     * Clients are built once and shared (rather than per call) so connections are reused.
     */
    static RestClient pmcRestClient(RestClient.Builder builder, String baseUrl, PmcProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}

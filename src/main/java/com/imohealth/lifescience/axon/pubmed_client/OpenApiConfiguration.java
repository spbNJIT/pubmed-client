package com.imohealth.lifescience.axon.pubmed_client;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API-level OpenAPI metadata. springdoc generates the paths from the controllers and serves the spec at
 * {@code /v3/api-docs} and Swagger UI at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI pubmedClientOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PubMed Client API")
                        .version("0.0.1")
                        .description("""
                                Passes requests to the NCBI E-utilities PubMed API (search, fetch, summary) and \
                                downloads full-text PDFs from the PubMed Central (PMC) Article Datasets on AWS. \
                                PMC data is provided by the National Library of Medicine (NLM)."""))
                .externalDocs(new ExternalDocumentation()
                        .description("NCBI E-utilities documentation")
                        .url("https://www.ncbi.nlm.nih.gov/books/NBK25497/"));
    }

    /**
     * Every endpoint answers 400 for a missing or empty body and 424 when NCBI/PMC fails, so document those once
     * here instead of on each controller method. Operations that describe their own 400 or 424 keep theirs.
     */
    @Bean
    OpenApiCustomizer standardErrorResponses() {
        return openApi -> openApi.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .forEach(operation -> {
                    operation.getResponses().putIfAbsent("400",
                            new ApiResponse().description("Request body is missing or empty"));
                    operation.getResponses().putIfAbsent("424",
                            new ApiResponse().description("The upstream NCBI or PMC service failed"));
                });
    }
}

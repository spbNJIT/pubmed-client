package com.imohealth.lifescience.axon.pubmed_client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void documentsEveryEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("PubMed Client API"))
                .andExpect(jsonPath("$.paths['/pubmed/query'].post.summary").value("Search PubMed"))
                .andExpect(jsonPath("$.paths['/pubmed/fetch'].post.summary").value("Fetch PubMed articles"))
                .andExpect(jsonPath("$.paths['/pubmed/summary'].post.summary").value("Fetch PubMed summaries"))
                .andExpect(jsonPath("$.paths['/pubmed/history/date'].post.summary").value("Fetch PubMed history dates"))
                .andExpect(jsonPath("$.paths['/pmc/pdf'].post.summary").value("Download PMC PDFs"))
                .andExpect(jsonPath("$.tags[*].name", hasItems("PubMed", "PMC")));
    }

    @Test
    void documentsPdfDownloadAsBinaryZip() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/pmc/pdf'].post.responses['200'].content['application/zip'].schema.format")
                        .value("binary"))
                .andExpect(jsonPath("$.paths['/pmc/pdf'].post.responses['400'].description",
                        startsWith("Body missing or empty, or more than")))
                .andExpect(jsonPath("$.paths['/pmc/pdf'].post.requestBody.content['application/json'].schema.type")
                        .value("array"));
    }

    @Test
    void addsStandardErrorResponsesToEveryOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/pubmed/fetch'].post.responses['400'].description")
                        .value("Request body is missing or empty"))
                .andExpect(jsonPath("$.paths['/pubmed/query'].post.responses['424'].description")
                        .value("The upstream NCBI or PMC service failed"));
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/swagger-ui/index.html")));
    }
}

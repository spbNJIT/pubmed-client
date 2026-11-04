package com.imohealth.lifescience.axon.pubmed_client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QueryControllerTest {

    @Mock
    QueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new QueryController(queryService)).build();
    }

    @Test
    void passesPagingParameters() throws Exception {
        when(queryService.callPubMedSearch("asthma", 100, 500)).thenReturn(ResponseEntity.ok("{\"page\":true}"));

        mockMvc.perform(post("/pubmed/query?retstart=100&retmax=500").contentType(MediaType.TEXT_PLAIN).content("asthma"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"page\":true}"));
    }

    @Test
    void defaultsToFirstPage() throws Exception {
        when(queryService.callPubMedSearch("asthma", 0, null)).thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(post("/pubmed/query").contentType(MediaType.TEXT_PLAIN).content("asthma"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsLastAllowedPage() throws Exception {
        when(queryService.callPubMedSearch("asthma", 9998, 9999)).thenReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(post("/pubmed/query?retstart=9998&retmax=9999").contentType(MediaType.TEXT_PLAIN).content("asthma"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsOutOfRangePaging() throws Exception {
        for (String paging : new String[]{"retstart=-1", "retstart=9999", "retmax=0", "retmax=10000"}) {
            mockMvc.perform(post("/pubmed/query?" + paging).contentType(MediaType.TEXT_PLAIN).content("asthma"))
                    .andExpect(status().isBadRequest());
        }

        verify(queryService, never()).callPubMedSearch(any(), anyInt(), any());
    }
}

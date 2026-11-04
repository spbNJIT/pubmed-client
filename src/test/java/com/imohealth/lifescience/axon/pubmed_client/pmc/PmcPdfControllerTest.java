package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.imohealth.lifescience.axon.pubmed_client.pmc.model.ResolvedPmid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpServerErrorException;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FAILED_DEPENDENCY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PmcPdfControllerTest {

    private static final int MAX_PMIDS = 3;

    @Mock
    PmcPdfService pmcPdfService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var properties = new PmcProperties("https://bucket.test", "https://idconv.test/articles/", "pubmed-client", "",
                200, MAX_PMIDS, Duration.ofSeconds(1), Duration.ofSeconds(1));
        mockMvc = MockMvcBuilders.standaloneSetup(new PmcPdfController(pmcPdfService, properties)).build();
    }

    @Test
    void streamsZipForResolvedPmids() throws Exception {
        List<ResolvedPmid> articles = List.of(new ResolvedPmid(1L, "PMC1"));
        when(pmcPdfService.resolve(List.of(1L, 2L))).thenReturn(articles);
        doAnswer(invocation -> {
            invocation.getArgument(1, OutputStream.class).write("zip-bytes".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(pmcPdfService).writePdfArchive(eq(articles), any(OutputStream.class));

        MvcResult result = mockMvc.perform(post("/pmc/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 2, 1]"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"pmc-pdfs.zip\""))
                .andExpect(content().string("zip-bytes"));
    }

    @Test
    void rejectsEmptyList() throws Exception {
        mockMvc.perform(post("/pmc/pdf").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());

        verify(pmcPdfService, never()).resolve(anyList());
    }

    @Test
    void rejectsMissingBody() throws Exception {
        mockMvc.perform(post("/pmc/pdf").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(pmcPdfService, never()).resolve(anyList());
    }

    @Test
    void rejectsMoreThanMaxDistinctPmids() throws Exception {
        String body = LongStream.rangeClosed(1, MAX_PMIDS + 1).boxed().toList().toString();

        mockMvc.perform(post("/pmc/pdf").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(pmcPdfService, never()).resolve(anyList());
    }

    @Test
    void acceptsDuplicatesUpToMaxDistinctPmids() throws Exception {
        when(pmcPdfService.resolve(List.of(1L, 2L, 3L))).thenReturn(List.of());

        mockMvc.perform(post("/pmc/pdf").contentType(MediaType.APPLICATION_JSON).content("[1, 2, 3, 3, 2]"))
                .andExpect(request().asyncStarted());
    }

    @Test
    void returnsFailedDependencyWhenIdConverterFails() throws Exception {
        when(pmcPdfService.resolve(List.of(1L))).thenThrow(new HttpServerErrorException(FAILED_DEPENDENCY));

        mockMvc.perform(post("/pmc/pdf").contentType(MediaType.APPLICATION_JSON).content("[1]"))
                .andExpect(status().isFailedDependency());
    }
}

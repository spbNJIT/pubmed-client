package com.imohealth.lifescience.axon.pubmed_client.pmc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PmcPropertiesTest {

    @Autowired
    PmcProperties properties;

    @Test
    void bindsDefaultsFromApplicationYaml() {
        assertThat(properties.bucketUrl()).isEqualTo("https://pmc-oa-opendata.s3.amazonaws.com");
        assertThat(properties.idConverterUrl()).isEqualTo("https://pmc.ncbi.nlm.nih.gov/tools/idconv/api/v1/articles/");
        assertThat(properties.tool()).isEqualTo("pubmed-client");
        assertThat(properties.idConverterBatchSize()).isEqualTo(200);
        assertThat(properties.maxPmids()).isEqualTo(500);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(60));
    }
}

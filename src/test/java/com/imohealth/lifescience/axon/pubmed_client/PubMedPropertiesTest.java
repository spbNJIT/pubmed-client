package com.imohealth.lifescience.axon.pubmed_client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PubMedPropertiesTest {

    @Autowired
    PubMedProperties properties;

    @Test
    void bindsFromApplicationYaml() {
        assertThat(properties.baseUrl()).isEqualTo("https://eutils.ncbi.nlm.nih.gov/entrez/eutils");
        assertThat(properties.apiKey()).isEqualTo("123");
        assertThat(properties.search().defaultRetmax()).isEqualTo(10);
    }

    @Test
    void requiresApiKey() {
        assertThatThrownBy(() -> new PubMedProperties("https://eutils.test", " ", new PubMedProperties.Search(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pubmed.api-key");
    }
}

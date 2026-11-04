package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PmcMappersTest {

    @Test
    void xmlMapperRejectsExternalEntities() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE result [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <result><value>&secret;</value></result>
                """;

        assertThatThrownBy(() -> PmcMappers.xmlMapper().readTree(xml)).isInstanceOf(Exception.class);
    }

    @Test
    void jsonMapperIgnoresUnknownProperties() throws Exception {
        record Sample(String known) {
        }

        Sample sample = PmcMappers.jsonMapper().readValue("{\"known\":\"a\",\"unknown\":1}", Sample.class);

        assertThat(sample.known()).isEqualTo("a");
    }

    @Test
    void xmlMapperReadsPlainXml() throws Exception {
        JsonNode tree = PmcMappers.xmlMapper().readTree("<result><value>a</value></result>");

        assertThat(tree.get("value").asText()).isEqualTo("a");
    }
}

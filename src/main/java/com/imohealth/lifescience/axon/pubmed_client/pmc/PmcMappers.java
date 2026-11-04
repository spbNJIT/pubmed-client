package com.imohealth.lifescience.axon.pubmed_client.pmc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.xml.stream.XMLInputFactory;

/**
 * Jackson mappers for PMC responses, configured the same way as the E-utilities mappers in {@code FetchService}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PmcMappers {

    static XmlMapper xmlMapper() {
        // S3 listings are untrusted input - disable DTDs and external entities to prevent XXE
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return XmlMapper.builder(new XmlFactory(inputFactory))
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .build();
    }

    static ObjectMapper jsonMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}

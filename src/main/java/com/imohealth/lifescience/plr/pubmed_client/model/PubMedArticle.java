package com.imohealth.lifescience.plr.pubmed_client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PubMedArticle {

    @JacksonXmlProperty(localName = "MedlineCitation")
    private Map<String, Object> medlineCitation;

    @JacksonXmlProperty(localName = "PubmedData")
    private Map<String, Object> pubmedData;
}

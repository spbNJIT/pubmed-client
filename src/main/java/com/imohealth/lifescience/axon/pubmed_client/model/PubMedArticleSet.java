package com.imohealth.lifescience.axon.pubmed_client.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JacksonXmlRootElement(localName = "PubmedArticleSet")
public class PubMedArticleSet {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "PubmedArticle")
    private List<PubMedArticle> pubmedArticles = new ArrayList<>();
}

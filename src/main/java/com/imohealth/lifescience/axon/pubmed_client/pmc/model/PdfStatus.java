package com.imohealth.lifescience.axon.pubmed_client.pmc.model;

/**
 * Outcome of looking up and downloading the PDF for one PMID.
 */
public enum PdfStatus {
    DOWNLOADED,
    NOT_IN_PMC,
    NOT_IN_BUCKET,
    NO_PDF,
    ERROR
}

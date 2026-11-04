# PMC PDF Download

`POST /pmc/pdf` takes a list of PubMed IDs and streams back a ZIP of the full-text PDFs available in the public [PMC Article Datasets](https://pmc.ncbi.nlm.nih.gov/tools/pmcaws/) S3 bucket (`pmc-oa-opendata`, anonymous access, no AWS account needed).

## Usage

```bash
curl -X POST localhost:8088/pmc/pdf \
  -H 'Content-Type: application/json' \
  -d '[36537061, 36897886, 12345]' \
  -o pmc-pdfs.zip
```

| Response | When |
|----------|------|
| `200 application/zip` | Always when the input is valid - even if no PMID has a PDF |
| `400 Bad Request` | Body missing or empty, or more than `pmc.max-pmids` distinct PMIDs |
| `424 Failed Dependency` | The PMC ID Converter API failed |

Interactive API docs for all endpoints are served at `/swagger-ui.html`, and the OpenAPI spec at `/v3/api-docs` (`/v3/api-docs.yaml` for YAML).

## Archive Contents

- `<pmid>_PMC<id>.<version>.pdf` - one PDF per PMID that has one, e.g. `36537061_PMC10009416.1.pdf`
- `manifest.csv` - one row per distinct PMID, in request order: `pmid,pmcid,version,status,file_name,is_manuscript,is_retracted,license_code,message`
- `NOTICE.txt` - NLM source acknowledgement required by the dataset terms

Manifest statuses:

| Status | Meaning |
|--------|---------|
| `DOWNLOADED` | PDF included in the archive |
| `NOT_IN_PMC` | The ID Converter has no PMCID for the PMID |
| `NOT_IN_BUCKET` | The PMCID has no article versions in the bucket |
| `NO_PDF` | No version has a PDF (the publisher license does not permit one) |
| `ERROR` | Listing, metadata or download failed for this PMID; see `message` |

## How It Works

1. **Resolve** - PMIDs are mapped to PMCIDs with the [PMC ID Converter API](https://pmc.ncbi.nlm.nih.gov/tools/idconv/), 200 IDs per call. This runs before the response starts streaming, so an ID Converter outage can still return `424`.
2. **Discover versions** - the bucket is keyed by PMCID and version (`PMC<id>.<v>/`), and versions are not contiguous, so each PMCID's versions are listed with S3 ListObjectsV2 (`prefix=PMC<id>.&delimiter=/`).
3. **Select** - each version's `metadata/PMC<id>.<v>.json` is read. The highest-numbered published version with a `pdf_url` wins; if there is none, the highest-numbered author manuscript with a PDF is used.
4. **Download** - the PDF is fetched over HTTPS, spooled to a temp file, then copied into the ZIP so a failed download never leaves a truncated entry. Only article PDF keys on the configured bucket are fetched.

PMIDs are processed sequentially. A failure for one PMID is recorded in the manifest and the archive continues; a client disconnect stops processing.

Retracted articles and all licenses are included - `is_retracted` and `license_code` are recorded in the manifest so the caller can decide on reuse.

## Code

All code is in the `pmc` package:

| Class | Responsibility |
|-------|----------------|
| `PmcPdfController` | Endpoint, input validation, streaming response |
| `PmcPdfService` | Orchestration, version selection, ZIP, manifest and notice |
| `PmcIdConverterClient` | PMID to PMCID batches |
| `PmcOpenDataClient` | Bucket listing, metadata, PDF download, URL validation |
| `PmcClientConfiguration` | Client beans with shared `RestClient`s and timeouts |
| `PmcProperties` | `pmc.*` configuration |
| `PmcMappers` | XXE-hardened Jackson mappers |
| `model/*` | ID Converter, S3 listing, metadata and manifest types |

## Configuration

```yaml
spring.mvc.async.request-timeout: 30m   # streamed archives outlive the ~30s container default
pmc:
  bucket-url: "https://pmc-oa-opendata.s3.amazonaws.com"
  id-converter-url: "https://pmc.ncbi.nlm.nih.gov/tools/idconv/api/v1/articles/"
  tool: "pubmed-client"
  email: "${PMC_EMAIL:}"   # NCBI asks callers to identify themselves; omitted when blank
  id-converter-batch-size: 200
  max-pmids: 500
  connect-timeout: 10s
  read-timeout: 60s
```

## Not Included

- Non-PDF files (XML, plain text, images, supplementary data)
- License filtering or skipping retracted articles
- Concurrent downloads, async jobs or caching

## Terms

Use of the dataset requires acknowledging NLM as the source (done in `NOTICE.txt`), and redistribution is limited to articles licensed for it. See the [bucket README](https://pmc-oa-opendata.s3.amazonaws.com/README.txt).

SharePoint Data Store for Fess
[![Java CI with Maven](https://github.com/codelibs/fess-ds-sharepoint/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-ds-sharepoint/actions/workflows/maven.yml)
==========================

## Overview

SharePoint Data Store for Fess.

## Download

See [Maven Repository](https://repo1.maven.org/maven2/org/codelibs/fess/fess-ds-sharepoint/).

## Installation

1. Download fess-ds-sharepoint-X.X.X.jar
2. Copy fess-ds-sharepoint-X.X.X.jar to $FESS\_HOME/app/WEB-INF/lib or /usr/share/fess/app/WEB-INF/lib

## Crawling Setting

### include_pattern / exclude_pattern

These are **not** matched against the URL Fess indexes and displays in search results. They are
matched against the value each crawl path already has on hand at the point it can still skip the
work a rejected item would otherwise cost:

- **A file in a document library** is matched against its **server-relative path**, e.g.
  `/sites/mysite/Shared Documents/Reports/2026.xlsx`. A pattern copied from a sibling data store
  that matches the full URL, such as `https://mysite.sharepoint.com/sites/mysite/.*`, will not
  match anything here - there is no scheme or host in this value.
  Example: `include_pattern=/sites/mysite/Shared Documents/Reports/.*`
- **A list item** is matched against its `FileRef`, e.g. `/sites/mysite/Lists/Tasks/1_.000`. This
  is checked only after the item's field values have already been fetched, since list items carry
  no URL-ish value before then - `exclude_pattern` on a list item does not save that request the
  way it does for a file.
  Example: `exclude_pattern=/sites/mysite/Lists/Archive/.*`
- **A list item attachment** is matched against its own **server-relative path**, e.g.
  `/sites/mysite/Lists/Tasks/Attachments/1/spec.pdf`. It is matched separately from the item it
  belongs to: excluding an item by its `FileRef` does not exclude the item's attachments, and vice
  versa, because the two values are different paths.
  Example: `exclude_pattern=/sites/mysite/Lists/Tasks/Attachments/.*`

Both are treated as unset if left blank, and an invalid regular expression is treated as unset
rather than rejecting every item.

### ignore_error

`ignore_error` decides whether a file's content extraction failure is logged and skipped instead of
failing that crawl target. **It defaults to `false` here, unlike the other `fess-ds-*` plugins,
which default it to `true`.**

The reason is that the suppression fires when *either* this parameter *or* the global
`crawler.ignore.content.exception` setting says to ignore - it is an OR, not a per-data-config
override. Defaulting it to `true` would make the suppression unconditional and silently override an
installation that had deliberately set `crawler.ignore.content.exception=false` to get hard
failures. This plugin ignored content-extraction failures exactly as that global setting told it to
before the parameter existed, and the `false` default keeps that unchanged. The sibling plugins had
no such prior behaviour to preserve.

Set `ignore_error=true` to have extraction failures logged and skipped regardless of the global
setting.

### extractor_name

`extractor_name` names the extractor component used **only for a MIME type the extractor factory
does not map** - it is a fallback, not the extractor used for a file in general.

fess-crawler's `ExtractorBuilder.extract()` asks `extractorFactory.getExtractor(mimeType)` first and
then `getExtractor(detectedMimeType)`; only when neither the declared nor the detected MIME type is
mapped does it fall through to the component named here. `fess-crawler-lasta`'s
`crawler/extractor.xml` maps around 1500 lines of MIME types, including `application/pdf`,
`text/html` and `text/plain`, so for essentially any real file this parameter changes nothing. To
change which extractor handles a mapped MIME type, change that mapping, not this parameter.

### User-Agent

**Upgrade warning:** the User-Agent changed from Apache HttpClient's default
(`Apache-HttpClient/4.5.14 (Java/...)`) to the plugin-specific string below. Any proxy, WAF or
SharePoint request classifier that allowlisted the old value will start rejecting or reclassifying
this crawler. Check those rules before upgrading.

Every request from this data store carries the User-Agent `FessSharePointDataStore/1.0`. Two of
SharePoint's built-in request classifiers matter here, and they are separate claims: a request
that `SPSearchCrawlingRequestClassifier` recognizes as a search-engine crawler (by user-agent
pattern) defaults to `ThrottleLevel.FirstStage`; this string is deliberately not one of those
patterns, so it is not classified that way at all. Separately, an on-premises administrator who
wants to exempt this crawl from throttling entirely can register this exact string with
`SPHttpUserAgentAndMethodClassifier` at `ThrottleLevel = Never`.

### Throttling and backoff

This data store cooperates with two on-premises SharePoint throttling signals. Neither is
currently configurable - there is no data-config parameter to disable or tune either one.

- **A 503 response** is retried the same as any other error, up to `retry_limit`, but with an
  increasing wait before each retry: 2 seconds, then 4, then 8, doubling up to a 30-second cap,
  each randomized to 70-129% of that value. A crawl target that keeps returning 503 pays this wait
  before every retry it actually gets, but not after its last one - a target `retry_limit`
  ultimately gives up on is not delayed pointlessly first.
- **Every response** - successful or not, including a page of a listing the crawl is about to
  discard - is inspected for the `X-SharePointHealthScore` response header (0 idle to 10 very
  busy). A score of 9 or above makes the crawl wait before doing anything else: score 9 waits the
  same ~2 seconds as the first 503 retry above, score 10 waits ~4 seconds, and so on, doubling for
  each point past 9. **This adds up across the whole crawl, with no aggregate cap**: a farm sitting
  at health score 9 under sustained load adds roughly 2 seconds to *every single request* this data
  store makes - including every page of every folder and list listing - which can turn a crawl that
  would otherwise take hours into one that takes substantially longer. If a crawl unexpectedly
  slows down by an order of magnitude, check the farm's health score during that window before
  assuming something else is wrong.

**429 and `Retry-After` are not handled.** The backoff above is deliberately scoped to the two
signals an on-premises farm sends. A `429 Too Many Requests` is retried like any other error, with
no wait at all, and a `Retry-After` header on any response is ignored - so a SharePoint Online
tenant, which is what actually sends those, gets no cooperation from this backoff.

### List Crawl

```
# Parameter
url={URL of SharePoint}
auth.ntlm.user={Name of SharePoint User}
auth.ntlm.password={Passsword}
site.name={SiteName of crawling target}
site.path={Server-relative managed path of the site, e.g. /teams/eng or / for the root site collection. Optional: when set, site.name is no longer required. Leaving it unset keeps the existing /sites/{site.name} behavior exactly.}
site.list_name={ListName of crawling target}
## (Option parameter)
list.item.content.include_fields={FieldName to include to content.}
list.item.content.exclude_fields={FieldName to exclude to content.}
ignore_error={true or false. Log a content extraction failure instead of failing the crawl target. Default is false. See "ignore_error" above.}
default_permissions={Comma-separated permissions merged into every document's role list, e.g. {role}guest.}
include_pattern={Regular expression a crawled item's URL-ish value must match to be crawled. See "include_pattern / exclude_pattern" above for what that value is.}
exclude_pattern={Regular expression that excludes a crawled item from being crawled. See "include_pattern / exclude_pattern" above.}
max_content_length={Maximum file size in bytes. -1 for no limit (default).}
supported_mimetypes={Comma-separated regular expressions a file's MIME type must match at least one of. Default is .*}
extractor_name={Fallback extractor component for a MIME type the extractor factory does not map. Default is tikaExtractor. See "extractor_name" above.}
proxy_host={HTTP proxy host to route requests through.}
proxy_port={HTTP proxy port to route requests through.}
## SharePoint2013
sp.version=2013


# Script
url=url
host=host
site=site
title="["+list_name+"]"+title
content=content
cache=content
digest=digest
content_length=content.length()
last_modified=last_modified
created=created
timestamp=last_modified
mimetype=mimetype
filetype=filetype
```

### Document Library Crawl

```
# Parameter
url={URL of SharePoint}
auth.ntlm.user={Name of SharePoint User}
auth.ntlm.password={Passsword}
site.name={SiteName of crawling target}
site.path={Server-relative managed path of the site, e.g. /teams/eng or / for the root site collection. Optional: when set, site.name is no longer required. Leaving it unset keeps the existing /sites/{site.name} behavior exactly.}
site.doclib_path={DocumentLibrary path. Ex) /Shared Documents}
## (Option parameter)
ignore_error={true or false. Log a content extraction failure instead of failing the crawl target. Default is false. See "ignore_error" above.}
default_permissions={Comma-separated permissions merged into every document's role list, e.g. {role}guest.}
include_pattern={Regular expression a crawled item's URL-ish value must match to be crawled. See "include_pattern / exclude_pattern" above for what that value is.}
exclude_pattern={Regular expression that excludes a crawled item from being crawled. See "include_pattern / exclude_pattern" above.}
max_content_length={Maximum file size in bytes. -1 for no limit (default).}
supported_mimetypes={Comma-separated regular expressions a file's MIME type must match at least one of. Default is .*}
extractor_name={Fallback extractor component for a MIME type the extractor factory does not map. Default is tikaExtractor. See "extractor_name" above.}
proxy_host={HTTP proxy host to route requests through.}
proxy_port={HTTP proxy port to route requests through.}
## SharePoint2013
sp.version=2013

# Script
url=url
host=host
site=site
title=title
content=content
cache=content
digest=digest
content_length=content.length()
last_modified=last_modified
created=created
timestamp=last_modified
mimetype=mimetype
filetype=filetype
```

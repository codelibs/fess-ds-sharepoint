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

Both are treated as unset if left blank, and an invalid regular expression is treated as unset
rather than rejecting every item.

### List Crawl

```
# Parameter
url={URL of SharePoint}
auth.ntlm.user={Name of SharePoint User}
auth.ntlm.password={Passsword}
site.name={SiteName of crawling target}
site.list_name={ListName of crawling target}
## (Option parameter)
list.item.content.include_fields={FieldName to include to content.}
list.item.content.exclude_fields={FieldName to exclude to content.}
ignore_error={true or false. Log a content extraction failure instead of failing the crawl target. Default is true.}
default_permissions={Comma-separated permissions merged into every document's role list, e.g. {role}guest.}
include_pattern={Regular expression a crawled item's URL-ish value must match to be crawled. See "include_pattern / exclude_pattern" above for what that value is.}
exclude_pattern={Regular expression that excludes a crawled item from being crawled. See "include_pattern / exclude_pattern" above.}
max_content_length={Maximum file size in bytes. -1 for no limit (default).}
supported_mimetypes={Comma-separated regular expressions a file's MIME type must match at least one of. Default is .*}
extractor_name={Name of the extractor component used to extract file content. Default is tikaExtractor.}
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
site.doclib_path={DocumentLibrary path. Ex) /Shared Documents}
## (Option parameter)
ignore_error={true or false. Log a content extraction failure instead of failing the crawl target. Default is true.}
default_permissions={Comma-separated permissions merged into every document's role list, e.g. {role}guest.}
include_pattern={Regular expression a crawled item's URL-ish value must match to be crawled. See "include_pattern / exclude_pattern" above for what that value is.}
exclude_pattern={Regular expression that excludes a crawled item from being crawled. See "include_pattern / exclude_pattern" above.}
max_content_length={Maximum file size in bytes. -1 for no limit (default).}
supported_mimetypes={Comma-separated regular expressions a file's MIME type must match at least one of. Default is .*}
extractor_name={Name of the extractor component used to extract file content. Default is tikaExtractor.}
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

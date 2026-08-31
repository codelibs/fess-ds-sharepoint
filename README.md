SharePoint Data Store for Fess
[![Java CI with Maven](https://github.com/codelibs/fess-ds-sharepoint/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-ds-sharepoint/actions/workflows/maven.yml)
==========================

## Overview

This data store crawls **on-premises SharePoint Server (2013, 2016, 2019, and Subscription
Edition)** over its REST/OData and (for 2013) XML/Atom APIs. If you are looking for **SharePoint
Online / Microsoft 365**, use [`fess-ds-microsoft365`](https://github.com/codelibs/fess-ds-microsoft365)
instead - this connector's OAuth support targets Azure ACS app-only authentication only (see
[Authentication](#authentication)) and has no Microsoft Graph integration.

## Download

`pom.xml`'s `distributionManagement` declares that this artifact is published to
`maven.codelibs.org` - `scpexe://maven.codelibs.org/var/www/maven/release` for releases,
`.../snapshot` for snapshots - not Maven Central, with an in-repo comment explaining that Fess
plugins are distributed through `maven.codelibs.org` deliberately, separately from the
Central-hosted `fess-parent`/`fess`/`fess-crawler` SNAPSHOT deployments.

> **Maintainer note - no download link below is established, please confirm one:** the previous
> version of this README linked to
> `https://repo1.maven.org/maven2/org/codelibs/fess/fess-ds-sharepoint/` (Maven Central), which the
> `distributionManagement` above contradicts. This rewrite does not replace it with a new working
> link, because the only alternative - `https://maven.codelibs.org/org/codelibs/fess/fess-ds-sharepoint/`
> - is *inferred* from what `pom.xml` declares, not confirmed by a network check of what either host
> actually serves, and there may be an older release history on Central from before this
> `distributionManagement` override existed. Please confirm the correct download location and add
> it here.

## Installation

1. Download `fess-ds-sharepoint-X.X.X.jar`.
2. Copy it to `$FESS_HOME/app/WEB-INF/lib` (or `/usr/share/fess/app/WEB-INF/lib`).

## Quick Start

The smallest working configuration for each of this plugin's two crawl modes. Both assume NTLM
against an on-premises farm; see [Authentication](#authentication) for Kerberos and OAuth.

**List Crawl** - crawl a single SharePoint list:

```
# Parameter
url=http://sharepoint.example.com/
auth.ntlm.user=DOMAIN\svc-fess
auth.ntlm.password=changeit
site.name=mysite
site.list_name=Tasks

# Script
url=url
title=title
content=content
digest=digest
content_length=content.length()
last_modified=last_modified
```

**Document Library Crawl** - crawl a document library's files:

```
# Parameter
url=http://sharepoint.example.com/
auth.ntlm.user=DOMAIN\svc-fess
auth.ntlm.password=changeit
site.name=mysite
site.doclib_path=/Shared Documents

# Script
url=url
title=title
content=content
digest=digest
content_length=content.length()
last_modified=last_modified
```

## Parameters

All parameters are read as data-config parameters (the "Parameter" box of the data store's admin
screen). "Req'd" means the crawl fails to start (a `ValidationException`) without it. Defaults come
from the code, not from any prior documentation.

### URL / Site

| Parameter | Default | Req'd | Description |
|---|---|---|---|
| `url` | none | **Yes** | SharePoint server base URL, e.g. `http://sharepoint.example.com/`. |
| `site.name` | none | **Yes, unless `site.path` is set** | Site collection name crawled under `/sites/<site.name>/`. |
| `site.path` | none (⇒ `/sites/<site.name>/`) | No | Server-relative managed path of the site, e.g. `/teams/eng` or `/` for the root site collection. When set, `site.name` is no longer required and this path is used verbatim (leading `/` added, trailing `/` added, if missing) instead of the hardcoded `/sites/` prefix. Unset keeps the existing behavior exactly. |
| `site.list_id` | none | No | Crawl a single list by GUID (List Crawl mode). Must be a bare GUID literal. |
| `site.list_name` | none | No | Crawl a single list by display name (List Crawl mode). |
| `site.doclib_path` | none | No | Document-library path under the site (Document Library Crawl mode), e.g. `/Shared Documents`. |
| `site.exclude_list` | none (no exclusions) | No | Comma-separated regex patterns of list entity-type names to exclude. Only applies to a whole-site crawl (none of `site.list_id`/`site.list_name`/`site.doclib_path` set). |
| `site.exclude_folder` | none (no exclusions) | No | Comma-separated regex patterns of top-level folder titles to exclude. Only applies to a whole-site crawl. |
| `site.crawl_subsites` | `false` | No | Recurse depth-first into the site's subsites via `_api/web/webinfos`. Only applies to a whole-site crawl. See [below](#sitecrawl_subsites--sitemax_depth). |
| `site.max_depth` | `10` | No | How many subsite hops below the root site `site.crawl_subsites` may recurse. The root is depth 0. |

### Authentication Parameters

| Parameter | Default | Req'd | Description |
|---|---|---|---|
| `auth.ntlm.user` | none | No | NTLM username. Setting it enables NTLM. `DOMAIN\user` works. |
| `auth.ntlm.password` | none | No | NTLM password. |
| `auth.ntlm.domain` | none | No | Windows domain sent as its own NTLM field, separate from `auth.ntlm.user`. |
| `auth.ntlm.workstation` | none | No | Workstation name sent in the NTLM negotiation. |
| `auth.kerberos.principal` | none | No | Client principal, written as `user@REALM`. Setting it enables Kerberos/SPNEGO. |
| `auth.kerberos.keytab` | none | No | Path to a keytab holding a key for the principal. Mutually exclusive with `auth.kerberos.password`. |
| `auth.kerberos.password` | none | No | The principal's password, used only when no keytab is set. |
| `auth.kerberos.strip_port` | `true` | No | Strip the port from the service principal name. |
| `auth.kerberos.use_canonical_hostname` | `false` | No | Resolve the target host to its canonical name before building the service principal name. |
| `auth.kerberos.krb5_conf` | none | No | Path to a `krb5.conf`. Applied only when `java.security.krb5.conf` is not already set. |
| `auth.kerberos.debug` | `false` | No | Enable `Krb5LoginModule` debug output. |
| `auth.oauth.client_id` | none | No | Azure ACS app-only OAuth client ID. Setting it enables OAuth. |
| `auth.oauth.client_secret` | none | No | OAuth client secret. |
| `auth.oauth.tenant` | none | No | Tenant name, without `.sharepoint.com`. |
| `auth.oauth.realm` | none | No | Azure AD realm/directory ID. |

**Exactly one** of `auth.kerberos.principal`, `auth.ntlm.user`, `auth.oauth.client_id` may be set - see
[Authentication](#authentication) below for why.

### List

| Parameter | Default | Req'd | Description |
|---|---|---|---|
| `list.items.number_per_page` | `100` | No | Page size for `GetListItems`. |
| `list.item.content.include_fields` | none (no include filter) | No | Comma-separated field names; if set, only these list-item fields are concatenated into `content`. |
| `list.item.content.exclude_fields` | none (merged with 61 built-in excludes) | No | Comma-separated field-name patterns (each treated as a regex) excluded from `content`, in addition to the built-in exclusions. |
| `list.is_sub_page` | `false` | No | Treat list items as SitePages/wiki subpages, affecting paging fallback and the web-link shape. |

### HTTP

| Parameter | Default | Req'd | Description |
|---|---|---|---|
| `http.connection_timeout` | `30000` (ms) | No | HTTP connect timeout; also used as the connection-request/pool-wait timeout. |
| `http.socket_timeout` | `30000` (ms) | No | HTTP socket (read) timeout. |
| `proxy_host` | none | No | HTTP proxy host. |
| `proxy_port` | `-1` (no proxy) | No (needs `proxy_host`) | HTTP proxy port. |

### Filtering & Content

| Parameter | Default | Req'd | Description |
|---|---|---|---|
| `include_pattern` | none | No | Regex an item's URL-ish value must match to be crawled. See [include_pattern / exclude_pattern](#include_pattern--exclude_pattern) for what value that is. |
| `exclude_pattern` | none | No | Regex that excludes a matching item from being crawled. |
| `supported_mimetypes` | `.*` | No | Comma-separated regexes a file's MIME type must match at least one of to be crawled. |
| `max_content_length` | `-1` (no limit) | No | Maximum file size in bytes; an over-limit file is skipped, not failed. |
| `extractor_name` | `tikaExtractor` | No | Fallback extractor component used only for a MIME type the extractor factory does not map. See [extractor_name](#extractor_name). |

### Behaviour

| Parameter | Default | Req'd | Description |
|---|---|---|---|
| `sp.version` | none (⇒ SharePoint Online / 2016+ REST dialect) | No | Set to `2013` to switch to the XML/Atom, `GetXxxByServerRelativeUrl` API family for SharePoint 2013. See [Limitations](#limitations). |
| `retry_limit` | `2` | No | Max retries per crawl unit on a SharePoint server/client exception. |
| `role.skip` | `false` | No | Skip fetching per-item permissions entirely. See [Permissions](#permissions). |
| `ignore_error` | `false` | No | Log and skip a file's content-extraction failure instead of failing the crawl target. See [ignore_error](#ignore_error) for why the default differs from sibling plugins. |
| `default_permissions` | none | No | Comma-separated permission strings merged into every document's role list in addition to whatever SharePoint returned. |
| `delete_old_docs` | core default `true` | No | Whether documents not refreshed this run are deleted. This plugin forces it to `false` for the current run when any crawl target failed, regardless of what is configured, so a partial crawl never deletes documents the failure prevented it from re-seeing. |
| `number_of_threads` | `1` (no thread pool) | No | How many crawl targets are worked on at once, capped at twice the processor count. See [number_of_threads](#number_of_threads). |
| `script_type` | `groovy` (`Constants.LEGACY_SCRIPT`) | No | Script engine for the data-config Script (inherited from `AbstractDataStore`). |
| `readInterval` | `0` (ms) | No | Sleep between successive crawl results (inherited from `AbstractDataStore`; note the camelCase spelling, unlike every other parameter here). |

## Script Variables

### Fixed keys

| Key | List item (`ItemCrawl`) | Doclib file (`FolderCrawl`→`FileCrawl`) | Attachment (`ItemAttachmentsCrawl`→`FileCrawl`) |
|---|---|---|---|
| `url` | web link | file URL | file URL |
| `host` | hostname | hostname | hostname |
| `site` | server-relative path (`FileRef`) | server-relative path | server-relative path |
| `title` | `Title` field, else `FileLeafRef`/filename | doclib file's own `Title` list value if present, else filename | filename |
| `titleWithListName` | `"[listName] title"` | `"[listName] filename"`, or just the filename (list name is always empty for a doclib crawl) | `"[listName] filename"` |
| `listName` | list display name, or `""` | always `""` | actual list name |
| `content` | concatenation of field values | extracted text | extracted text |
| `digest` | abbreviated `content` | abbreviated `content` | abbreviated `content` |
| `content_length` | `content.length()` | `content.length()` | `content.length()` |
| `last_modified` | from the listing | from the listing | from the listing |
| `created` | from the listing | from the listing | from the listing |
| `mimetype` | always `text/html` | detected | detected |
| `filetype` | derived from `mimetype` | derived from `mimetype` | derived from `mimetype` |
| `role` | permission list, only if non-empty | permission list, only if non-empty | permission list, only if non-empty |
| `list_name` | present | **absent** | present |
| `list_id` | present | **absent** | present |
| `item_id` | present | **absent** | present |

**`content_length` is `content.length()` - the character count (UTF-16 code units) of the extracted
or concatenated text, not the file's byte size.** This is unlike `file.size` in the Box, Google
Drive and Dropbox connectors, which is the actual byte size from each service's own file metadata.
Do not compare this plugin's `content_length` against those.

`ItemCrawl` sets both `listName` (camelCase) and `list_name` (snake_case) to the same value - a
genuine redundancy, not two different pieces of data.

### Dynamic keys: `val_*`

Every key of a list item's `FieldValuesAsText` (the raw field-value map SharePoint returns for that
item, including OData metadata keys such as `odata.metadata`) is exposed under two names: once
unprefixed - only if that name is not already one of the fixed keys above - and once with a `val_`
prefix, unconditionally, e.g. a `Status` field becomes both `Status` and `val_Status`.

**`val_*` keys exist only on the list-item crawl path (`ItemCrawl`).** A document-library file
(`FolderCrawl`→`FileCrawl`) or a list-item attachment (`ItemAttachmentsCrawl`→`FileCrawl`) never
produces any `val_*` key - `FileCrawl` only pulls `Title`/`Description`/`Keywords` out of its own
list-values map, and never applies the `val_` prefix.

## Authentication

Three authentication methods are available, and **exactly one may be configured**. Setting more
than one of `auth.kerberos.principal`, `auth.ntlm.user` and `auth.oauth.client_id` fails the data
config job with a validation error before any request is made. That is deliberate: only one
credential is registered with the HTTP client, and the scope it is registered under matches a
`Negotiate` challenge as readily as an `NTLM` one, so the combination would otherwise produce 401s
that nothing in the log explains.

### NTLM

```
auth.ntlm.user={Name of SharePoint User}
auth.ntlm.password={Password}
auth.ntlm.domain={Windows domain. Optional; unset by default.}
auth.ntlm.workstation={Workstation name sent in the NTLM negotiation. Optional; unset by default.}
```

`auth.ntlm.domain` and `auth.ntlm.workstation` both default to unset, which builds exactly the
credential this data store has always built. Writing the domain into the username as
`DOMAIN\user` keeps working unchanged - Apache HttpClient passes that string through as the user
name without splitting it, so whether it is accepted is up to the server. Setting
`auth.ntlm.domain` sends the domain as its own NTLM field instead, which is what a server that
rejects the combined form wants.

### Kerberos (SPNEGO)

**Supported envelope: a single crawler JVM, a single `krb5.conf` per Fess instance, keytab or
password, no delegation, no channel binding, and mutually exclusive with NTLM and OAuth.** Anything
outside that is not supported.

```
auth.kerberos.principal={Client principal, written as user@REALM. Setting it is what enables Kerberos.}
auth.kerberos.keytab={Path to a keytab holding a key for the principal. Mutually exclusive with auth.kerberos.password.}
auth.kerberos.password={The principal's password. Used only when no keytab is set.}
auth.kerberos.strip_port={true or false. Strip the port from the service principal name. Default is true.}
auth.kerberos.use_canonical_hostname={true or false. Resolve the target host to its canonical name for the service principal name. Default is false.}
auth.kerberos.krb5_conf={Path to a krb5.conf. Applied only when java.security.krb5.conf is not already set.}
auth.kerberos.debug={true or false. Krb5LoginModule debug output. Default is false.}
```

- **`krb5.conf` belongs in `jvm.crawler.options`**, as
  `-Djava.security.krb5.conf=/path/to/krb5.conf`. Data-store crawling runs in the crawler **child
  process**, so setting it anywhere that only affects the webapp has no effect, and a webapp
  restart does not pick a change up - the crawler job has to run again. `auth.kerberos.krb5_conf`
  is a convenience for the case where nothing has set the property: it **never overwrites an
  already-set value**, because the property is JVM-global and one crawler JVM runs every data
  config of a crawl job. When it declines to overwrite, it logs a warning naming both paths.
- **Put `udp_preference_limit = 1` in `krb5.conf`'s `[libdefaults]`.** Without it the JDK tries
  UDP first, and when the KDC does not answer - it is unreachable, a firewall is dropping UDP 88,
  or the reply exceeds the datagram size - it retries three times at thirty seconds each *before*
  falling back to TCP. A crawl that looks hung for a minute and a half per authentication, with
  nothing in the log, is usually this.
- **Always write the principal as `user@REALM`.** `default_realm` is JVM-global and several
  SharePoint farms in different realms have to share one `krb5.conf`, so a bare `user` resolves
  against whichever realm that file happens to name.
- **`auth.kerberos.use_canonical_hostname` defaults to `false`**, deliberately unlike Apache
  HttpClient's own default. With it on, the target host is put through reverse DNS before the
  service principal name is built, which under alternate access mappings or behind a load balancer
  produces a name no SPN is registered for - and the resulting failure says nothing about DNS. Turn
  it on only if the SPN really is registered against the canonical name.
- **IIS Extended Protection set to `tokenChecking=Require` cannot work.** Neither Apache HttpClient
  4.5 nor 5.x supports channel binding. IIS defaults this to `None`, so it is usually not hit, and
  there is no workaround when it is.
- **The ticket is obtained once, when the crawl's HTTP client is built, and is never renewed.** A
  crawl that runs longer than the ticket lifetime starts failing to authenticate partway through.
- **`auth.kerberos.password` is stored and displayed in clear text**, exactly as
  `auth.ntlm.password` already is. Fess has no masking mechanism for data-store handler parameters;
  the data config edit screen renders them as a plain text area. Prefer `auth.kerberos.keytab`,
  and give the keytab file restrictive permissions.
- `auth.kerberos.debug=true` makes `Krb5LoginModule` write to the crawler process's standard
  output, not to the Fess log.

### OAuth (Azure ACS app-only)

```
auth.oauth.client_id={OAuth client ID}
auth.oauth.client_secret={OAuth client secret}
auth.oauth.tenant={Tenant name, without .sharepoint.com}
auth.oauth.realm={Azure AD realm/directory ID}
```

Setting `auth.oauth.client_id` enables a client-credentials (app-only) flow against the Windows
Azure Access Control Service, `https://accounts.accesscontrol.windows.net/{realm}/tokens/OAuth/2`.
The access token is fetched once, when the crawl's HTTP client is built, applied as a `Bearer`
`Authorization` header on every request, and refreshed and retried once if a request comes back
401. **Microsoft has deprecated ACS and scheduled it for retirement**; this plugin logs a warning
to that effect on every OAuth-configured crawl. There is no Entra ID app-registration
(certificate or client-secret) flow implemented in this plugin - only legacy ACS app-only auth.

Only `auth.oauth.client_id`'s presence is checked before OAuth is wired up; `client_secret`,
`tenant` and `realm` are read unconditionally and can silently be blank if omitted, which breaks
token acquisition with no dedicated validation message.

**`sp.version=2013` and OAuth have never worked together.** Every SharePoint 2013 API call this
plugin makes goes through the XML/Atom client, and no code path in that client attaches an OAuth
token to a request - so with both set, every request is sent unauthenticated. The crawl logs a
warning saying exactly this and naming `auth.ntlm.*` as the alternative; it does not fail the job.
Use `auth.ntlm.*` for SharePoint 2013.

## Permissions

`role.skip=true` (default `false`) skips fetching per-item permissions entirely: no
`GetListItemRole` call is made, no `role` key is ever set for the item, and the document ends up
carrying only the data config's static Permission setting and, if configured, `default_permissions`
- no SharePoint-derived permission reaches it at all.

When roles are fetched, SharePoint's own users, security groups and SharePoint groups are expanded
and mapped to Fess search roles:

- An **on-premises AD** account or group (login name containing a backslash, not starting with an
  Azure claim prefix) is mapped via the standard AD user/group role helpers.
- An **Azure AD (Entra ID)** account (login name starting with `i:0#.f|membership|`) is mapped
  **twice** - once by its full Azure claim value, once by the AD-account portion before `@` in that
  claim - so both an Entra-ID-style and an AD-style role are added for the same user. A security
  group flagged as Azure (by one of several claim-style prefixes, including the special
  `spo-grid-all-users` "everyone" group) is mapped the same way, under both forms.
- A **SharePoint group** has its own membership (users, security groups, nested groups) expanded
  recursively, with a visited-group guard to stop infinite recursion between groups that contain
  each other.

`default_permissions` (comma-separated) is merged in **after** all of the above, and applies even
when SharePoint returned no role for the item at all - the case both `role.skip=true` and "SharePoint
returned nothing" produce. The final role list is the union of the data config's static Permission
setting, the SharePoint-derived roles (unless skipped), and `default_permissions`, de-duplicated.

## Additional Notes

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

### site.crawl_subsites / site.max_depth

`site.crawl_subsites` (default `false`) makes a full site crawl - one where neither
`site.list_name` nor `site.doclib_path` is set - recurse into the site's subsites, discovered via
`_api/web/webinfos`. **Leaving it unset keeps the crawl issuing exactly the same requests it always
has, including never requesting `webinfos` at all.**

A subsite's documents land in the same data config as the root site's, under their own
server-relative paths - there is nothing in the index that marks a document as having come from a
subsite rather than the root.

`site.max_depth` (default `10`) bounds how many subsite hops below the root site are crawled once
`site.crawl_subsites=true`. The root site itself is depth 0, so `site.max_depth=1` crawls the
root's direct children and no further. Setting it below `1` while `site.crawl_subsites=true` turns
the feature back off - no subsite is crawled at all - and is logged as a warning when the crawl
starts.

Only a subsite whose server-relative path lies below the site it was discovered from is crawled. A
farm that reports a child outside that path - a different site collection, the root site
collection, or a path with `..` segments - has that child skipped with a warning.

Turning this on **multiplies the crawl's total time** by roughly the number of subsites discovered
(bounded by `site.max_depth`): each one gets its own full top-level folder listing, list listing,
and (if not at the depth bound) its own `webinfos` call, on top of everything the root site's crawl
already does.

`webinfos` is **not security-trimmed** - it returns every subsite regardless of whether the crawl
account can read it. A subsite the account cannot read answers the first request of its own crawl
with a 403; that is logged as a warning and the subsite is skipped, without retrying it and without
counting it as a crawl failure, because counting it would suppress this data config's
stale-document cleanup entirely. The same applies to a 403 on the `webinfos` listing itself, which
means only that this site's children cannot be enumerated.

Two limits on that, both deliberate. A 403 on the **root site** named by `site.name`/`site.path` is
still a crawl failure - that is a misconfiguration to fix, not a permission boundary to skip.
And only a 403 is skipped, never a **401**: the same credentials serve every site in the crawl, so
a 401 is an authentication problem affecting the whole crawl (an expired Kerberos ticket, a rejected
password, an OAuth token that could not be refreshed) rather than a per-site permission boundary.

### number_of_threads

`number_of_threads` (default `1`) is how many crawl targets are worked on at once. At the default
the crawl runs exactly as it always has: every target is crawled on the crawling thread and **no
thread pool is created at all**.

The value is **capped at twice the processor count** of the machine running Fess, so a data config
cannot ask for more concurrency than the host can serve. A value below `1` - or a blank or
unparseable one - falls back to `1` rather than being honoured or failing the job. A value that was
capped, or one below `1`, is logged with both the requested and the actual value; an unparseable one
logs a warning. **A blank value logs nothing**, because a blank field means the parameter was simply
not set.

The HTTP connection pool is sized to match. This matters because Apache HttpClient allows only 2
connections per route by default and a whole crawl is a single route: without raising it, every
thread past the second would spend the crawl waiting for a connection rather than making requests.

**`readInterval` still paces document hand-off, one document per interval, whatever this is set
to.** Threads make the crawl discover and fetch faster; they do not make documents reach the indexer
faster. That is deliberate: dividing an operator's configured interval by the thread count would
multiply exactly the load they configured that interval to limit. A worker that finishes a document
while the previous ones are still being handed over simply waits.

What raising this **does** multiply is the request rate against SharePoint. The 503 backoff and the
`X-SharePointHealthScore` wait described below are applied per crawl target, on the thread crawling
it, so `n` threads make up to `n` times the requests a single-threaded crawl makes - including
during a period the farm is signalling that it is busy. On an on-premises farm, raise this
gradually.

Two things put a ceiling on what more threads actually buy:

- **The first time each SharePoint group's membership is read, it is read by one thread at a time.**
  Permissions are resolved through a cache shared by the whole crawl, and that cache is guarded by a
  single lock held across the group's member lookups. That lock is what stops one thread from
  handing another a group whose members are still being read - which would index the items that
  group protects with none of its permissions. Once a group is in the cache every later reference to
  it is a cheap lookup, so this is a **cold-cache cost**: a crawl of a site with many distinct groups
  spends its early minutes closer to single-threaded than to `n` threads, and one whose items share a
  handful of groups barely notices. `role.skip=true`, which does not read permissions at all, avoids
  it entirely.
- Discovery is sequential per site: a site's folder and list listings are one crawl target, so the
  threads have nothing to share out until that target has finished and queued what it found.

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

## Limitations

- **No incremental or delta crawl of any kind.** There is no change-token, delta-query, or
  "last modified since" filtering anywhere in this plugin - every run does a full listing of every
  list, folder and file it is configured to reach. `delete_old_docs` (see the Behaviour table
  above) only controls whether documents the current full crawl did not see again are deleted
  afterwards; that is post-hoc cleanup, not incremental fetching.
- **`%` and `#` in file/folder names** are supported on the default (non-`2013`) code path. Only
  SharePoint Server 2019 and Subscription Edition accept those two characters in a name at all
  ([2019 release notes](https://learn.microsoft.com/en-us/sharepoint/what-s-new/new-and-improved-features-in-sharepoint-server-2019));
  2016 [explicitly still rejects them](https://learn.microsoft.com/en-us/sharepoint/what-s-new/new-and-improved-features-in-sharepoint-server-2016)
  and so does 2013. The default code path addresses such a file through the
  `...ByServerRelativePath(decodedUrl=...)` endpoints, which take the decoded path
  ([Microsoft: ResourcePath API](https://learn.microsoft.com/en-us/sharepoint/dev/solution-guidance/supporting-and-in-file-and-folder-with-the-resourcepath-api)),
  and the crawl escapes both characters in the link it indexes the file under.
  **`sp.version=2013` cannot address such a file**: it uses the older
  `...ByServerRelativeUrl(...)` endpoints, which read their argument as an already-encoded URL.
  That is a deliberate limit rather than a gap - a SharePoint 2013 farm cannot hold such a name in
  the first place - so it only matters if `sp.version=2013` is pointed at a 2019 or Subscription
  Edition server, which is not a configuration to use. None of this is exercised against a real
  farm; it is verified against this plugin's mock server and the documentation cited above.
- **IIS Extended Protection `tokenChecking=Require` cannot be supported.** Neither Apache HttpClient
  4.5 nor 5.x implements channel binding, which Extended Protection at `Require` depends on. IIS
  defaults this setting to `None`, so most farms are unaffected, and there is no workaround for a
  farm where it is set to `Require`.
- **Passwords in data-config parameters are stored and displayed in clear text.** This applies to
  `auth.ntlm.password` and `auth.kerberos.password` alike: Fess has no masking mechanism for
  data-store handler parameters, and the data config edit screen renders them in a plain text area.
  Prefer `auth.kerberos.keytab` over `auth.kerberos.password` where Kerberos is available, and give
  the keytab file restrictive permissions.
- **Subsites and managed paths other than `/sites/` and the one set via `site.path` are still not
  discovered on their own** - `site.crawl_subsites` recurses only from the root site you configure,
  and `site.path` reaches exactly the one managed path you set, not every managed path on the farm.

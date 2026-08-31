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

**`read_interval` still paces document hand-off, one document per interval, whatever this is set
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

### Authentication

Three authentication methods are available, and **exactly one may be configured**. Setting more
than one of `auth.kerberos.principal`, `auth.ntlm.user` and `auth.oauth.client_id` fails the data
config job with a validation error before any request is made. That is deliberate: only one
credential is registered with the HTTP client, and the scope it is registered under matches a
`Negotiate` challenge as readily as an `NTLM` one, so the combination would otherwise produce 401s
that nothing in the log explains.

#### NTLM

```
auth.ntlm.user={Name of SharePoint User}
auth.ntlm.password={Password}
auth.ntlm.domain={Windows domain. Optional; unset by default.}
auth.ntlm.workstation={Workstation name sent in the NTLM negotiation. Optional; unset by default.}
```

`auth.ntlm.domain` and `auth.ntlm.workstation` are new and both default to unset, which builds
exactly the credential this data store has always built. Writing the domain into the username as
`DOMAIN\user` keeps working unchanged - Apache HttpClient passes that string through as the user
name without splitting it, so whether it is accepted is up to the server. Setting
`auth.ntlm.domain` sends the domain as its own NTLM field instead, which is what a server that
rejects the combined form wants.

#### Kerberos (SPNEGO)

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
auth.ntlm.domain={Windows domain for NTLM. Optional; unset by default. See "Authentication" above.}
auth.ntlm.workstation={Workstation name sent in the NTLM negotiation. Optional; unset by default.}
auth.kerberos.principal={Kerberos client principal, written as user@REALM. Setting it enables Kerberos and excludes auth.ntlm.* and auth.oauth.*. See "Authentication" above.}
auth.kerberos.keytab={Path to a keytab holding a key for the principal.}
auth.kerberos.password={The principal's password. Used only when no keytab is set. Stored and displayed in clear text.}
auth.kerberos.strip_port={true or false. Default is true.}
auth.kerberos.use_canonical_hostname={true or false. Default is false.}
auth.kerberos.krb5_conf={Path to a krb5.conf. Applied only when java.security.krb5.conf is not already set.}
auth.kerberos.debug={true or false. Krb5LoginModule debug output. Default is false.}
site.crawl_subsites={true or false. Recurse into the site's subsites. Only applies to a full site crawl (site.list_name/site.doclib_path unset). Default is false. See "site.crawl_subsites / site.max_depth" above.}
site.max_depth={How many subsite hops below the root site site.crawl_subsites may recurse. The root is depth 0. Default is 10.}
number_of_threads={How many crawl targets are worked on at once. Default is 1 (no thread pool at all), capped at twice the processor count. See "number_of_threads" above.}
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
auth.ntlm.domain={Windows domain for NTLM. Optional; unset by default. See "Authentication" above.}
auth.ntlm.workstation={Workstation name sent in the NTLM negotiation. Optional; unset by default.}
auth.kerberos.principal={Kerberos client principal, written as user@REALM. Setting it enables Kerberos and excludes auth.ntlm.* and auth.oauth.*. See "Authentication" above.}
auth.kerberos.keytab={Path to a keytab holding a key for the principal.}
auth.kerberos.password={The principal's password. Used only when no keytab is set. Stored and displayed in clear text.}
auth.kerberos.strip_port={true or false. Default is true.}
auth.kerberos.use_canonical_hostname={true or false. Default is false.}
auth.kerberos.krb5_conf={Path to a krb5.conf. Applied only when java.security.krb5.conf is not already set.}
auth.kerberos.debug={true or false. Krb5LoginModule debug output. Default is false.}
number_of_threads={How many crawl targets are worked on at once. Default is 1 (no thread pool at all), capped at twice the processor count. See "number_of_threads" above.}
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

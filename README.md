SharePoint Data Store for Fess
[![Java CI with Maven](https://github.com/codelibs/fess-ds-sharepoint/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-ds-sharepoint/actions/workflows/maven.yml)
==========================

## Overview

SharePoint Data Store for Fess.

## Download

See [Maven Repository](http://central.maven.org/maven2/org/codelibs/fess/fess-ds-sharepoint/).

## Installation

1. Download fess-ds-sharepoint-X.X.X.jar
2. Copy fess-ds-sharepoint-X.X.X.jar to $FESS\_HOME/app/WEB-INF/lib or /usr/share/fess/app/WEB-INF/lib

## Crawling Setting

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

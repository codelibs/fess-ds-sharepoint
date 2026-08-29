/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfolder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;

public class GetFolder2013ResponseTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_utcTimestampsAreNotShiftedByTheJvmTimeZone() {
        // The 'Z' in the pattern is a literal, so without an explicit zone the instant is read in
        // whatever zone the JVM happens to run in.
        final TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));

            final Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("UniqueId", "aaaaaaaa-0000-0000-0000-000000000000");
            dataMap.put("Name", "docs");
            dataMap.put("ServerRelativeUrl", "/sites/test/docs");
            dataMap.put("TimeCreated", "2026-01-01T00:00:00Z");
            dataMap.put("TimeLastModified", "2026-01-02T00:00:00Z");

            final GetFolder2013Response response = GetFolder2013Response.build(dataMap);

            assertEquals("TimeCreated must be read as UTC, not the JVM default zone", Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(),
                    response.getCreated().getTime());
            assertEquals("TimeLastModified must be read as UTC, not the JVM default zone",
                    Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(), response.getModified().getTime());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void test_folderSurvivesADateItCannotParse() {
        // A folder's date failing to parse used to throw and abort the whole subtree at the
        // caller's first call (see FolderCrawl, which reads this folder before it lists anything
        // inside it).
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("UniqueId", "aaaaaaaa-0000-0000-0000-000000000000");
        dataMap.put("Name", "docs");
        dataMap.put("ServerRelativeUrl", "/sites/test/docs");
        dataMap.put("TimeCreated", "not-a-date");
        dataMap.put("TimeLastModified", "not-a-date");
        dataMap.put("ItemCount", 5);

        final GetFolder2013Response response = GetFolder2013Response.build(dataMap);

        assertNotNull("the folder must still be returned", response);
        assertEquals("the folder's item count must survive", 5, response.getItemCount());
        assertNull("an unparseable date is dropped, not fatal", response.getCreated());
        assertNull("an unparseable date is dropped, not fatal", response.getModified());
    }

    @Test
    public void test_aMalformedCreationDateDoesNotCostTheModificationDate() {
        // The two dates used to share one try block, so the first one to fail took the second
        // one down with it - including the modification date, which is the one the index uses.
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("UniqueId", "aaaaaaaa-0000-0000-0000-000000000000");
        dataMap.put("Name", "docs");
        dataMap.put("ServerRelativeUrl", "/sites/test/docs");
        dataMap.put("TimeCreated", "not-a-date");
        dataMap.put("TimeLastModified", "2026-01-02T00:00:00Z");

        final GetFolder2013Response response = GetFolder2013Response.build(dataMap);

        assertNull("the unparseable creation date is dropped", response.getCreated());
        assertEquals("the valid modification date must survive the unparseable creation date",
                Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(), response.getModified().getTime());
    }

    @Test
    public void test_folderSurvivesAMissingModifiedDate() {
        // Unlike TimeCreated, TimeLastModified was parsed unconditionally: a folder with no
        // TimeLastModified element at all fed SimpleDateFormat.parse(null), which throws a
        // NullPointerException rather than a ParseException, bypassing the catch entirely.
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("UniqueId", "aaaaaaaa-0000-0000-0000-000000000000");
        dataMap.put("Name", "docs");
        dataMap.put("ServerRelativeUrl", "/sites/test/docs");
        dataMap.put("ItemCount", 5);

        final GetFolder2013Response response = GetFolder2013Response.build(dataMap);

        assertNotNull("the folder must still be returned", response);
        assertNull("a missing TimeLastModified must not throw", response.getModified());
    }
}

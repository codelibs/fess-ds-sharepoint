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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;

public class GetFilesResponseTest extends UnitDsTestCase {

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
            dataMap.put("Name", "report.pdf");
            dataMap.put("ServerRelativeUrl", "/sites/test/report.pdf");
            dataMap.put("TimeCreated", "2026-01-01T00:00:00Z");
            dataMap.put("TimeLastModified", "2026-01-02T00:00:00Z");

            final GetFilesResponse.DocLibFile docLibFile = GetFilesResponse.createDocLibFile(dataMap);

            assertEquals("TimeCreated must be read as UTC, not the JVM default zone", Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(),
                    docLibFile.getCreated().getTime());
            assertEquals("TimeLastModified must be read as UTC, not the JVM default zone",
                    Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(), docLibFile.getModified().getTime());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void test_aMalformedCreationDateDoesNotCostTheModificationDate() {
        // The two dates used to share one try block, so the first one to fail took the second
        // one down with it - including the modification date, which is the one the index uses.
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("Name", "report.pdf");
        dataMap.put("ServerRelativeUrl", "/sites/test/report.pdf");
        dataMap.put("TimeCreated", "not-a-date");
        dataMap.put("TimeLastModified", "2026-01-02T00:00:00Z");

        final GetFilesResponse.DocLibFile docLibFile = GetFilesResponse.createDocLibFile(dataMap);

        assertNull("the unparseable creation date is dropped", docLibFile.getCreated());
        assertEquals("the valid modification date must survive the unparseable creation date",
                Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(), docLibFile.getModified().getTime());
    }

    @Test
    public void test_fileSurvivesAMissingDate() {
        // DocumentUtil.getValue returns null when a key is absent; a file with no timestamps at
        // all must still be listed rather than throwing out of the whole page.
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("Name", "report.pdf");
        dataMap.put("ServerRelativeUrl", "/sites/test/report.pdf");

        final GetFilesResponse.DocLibFile docLibFile = GetFilesResponse.createDocLibFile(dataMap);

        assertEquals("the file name must still be read despite the missing dates", "report.pdf", docLibFile.getFileName());
        assertEquals("the server relative URL must still be read despite the missing dates", "/sites/test/report.pdf",
                docLibFile.getServerRelativeUrl());
        assertNull("a missing date must not throw", docLibFile.getCreated());
        assertNull("a missing date must not throw", docLibFile.getModified());
    }
}

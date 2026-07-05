package org.hospital.avicennaarchive.extract;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the site-specific mapping of logical field/tab names to the
 * accessible-name regex patterns actually used by the running Avicenna
 * client. These names are not knowable in advance - run
 * {@code TreeDumperCli} against the live client first and adjust
 * tab-selectors.json to match what it reports before trusting extraction.
 */
public class TabSelectors {
    public Map<String, String> demographicFields = new LinkedHashMap<>();
    public Map<String, String> tabButtons = new LinkedHashMap<>();
    public String patientNoFieldPattern;
    public String searchButtonPattern;
    public long searchTimeoutMs = 15000;
    public long tabLoadTimeoutMs = 8000;

    public static TabSelectors loadDefault() throws IOException {
        try (var in = TabSelectors.class.getResourceAsStream("/tab-selectors.json")) {
            if (in == null) {
                throw new IOException("tab-selectors.json not found on classpath");
            }
            return new ObjectMapper().readValue(in, TabSelectors.class);
        }
    }

    public static TabSelectors load(File file) throws IOException {
        return new ObjectMapper().readValue(file, TabSelectors.class);
    }
}

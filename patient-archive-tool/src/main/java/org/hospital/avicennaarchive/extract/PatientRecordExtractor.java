package org.hospital.avicennaarchive.extract;

import com.sun.jna.Pointer;
import org.hospital.avicennaarchive.accessbridge.AccessBridgeSession;
import org.hospital.avicennaarchive.model.PatientRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the already-open Avicenna client the same way a doctor would:
 * types the patient ID into the "Patient No" field, triggers the search,
 * waits for the record to load, then walks each clinical tab reading its
 * table contents into a structured record.
 *
 * This assumes a doctor is already logged in and the "Outpatient / Open
 * Patient Protocol" screen (or equivalent) is the active window - it does
 * not perform authentication itself.
 */
public class PatientRecordExtractor {

    private final AccessBridgeSession session;
    private final TabSelectors selectors;

    public PatientRecordExtractor(AccessBridgeSession session, TabSelectors selectors) {
        this.session = session;
        this.selectors = selectors;
    }

    public PatientRecord extract(String patientId) {
        Pointer root = session.rootContext();

        Pointer patientNoField = session.findByName(root, selectors.patientNoFieldPattern, null);
        if (patientNoField == null) {
            throw new IllegalStateException("Could not locate the Patient No field. " +
                "Run TreeDumperCli against the live window and fix patientNoFieldPattern in tab-selectors.json.");
        }
        session.setText(patientNoField, patientId);

        Pointer searchButton = session.findByName(root, selectors.searchButtonPattern, "push button");
        if (searchButton != null) {
            session.invokeAction(searchButton, "click");
        } else {
            session.invokeAction(patientNoField, "click");
        }

        Pointer nameField = session.findByName(root, selectors.demographicFields.get("nameEnglish"), null);
        AccessBridgeSession.waitUntil(
            () -> nameField != null && !session.readText(nameField).trim().isEmpty(),
            selectors.searchTimeoutMs, 300);

        if (nameField == null || session.readText(nameField).trim().isEmpty()) {
            throw new IllegalStateException("Patient " + patientId +
                " did not load within " + selectors.searchTimeoutMs + "ms - check the ID exists and is visible " +
                "to this doctor account, or that the search trigger (searchButtonPattern) actually fired the lookup.");
        }

        PatientRecord record = new PatientRecord();
        record.patientNo = patientId;
        record.extractedAtIso = Instant.now().toString();

        for (Map.Entry<String, String> field : selectors.demographicFields.entrySet()) {
            Pointer fieldAc = session.findByName(root, field.getValue(), null);
            record.demographics.put(field.getKey(), fieldAc == null ? "" : session.readText(fieldAc));
        }

        for (Map.Entry<String, String> tab : selectors.tabButtons.entrySet()) {
            record.tabs.put(tab.getKey(), extractTab(root, tab.getValue()));
        }

        return record;
    }

    private List<Map<String, String>> extractTab(Pointer root, String tabNamePattern) {
        Pointer tabButton = session.findByName(root, tabNamePattern, null);
        if (tabButton == null) {
            return Collections.emptyList();
        }
        session.invokeAction(tabButton, "click");
        AccessBridgeSession.waitUntil(() -> true, Math.min(selectors.tabLoadTimeoutMs, 1000), 1000);

        List<Pointer> tables = session.findAllByRole(root, "table");
        List<Map<String, String>> rows = new ArrayList<>();
        for (Pointer table : tables) {
            String[][] cells = session.readTable(table);
            for (String[] row : cells) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int c = 0; c < row.length; c++) {
                    rowMap.put("col" + c, row[c] == null ? "" : row[c]);
                }
                rows.add(rowMap);
            }
        }
        return rows;
    }
}

package org.hospital.avicennaarchive.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Structured archival record for one patient, one field per demographic + one entry per tab scraped. */
public class PatientRecord {
    public String patientNo;
    public Map<String, String> demographics = new LinkedHashMap<>();
    public Map<String, List<Map<String, String>>> tabs = new LinkedHashMap<>();
    public List<String> attachments = new java.util.ArrayList<>();
    public String extractedAtIso;
}

package org.hospital.avicennaarchive.cli;

import org.hospital.avicennaarchive.accessbridge.AccessBridgeSession;
import org.hospital.avicennaarchive.accessbridge.NativeLibs;
import org.hospital.avicennaarchive.archive.ArchiveWriter;
import org.hospital.avicennaarchive.extract.PatientRecordExtractor;
import org.hospital.avicennaarchive.extract.TabSelectors;
import org.hospital.avicennaarchive.model.PatientRecord;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Usage: java -jar patient-archive-tool.jar <patientId> [outputDir] [tab-selectors.json] [windowTitleSubstring]
 *
 * Requires: a doctor already logged into the Avicenna client on this machine,
 * with Java Access Bridge enabled ('jabswitch /enable'), and tab-selectors.json
 * verified against the real component tree (see TreeDumperCli) before relying
 * on the output.
 */
public class PatientArchiverCli {
    public static void main(String[] args) throws Exception {
        NativeLibs.ensureAccessBridgeOnPath();
        if (args.length < 1) {
            System.err.println("Usage: patient-archive-tool <patientId> [outputDir] [selectorsFile] [windowTitle]");
            System.exit(2);
        }
        String patientId = args[0];
        Path outputDir = Paths.get(args.length > 1 ? args[1] : "archive");
        TabSelectors selectors = args.length > 2
            ? TabSelectors.load(new File(args[2]))
            : TabSelectors.loadDefault();
        String windowTitle = args.length > 3 ? args[3] : "Avicenna";

        AccessBridgeSession session = new AccessBridgeSession();
        session.init();
        System.out.println("Attaching to window containing: " + windowTitle);
        if (!session.attachToWindow(windowTitle, 20000)) {
            System.err.println("Could not attach to the Avicenna window. Is it open and is Java Access " +
                "Bridge enabled ('jabswitch /enable')?");
            System.exit(1);
        }

        try {
            PatientRecordExtractor extractor = new PatientRecordExtractor(session, selectors);
            PatientRecord record = extractor.extract(patientId);

            ArchiveWriter writer = new ArchiveWriter(outputDir);
            Path patientDir = writer.write(record);

            System.out.println("Archived patient " + patientId + " to " + patientDir.toAbsolutePath());
            System.out.println("Tabs captured: " + record.tabs.keySet());
        } finally {
            session.close();
        }
    }
}

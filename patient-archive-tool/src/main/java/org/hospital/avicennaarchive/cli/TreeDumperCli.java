package org.hospital.avicennaarchive.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.hospital.avicennaarchive.accessbridge.AccessBridgeSession;
import org.hospital.avicennaarchive.accessbridge.NativeLibs;
import org.hospital.avicennaarchive.model.AccessibleNode;

import java.io.File;

/**
 * Run this FIRST, on the doctor's workstation, before attempting any extraction.
 * It attaches to the running Avicenna window and dumps its full accessible
 * component tree (names, roles, states) to a JSON file, so you can find the
 * real accessible names of the Patient No field, search button, and tab
 * buttons and put them in tab-selectors.json - none of the patterns shipped
 * in this repo have been verified against a live Avicenna client.
 *
 * Usage: java -cp patient-archive-tool.jar org.hospital.avicennaarchive.cli.TreeDumperCli
 *            [windowTitleSubstring] [outputFile]
 */
public class TreeDumperCli {
    public static void main(String[] args) throws Exception {
        NativeLibs.ensureAccessBridgeOnPath();
        String titleSubstring = args.length > 0 ? args[0] : "Avicenna";
        String outputFile = args.length > 1 ? args[1] : "accessible-tree.json";

        AccessBridgeSession session = new AccessBridgeSession();
        session.init();
        System.out.println("Looking for a window with title containing: " + titleSubstring);
        if (!session.attachToWindow(titleSubstring, 20000)) {
            System.err.println("Could not find/attach to a window whose title contains \"" + titleSubstring + "\".");
            System.err.println("Visible top-level windows the bridge can see right now:");
            for (String w : session.describeTopLevelWindows()) {
                System.err.println("  " + w);
            }
            System.err.println();
            System.err.println("If the Avicenna window is listed with [java=false], the bridge is not active in it: ");
            System.err.println("  enable it on Avicenna's JRE ('jre8\\bin\\jabswitch.exe /enable') and RESTART the client.");
            System.err.println("If the Avicenna window is not listed at all, this tool and Avicenna are likely running");
            System.err.println("  at different privilege levels - run both as Administrator (or both normally).");
            System.exit(1);
        }

        AccessibleNode tree = session.dumpTree(session.rootContext(), 40);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
            .writeValue(new File(outputFile), tree);

        System.out.println("Wrote accessible tree to " + outputFile);
        session.close();
    }
}

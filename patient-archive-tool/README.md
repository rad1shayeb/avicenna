# Patient Archive Tool

Extracts one patient's record out of the Avicenna HIS client, by patient ID,
into a structured archive (`data.json` + attachments), for long-term
archiving purposes. It automates the same clicks a doctor already makes in
the desktop client — enter the patient ID, wait for the record to load, walk
through each clinical tab — using Java Access Bridge (JAB) to read/drive the
Swing UI. It does not touch the database or any vendor API; there isn't one
available to us.

## Status: unverified against the real client

This was built without access to a Windows machine or a running Avicenna
instance, so nothing here has been exercised against the live app. Two parts
need on-site verification before you trust any output:

1. **The native struct layouts** in
   `accessbridge/WindowsAccessBridge.java` (`AccessibleContextInfo`,
   `AccessibleTableInfo`, `AccessibleTableCellInfo`, etc.) reflect the
   Access Bridge API that's been stable since JDK 6, but JAB's ABI has had
   point revisions. Before running this for real, diff these structs against
   `AccessBridgeCallbacks.h` / `AccessBridgePackages.h` shipped under
   `<jdk>/include` on the machine Avicenna's JRE came from. A struct mismatch
   here doesn't throw an exception — it corrupts native memory silently.
2. **The accessible-name patterns** in `src/main/resources/tab-selectors.json`
   (e.g. `"Patient No.*"`, tab button names) are guesses based on the labels
   visible in a screenshot of the client. Swing accessible names don't have
   to match visible label text. Run `TreeDumperCli` (below) against the real,
   logged-in client and fix `tab-selectors.json` to match what it reports
   before running the archiver for real.

## One-time setup on the doctor's workstation

1. Enable Java Access Bridge for the JRE Avicenna runs under:
   ```
   <jre>\bin\jabswitch.exe /enable
   ```
   Restart the Avicenna client afterwards. Without this, `WindowsAccessBridge-64.dll`
   has nothing to talk to.
2. Confirm `WindowsAccessBridge-64.dll` and `JavaAccessBridge-64.dll` are on
   the `PATH` (they ship with the JRE under `bin/`, `jabswitch /enable` wires
   them up automatically).
3. Build: `mvn package` (requires network access to Maven Central for the
   JNA/Jackson dependencies, or a local mirror if the hospital network is
   restricted). Produces `target/patient-archive-tool.jar`.

## Workflow

1. **Discover real component names** (do this once, and again after any
   Avicenna client upgrade):
   ```
   java -cp target/patient-archive-tool.jar org.hospital.avicennaarchive.cli.TreeDumperCli "Avicenna" tree.json
   ```
   Open `tree.json`, find the Patient No field / search button / tab buttons,
   and update the patterns in `tab-selectors.json` (a copy can be passed
   explicitly; see below).

2. **Archive a patient**, with a doctor already logged in and the patient
   lookup screen open:
   ```
   java -jar target/patient-archive-tool.jar <patientId> archive/ tab-selectors.json "Avicenna"
   ```
   Output: `archive/<patientId>/data.json` (demographics + one row-list per
   clinical tab) and `archive/<patientId>/attachments/` (currently empty —
   attachment capture, e.g. saving rendered forms as PDF via the client's own
   Print action, is not yet implemented; see Known gaps).

## Known gaps / next steps

- **Attachments aren't captured yet.** The "Form" tab renders a printable
  document (see the morgue form in the screenshot this was scoped from);
  saving that to PDF means driving the client's Print action against a
  PDF-writer printer driver (e.g. "Microsoft Print to PDF") and moving the
  resulting file into `attachments/`. Needs the real print dialog's
  accessible tree to wire up.
- **Search-trigger reliability.** `setTextContents` sets a field's value
  directly; it does not fire whatever key/action listener the app binds to
  Enter. The extractor tries a nearby "search/find" button first and falls
  back to invoking `click` on the field itself — verify which one (or
  neither) actually triggers the lookup on the real screen, using
  `TreeDumperCli` to find the right button if the fallback doesn't work.
- **Table extraction assumes JTable-backed panels.** Tabs whose content
  is a tree, a rendered document (like the morgue form), or a custom
  component instead of a table will return no rows; those need bespoke
  handling once you know what's really under each tab.
- This only reads what the logged-in doctor's account can see. If archiving
  requires access beyond a single doctor's patient panel, that's an access-
  control/authorization discussion with hospital IT, not something to route
  around in this tool.

## Handling of what's extracted

Output lands on local disk only; nothing is transmitted off the workstation.
Treat `archive/` as containing PHI (it does) — access-control and retention
for that directory needs to follow the same policy as any other exported
patient record.

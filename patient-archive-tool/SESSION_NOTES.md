# Session Notes — Avicenna Patient Data Extraction Tool

**Session:** `session_013o2FnHQbjYqofNt7n9oF8B`
**Repo:** `rad1shayeb/avicenna`
**Branch:** `claude/patient-data-extraction-7xu46x`
**Date:** 2026-07-05

---

## 1. Goal

Build a tool that extracts a single patient's record out of the hospital's
Avicenna HIS, **by patient ID**, into a structured archive — for archiving
purposes. One patient at a time, on demand. Project is known to and approved
by hospital IT/administration.

## 2. What the repo actually is (important)

The `main` branch is **not** HIS source code. It's a **client install folder**
for **Datasel Avicenna**, a commercial closed-source Hospital Information System
(Turkish / Middle-East hospital software; this is a Palestine Ministry of Health
deployment — Rafidia Hospital, per the client screenshots).

What's in it:

- `AutoUpdaterClient.jar` + `avicenna.bat` — a launcher/auto-updater
- `Configuration.xml`, `UpdaterConfiguration.xml` — client config pointing at
  internal SVN servers (`10.222.3.x`, `10.221.3.x`) and app servers
- `dsl.jks`, `jssecacerts`, `RenkliRsaPublicKey.pem`, `salt.dat` — crypto material
- `consolelogs/*.txt` — runtime logs

**There is no application source, DB schema, or public API in this repo.** The
real Avicenna code lives on internal SVN and is delivered to the client as
compiled jars at runtime. The system is a Java Swing client that talks to the
server over a **proprietary RPC protocol** (`com.datasel.avicenna.*`), not REST.

### Consequence for the tool

We have **no DB and no API access** — only a doctor's UI login. So the tool
cannot query a database or call an API. It must drive the **same UI a doctor
uses**, via the **Java Access Bridge (JAB)**, which lets an external program
read and operate a Java Swing UI through Windows accessibility.

## 3. Security finding (still open)

`Configuration.xml` contained **plaintext SVN credentials**
(`username="avicenna" password="Av1C3nnA*"`) across ~13 facility blocks,
targeting internal servers — and this was pushed to GitHub.

- **Done:** password redacted from the current `Configuration.xml` (all 82
  occurrences → `REDACTED_ROTATE_THIS_CREDENTIAL`).
- **Still open (not us):** the real password is **still in git history** (earlier
  commits + `main`), and **rotating it on the SVN servers** requires someone with
  admin access to those servers (hospital IT / Datasel). The user does not have
  SVN credentials; the leaked ones must not be used.

## 4. The tool

Location: `patient-archive-tool/` — a Java 8 Maven project.

```
patient-archive-tool/
├─ pom.xml                         # JNA 5.14, Jackson 2.17, shade plugin, --release 8
├─ README.md                       # build/run/verify instructions + known gaps
├─ SESSION_NOTES.md                # this file
├─ tab-selectors.json (resources)  # field/tab accessible-name patterns (GUESSED — see §7)
└─ src/main/java/org/hospital/avicennaarchive/
   ├─ accessbridge/
   │  ├─ WindowsAccessBridge.java  # JNA binding to WindowsAccessBridge-{32,64}.dll + structs
   │  ├─ AccessBridgeSession.java  # attach to window, walk tree, read tables, drive fields, diagnostics
   │  └─ NativeLibs.java           # adds JRE bin + System32/SysWOW64 to jna.library.path
   ├─ model/
   │  ├─ AccessibleNode.java       # detached snapshot of a Swing component subtree
   │  └─ PatientRecord.java        # structured archival record
   ├─ extract/
   │  ├─ TabSelectors.java         # loads the name-pattern config
   │  └─ PatientRecordExtractor.java  # the workflow: enter ID, search, walk tabs, read
   ├─ archive/
   │  └─ ArchiveWriter.java        # writes archive/<patientId>/data.json + attachments/
   └─ cli/
      ├─ TreeDumperCli.java        # DISCOVERY tool — dumps the live UI tree to JSON
      └─ PatientArchiverCli.java   # MAIN entry — archive one patient by ID
```

### How it works
1. Attach to the running Avicenna window via JAB (`AccessBridgeSession`).
2. Type the patient ID into the "Patient No" field, trigger search.
3. Wait for the record to load (name field becomes non-empty).
4. Read the demographic fields, then click through each clinical tab
   (Visits, Diagnosis, Form, Reports, Radiology, Labs, etc.) and read the
   JTable contents into a structured record.
5. Write `archive/<patientId>/data.json` (+ an `attachments/` folder).

## 5. Environment facts (confirmed on the workstation)

- Avicenna client runs on: **`C:\avicennanew\jre8\bin\java.exe`** (64-bit path;
  confirmed via `Win32_Process`).
- The tool is launched with the bundled **`jdk8`** (64-bit: JNA reported
  `win32-x86-64`).
- Install root: `C:\avicennanew` (has `jdk8`, `jre8`, `jars`, `consolelogs`).
- Java Access Bridge was enabled on `jre8` (`jre8\bin\jabswitch.exe /enable`
  → "The Java Access Bridge has been enabled").

## 6. Current status / the blocker

Build works. Class loads. Native DLL loads (after the `jna.library.path` fix).
The tool **sees** the Avicenna window but reports it as **`[java=false]`**:

```
[java=false] Avicenna 2.3.11 [PRODDB]
```

`[java=false]` means the Access Bridge is **not active inside the running
Avicenna process**. The bridge only loads into a JVM **at startup**, and
Avicenna was still running from *before* JAB was enabled on `jre8`.

### → Next action (this is the one remaining step)
1. **Fully close Avicenna** (end every `jre8` `java.exe`/`javaw.exe`; verify with
   `Get-Process java,javaw`).
2. **Reopen Avicenna**, log in, open the patient screen.
3. **Re-run the tree dump** (command in §8). The window should now read
   `[java=true]`, attach, and write `tree.json`.
4. **Send `tree.json`** so the real accessible field/tab names replace the
   guessed patterns in `tab-selectors.json`.

## 7. Known gaps / caveats

- **Selectors are guesses.** The patterns in `tab-selectors.json`
  (`"Patient No.*"`, tab names, etc.) were inferred from a screenshot; Swing
  accessible names don't have to match visible labels. They must be corrected
  from a real `tree.json` before extraction is trustworthy.
- **Native struct layouts unverified.** The JAB struct definitions in
  `WindowsAccessBridge.java` follow the long-stable API but haven't been diffed
  against the exact JAB headers of Avicenna's JRE. A mismatch corrupts memory
  silently.
- **Attachments not captured yet.** Printable forms (e.g. the morgue/death form)
  need print-to-PDF automation (drive the client's Print action to a PDF printer
  driver); the `attachments/` folder is created but stays empty for now.
- **Search-trigger reliability.** Setting the field text may not fire the app's
  Enter handler; the extractor tries a nearby search button and falls back to
  clicking the field — needs on-site confirmation.
- **Table extraction assumes JTable-backed tabs.** Tabs that render a tree or a
  document instead of a table return no rows and need bespoke handling.
- Extraction only sees what the logged-in doctor's account can see.

## 8. Build & run reference

**Build** (from `patient-archive-tool/`, needs Maven + network to Maven Central):
```
mvn package
```
→ produces `target/patient-archive-tool.jar` (Java 8, self-contained).

**Step 1 — discover the real UI (run first, and after any Avicenna upgrade):**
```
jdk8\bin\java.exe "-Djna.library.path=C:\avicennanew\jdk8\jre\bin;C:\avicennanew\jdk8\bin;C:\avicennanew\jre8\bin;C:\Windows\System32" -cp patientarchivetool.jar org.hospital.avicennaarchive.cli.TreeDumperCli "Avicenna" tree.json
```
(The `-Djna.library.path` flag is no longer required with the latest build —
`NativeLibs` adds those dirs automatically — but it is harmless to keep.)

**Step 2 — archive a patient (after selectors are corrected from tree.json):**
```
java -jar patient-archive-tool.jar <patientId> archive tab-selectors.json "Avicenna"
```
→ writes `archive/<patientId>/data.json`.

If attach fails, the tool prints every visible top-level window tagged
`[java=true/false]`:
- Avicenna listed `[java=false]` → bridge not active → restart Avicenna (§6).
- Avicenna not listed at all → privilege mismatch → run PowerShell and Avicenna
  at the same level (both normal, or both as Administrator).

## 9. Continuing this session locally (teleport)

This is a Claude Code **web** session. To continue it in a local terminal:
```
git clone https://github.com/rad1shayeb/avicenna.git
cd avicenna
claude --teleport session_013o2FnHQbjYqofNt7n9oF8B
```
Requires the Claude Code CLI signed into the same claude.ai account, and must be
run from inside a checkout of `rad1shayeb/avicenna`. Teleporting brings this
conversation + all the tool code onto the workstation that has Avicenna, so the
tool can be built and run against the live client directly.

## 10. Commit history on this branch

1. Add patient archive tool scaffold for Avicenna HIS
2. Redact plaintext SVN password from Configuration.xml
3. Retarget patient archive tool to Java 8
4. Select Access Bridge DLL by JVM bitness
5. Auto-add Access Bridge DLL dirs to jna.library.path
6. Report visible windows when attach fails
7. Add session notes (this file)

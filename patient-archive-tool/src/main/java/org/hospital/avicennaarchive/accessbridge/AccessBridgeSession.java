package org.hospital.avicennaarchive.accessbridge;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.hospital.avicennaarchive.model.AccessibleNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.hospital.avicennaarchive.accessbridge.WindowsAccessBridge.*;

/**
 * High-level operations over the Access Bridge native API: locating the
 * Avicenna window, walking its Swing accessible tree, reading table cells,
 * and driving fields/buttons the same way a doctor's mouse and keyboard
 * would.
 */
public class AccessBridgeSession implements AutoCloseable {

    private final WindowsAccessBridge bridge = WindowsAccessBridge.INSTANCE;
    private int vmID = -1;
    private Pointer topLevelAc;

    public void init() {
        bridge.Windows_run();
    }

    /** Finds the first top-level Java window whose title contains titleSubstring. */
    public boolean attachToWindow(String titleSubstring, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            final Pointer[] match = {null};
            final int[] matchedVmId = {-1};
            User32.INSTANCE.EnumWindows((hWnd, data) -> {
                char[] buf = new char[512];
                User32.INSTANCE.GetWindowText(hWnd, buf, buf.length);
                String title = Native.toString(buf);
                if (title != null && title.contains(titleSubstring) && bridge.isJavaWindow(hWnd.getPointer())) {
                    IntByReference vmIdRef = new IntByReference();
                    PointerByReference acRef = new PointerByReference();
                    if (bridge.getAccessibleContextFromHWND(hWnd.getPointer(), vmIdRef, acRef)) {
                        match[0] = acRef.getValue();
                        matchedVmId[0] = vmIdRef.getValue();
                        return false;
                    }
                }
                return true;
            }, null);

            if (match[0] != null) {
                this.vmID = matchedVmId[0];
                this.topLevelAc = match[0];
                return true;
            }
            sleep(250);
        }
        return false;
    }

    public int vmID() {
        return vmID;
    }

    public Pointer rootContext() {
        return topLevelAc;
    }

    public AccessibleContextInfo infoOf(Pointer ac) {
        AccessibleContextInfo info = new AccessibleContextInfo();
        if (!bridge.getAccessibleContextInfo(vmID, ac, info)) {
            return null;
        }
        return info;
    }

    public List<Pointer> childrenOf(Pointer ac) {
        AccessibleContextInfo info = infoOf(ac);
        if (info == null) {
            return List.of();
        }
        List<Pointer> children = new ArrayList<>(info.childrenCount);
        for (int i = 0; i < info.childrenCount; i++) {
            Pointer child = bridge.getAccessibleChildFromContext(vmID, ac, i);
            if (child != null) {
                children.add(child);
            }
        }
        return children;
    }

    /** Recursively snapshots the accessible tree under ac into a detached, JSON-serializable model. */
    public AccessibleNode dumpTree(Pointer ac, int maxDepth) {
        AccessibleContextInfo info = infoOf(ac);
        AccessibleNode node = new AccessibleNode();
        if (info == null) {
            return node;
        }
        node.name = info.nameString();
        node.role = info.roleString();
        node.states = info.statesString();
        node.x = info.x;
        node.y = info.y;
        node.width = info.width;
        node.height = info.height;

        if (maxDepth > 0) {
            for (Pointer child : childrenOf(ac)) {
                node.children.add(dumpTree(child, maxDepth - 1));
                bridge.releaseJavaObject(vmID, child);
            }
        }
        return node;
    }

    /**
     * Depth-first search for the first descendant whose name matches namePattern (and role, if given).
     * roleFilter is matched against role_en_US (the stable English role name), not the localized role,
     * so it works regardless of which language Avicenna is displaying.
     */
    public Pointer findByName(Pointer root, String namePattern, String roleFilter) {
        AccessibleContextInfo info = infoOf(root);
        if (info == null) {
            return null;
        }
        boolean nameMatches = info.nameString() != null && info.nameString().matches(namePattern);
        boolean roleMatches = roleFilter == null || roleFilter.equalsIgnoreCase(info.roleEnUsString());
        if (nameMatches && roleMatches) {
            return root;
        }
        for (Pointer child : childrenOf(root)) {
            Pointer found = findByName(child, namePattern, roleFilter);
            if (found != null) {
                return found;
            }
            bridge.releaseJavaObject(vmID, child);
        }
        return null;
    }

    /** Collects every descendant whose role_en_US matches roleFilter (e.g. all JTables under a tab panel). */
    public List<Pointer> findAllByRole(Pointer root, String roleFilter) {
        List<Pointer> found = new ArrayList<>();
        collectByRole(root, roleFilter, found);
        return found;
    }

    private void collectByRole(Pointer node, String roleFilter, List<Pointer> out) {
        AccessibleContextInfo info = infoOf(node);
        if (info == null) {
            return;
        }
        if (roleFilter.equalsIgnoreCase(info.roleEnUsString())) {
            out.add(node);
        }
        for (Pointer child : childrenOf(node)) {
            collectByRole(child, roleFilter, out);
        }
    }

    public boolean setText(Pointer ac, String text) {
        return bridge.setTextContents(vmID, ac, new WString(text));
    }

    /** Reads the live text/value of a text-capable component (a field's current contents, not its static label). */
    public String readText(Pointer ac) {
        AccessibleTextInfo textInfo = new AccessibleTextInfo();
        if (!bridge.getAccessibleTextInfo(vmID, ac, textInfo, 0, 0) || textInfo.charCount <= 0) {
            return "";
        }
        char[] buf = new char[textInfo.charCount];
        if (!bridge.getAccessibleTextRange(vmID, ac, 0, textInfo.charCount - 1, buf, (short) buf.length)) {
            return "";
        }
        return new String(buf).trim();
    }

    /** Invokes the named action (e.g. "click", "toggleExpand") on a component, as a UI event would. */
    public boolean invokeAction(Pointer ac, String actionName) {
        AccessibleActions actions = new AccessibleActions();
        if (!bridge.getAccessibleActions(vmID, ac, actions)) {
            return false;
        }
        for (AccessibleActionInfo action : actions.actions()) {
            if (actionName.equals(action.nameString())) {
                AccessibleActionsToDo todo = new AccessibleActionsToDo();
                todo.actionsCount = 1;
                todo.actions[0] = action;
                IntByReference failedIndex = new IntByReference();
                return bridge.doAccessibleActions(vmID, ac, todo, failedIndex);
            }
        }
        return false;
    }

    /** Reads a JTable-backed component into row-major string cells via getAccessibleTableInfo/CellInfo. */
    public String[][] readTable(Pointer tableAc) {
        AccessibleTableInfo tableInfo = new AccessibleTableInfo();
        if (!bridge.getAccessibleTableInfo(vmID, tableAc, tableInfo)) {
            return new String[0][0];
        }
        String[][] rows = new String[tableInfo.rowCount][tableInfo.columnCount];
        for (int r = 0; r < tableInfo.rowCount; r++) {
            for (int c = 0; c < tableInfo.columnCount; c++) {
                AccessibleTableCellInfo cell = new AccessibleTableCellInfo();
                if (bridge.getAccessibleTableCellInfo(vmID, tableAc, r, c, cell)) {
                    rows[r][c] = cell.textString();
                } else {
                    rows[r][c] = "";
                }
            }
        }
        return rows;
    }

    public void release(Pointer ac) {
        if (ac != null) {
            bridge.releaseJavaObject(vmID, ac);
        }
    }

    public static void waitUntil(BooleanSupplier condition, long timeoutMs, long pollMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(pollMs);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (topLevelAc != null) {
            release(topLevelAc);
        }
    }
}

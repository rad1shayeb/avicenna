package org.hospital.avicennaarchive.accessbridge;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.Arrays;
import java.util.List;

/**
 * JNA binding for WindowsAccessBridge-64.dll, the native side of Java Access
 * Bridge (JAB) that Windows loads into an accessibility client process and
 * uses to introspect/drive Java Swing UIs running in other JVMs.
 *
 * The struct layouts below reflect the AccessBridgeCallbacks.h /
 * AccessBridgePackages.h API that has been stable since JDK 6, but JAB's ABI
 * has had point revisions (extra interface flags, table struct changes)
 * across JDK releases. Before trusting this against the real Avicenna
 * client, diff these structs against the headers shipped under
 * "%JAVA_HOME%/include" (or the JDK used to build the JRE Avicenna ships)
 * and adjust field order/sizes if they differ - a mismatch here corrupts
 * native memory silently rather than throwing a Java exception.
 */
public interface WindowsAccessBridge extends Library {

    int MAX_STRING_SIZE = 1024;
    int SHORT_STRING_SIZE = 256;
    int MAX_ACTION_INFO = 256;
    int MAX_ACTIONS_TO_DO = 32;

    WindowsAccessBridge INSTANCE = Native.load("WindowsAccessBridge-64", WindowsAccessBridge.class);

    /** Must be called once at startup; blocks briefly while the bridge discovers running JVMs. */
    void Windows_run();

    boolean isJavaWindow(Pointer hwnd);

    boolean getAccessibleContextFromHWND(Pointer hwnd, IntByReference vmID, PointerByReference ac);

    Pointer getTopLevelObject(int vmID, Pointer ac);

    Pointer getParentWithRole(int vmID, Pointer ac, WString role);

    boolean getAccessibleContextInfo(int vmID, Pointer ac, AccessibleContextInfo info);

    Pointer getAccessibleChildFromContext(int vmID, Pointer ac, int childIndex);

    Pointer getAccessibleParentFromContext(int vmID, Pointer ac);

    void releaseJavaObject(int vmID, Pointer object);

    boolean getVersionInfo(int vmID, AccessBridgeVersionInfo info);

    boolean setTextContents(int vmID, Pointer ac, WString text);

    boolean getAccessibleActions(int vmID, Pointer ac, AccessibleActions actions);

    boolean doAccessibleActions(int vmID, Pointer ac, AccessibleActionsToDo actionsToDo,
                                 IntByReference failedIndex);

    boolean getAccessibleTableInfo(int vmID, Pointer ac, AccessibleTableInfo tableInfo);

    boolean getAccessibleTableCellInfo(int vmID, Pointer accessibleTable, int row, int column,
                                        AccessibleTableCellInfo cellInfo);

    boolean getAccessibleTextInfo(int vmID, Pointer at, AccessibleTextInfo textInfo, int x, int y);

    boolean getAccessibleTextRange(int vmID, Pointer at, int start, int end, char[] text, short len);

    @Structure.FieldOrder({
        "name", "description", "role", "role_en_US", "states", "states_en_US",
        "indexInParent", "childrenCount", "x", "y", "width", "height",
        "accessibleComponent", "accessibleAction", "accessibleSelection", "accessibleText",
        "accessibleInterfaces"
    })
    class AccessibleContextInfo extends Structure {
        public char[] name = new char[MAX_STRING_SIZE];
        public char[] description = new char[MAX_STRING_SIZE];
        public char[] role = new char[SHORT_STRING_SIZE];
        public char[] role_en_US = new char[SHORT_STRING_SIZE];
        public char[] states = new char[SHORT_STRING_SIZE];
        public char[] states_en_US = new char[SHORT_STRING_SIZE];
        public int indexInParent;
        public int childrenCount;
        public int x;
        public int y;
        public int width;
        public int height;
        public int accessibleComponent;
        public int accessibleAction;
        public int accessibleSelection;
        public int accessibleText;
        public int accessibleInterfaces;

        public String nameString() { return Native.toString(name); }
        public String roleString() { return Native.toString(role); }
        public String roleEnUsString() { return Native.toString(role_en_US); }
        public String statesString() { return Native.toString(states); }
    }

    @Structure.FieldOrder({"row", "column", "text", "isSelected", "isEditable"})
    class AccessibleTableCellInfo extends Structure {
        public int row;
        public int column;
        public char[] text = new char[MAX_STRING_SIZE];
        public int isSelected;
        public int isEditable;

        public String textString() { return Native.toString(text); }
    }

    @Structure.FieldOrder({"caption", "summary", "rowCount", "columnCount"})
    class AccessibleTableInfo extends Structure {
        public Pointer caption;
        public Pointer summary;
        public int rowCount;
        public int columnCount;
    }

    @Structure.FieldOrder({"name", "description"})
    class AccessibleActionInfo extends Structure {
        public char[] name = new char[SHORT_STRING_SIZE];
        public char[] description = new char[MAX_STRING_SIZE];

        public String nameString() { return Native.toString(name); }
    }

    @Structure.FieldOrder({"actionInfo", "actionsCount"})
    class AccessibleActions extends Structure {
        public AccessibleActionInfo[] actionInfo = new AccessibleActionInfo[MAX_ACTION_INFO];
        public int actionsCount;

        public List<AccessibleActionInfo> actions() {
            return Arrays.asList(actionInfo).subList(0, Math.min(actionsCount, MAX_ACTION_INFO));
        }
    }

    @Structure.FieldOrder({"actions", "actionsCount"})
    class AccessibleActionsToDo extends Structure {
        public AccessibleActionInfo[] actions = new AccessibleActionInfo[MAX_ACTIONS_TO_DO];
        public int actionsCount;
    }

    @Structure.FieldOrder({"charCount", "caretIndex", "indexAtPoint"})
    class AccessibleTextInfo extends Structure {
        public int charCount;
        public int caretIndex;
        public int indexAtPoint;
    }

    @Structure.FieldOrder({"VMversion", "bridgeJavaClassVersion", "bridgeJavaDLLVersion", "bridgeWinDLLVersion"})
    class AccessBridgeVersionInfo extends Structure {
        public char[] VMversion = new char[SHORT_STRING_SIZE];
        public char[] bridgeJavaClassVersion = new char[SHORT_STRING_SIZE];
        public char[] bridgeJavaDLLVersion = new char[SHORT_STRING_SIZE];
        public char[] bridgeWinDLLVersion = new char[SHORT_STRING_SIZE];
    }
}

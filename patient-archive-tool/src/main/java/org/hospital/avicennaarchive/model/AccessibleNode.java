package org.hospital.avicennaarchive.model;

import java.util.ArrayList;
import java.util.List;

/** Snapshot of one node in a Swing accessible tree, detached from the live native handle. */
public class AccessibleNode {
    public String name;
    public String role;
    public String states;
    public int x, y, width, height;
    public final List<AccessibleNode> children = new ArrayList<>();

    public AccessibleNode findFirstByName(String namePattern) {
        if (name != null && name.matches(namePattern)) {
            return this;
        }
        for (AccessibleNode child : children) {
            AccessibleNode found = child.findFirstByName(namePattern);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public List<AccessibleNode> findAllByRole(String role) {
        List<AccessibleNode> result = new ArrayList<>();
        collectByRole(role, result);
        return result;
    }

    private void collectByRole(String role, List<AccessibleNode> out) {
        if (role.equals(this.role)) {
            out.add(this);
        }
        for (AccessibleNode child : children) {
            child.collectByRole(role, out);
        }
    }
}

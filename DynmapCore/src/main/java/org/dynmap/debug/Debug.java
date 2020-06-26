package org.dynmap.debug;

import java.util.ArrayList;

public class Debug {
    private static final ArrayList<Debugger> debuggers = new ArrayList<>();

    public synchronized static void addDebugger(Debugger d) {
        debuggers.add(d);
    }

    public synchronized static void removeDebugger(Debugger d) {
        debuggers.remove(d);
    }

    public synchronized static void clearDebuggers() {
        debuggers.clear();
    }

    public synchronized static void debug(String message) {
        debuggers.forEach(debugger -> debugger.debug(message));
    }

    public synchronized static void error(String message) {
        debuggers.forEach(debugger -> debugger.error(message));
    }

    public synchronized static void error(String message, Throwable thrown) {
        debuggers.forEach(debugger -> debugger.error(message, thrown));
    }
}

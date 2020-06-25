package org.dynmap;

public class ChatEvent {
    public final String source;
    public final String name;
    public final String message;
    public ChatEvent(String source, String name, String message) {
        this.source = source;
        this.name = name;
        this.message = message;
    }
}

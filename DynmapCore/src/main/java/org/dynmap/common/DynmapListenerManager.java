package org.dynmap.common;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;

/**
 * Simple handler for managing event listeners and dispatch in a neutral fashion
 * 
 */
public class DynmapListenerManager {
    private final DynmapCore core;
    
    public DynmapListenerManager(DynmapCore core) {
        this.core = core;
    }
    public interface EventListener {
    }
    public interface WorldEventListener extends EventListener {
        void worldEvent(DynmapWorld w);
    }
    public interface PlayerEventListener extends EventListener {
        void playerEvent(DynmapPlayer p);
    }
    public interface ChatEventListener extends EventListener {
        void chatEvent(DynmapPlayer p, String msg);
    }
    public interface BlockEventListener extends EventListener {
        void blockEvent(String material, String w, int x, int y, int z);
    }
    public interface SignChangeEventListener extends EventListener {
        void signChangeEvent(String material, String w, int x, int y, int z, String[] lines, DynmapPlayer p);
    }
    public enum EventType {
        WORLD_LOAD,
        WORLD_UNLOAD,
        WORLD_SPAWN_CHANGE,
        PLAYER_JOIN,
        PLAYER_QUIT,
        PLAYER_BED_LEAVE,
        PLAYER_CHAT,
        BLOCK_BREAK,
        SIGN_CHANGE
    }
    private final Map<EventType, List<EventListener>> listeners = new EnumMap<>(EventType.class);
    
    public void addListener(EventType type, EventListener listener) {
        synchronized(listeners) {
            List<EventListener> lst = listeners.get(type);
            if(lst == null) {
                lst = new ArrayList<>();
                listeners.put(type, lst);
                core.getServer().requestEventNotification(type);
            }
            lst.add(listener);
        }
    }
    
    public void processWorldEvent(EventType type, DynmapWorld w) {
        List<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        lst.stream()
                .filter(WorldEventListener.class::isInstance)
                .map(WorldEventListener.class::cast)
                .forEachOrdered(el -> {
                    try {
                        el.worldEvent(w);
                    } catch (Throwable t) {
                        Log.warning("processWorldEvent(" + type + "," + w + ") - exception", t);
                    }
                });
    }
    public void processPlayerEvent(EventType type, DynmapPlayer p) {
        List<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        lst.stream()
                .filter(PlayerEventListener.class::isInstance)
                .map(PlayerEventListener.class::cast)
                .forEachOrdered(el -> {
                    try {
                        el.playerEvent(p);
                    } catch (Throwable t) {
                        Log.warning("processPlayerEvent(" + type + "," + p + ") - exception", t);
                    }
                });
    }
    public void processChatEvent(EventType type, DynmapPlayer p, String msg) {
        List<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        lst.stream()
                .filter(ChatEventListener.class::isInstance)
                .map(ChatEventListener.class::cast)
                .forEachOrdered(el -> {
                    try {
                        el.chatEvent(p, msg);
                    } catch (Throwable t) {
                        Log.warning("processChatEvent(" + type + "," + msg + ") - exception", t);
                    }
                });
    }
    public void processBlockEvent(EventType type, String material, String world, int x, int y, int z)
    {
        List<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        lst.stream()
                .filter(BlockEventListener.class::isInstance)
                .map(BlockEventListener.class::cast)
                .forEachOrdered(el -> {
                    try {
                        el.blockEvent(material, world, x, y, z);
                    } catch (Throwable t) {
                        Log.warning("processBlockEvent(" + type + "," + material + "," + world + "," + x + "," + y + "," + z + ") - exception", t);
                    }
                });
    }
    public void processSignChangeEvent(EventType type, String material, String world, int x, int y, int z, String[] lines, DynmapPlayer p)
    {
        List<EventListener> lst = listeners.get(type);
        if(lst == null) return;
        lst.stream()
                .filter(SignChangeEventListener.class::isInstance)
                .map(SignChangeEventListener.class::cast)
                .forEachOrdered(el -> {
                    try {
                        el.signChangeEvent(material, world, x, y, z, lines, p);
                    } catch (Throwable t) {
                        Log.warning("processSignChangeEvent(" + type + "," + material + "," + world + "," + x + "," + y + "," + z + ") - exception", t);
                    }
                });
    }
    /* Clean up registered listeners */
    public void cleanup() {
        listeners.values().forEach(List::clear);
        listeners.clear();
    }
}

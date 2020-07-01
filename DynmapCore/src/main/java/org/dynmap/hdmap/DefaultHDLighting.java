package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.JSONUtils;
import org.json.simple.JSONObject;

import java.util.Arrays;


public class DefaultHDLighting implements HDLighting {
    private final String name;
    protected boolean grayscale;
    protected boolean blackandwhite;
    protected final int blackthreshold;
    protected final Color graytone;
    protected final Color graytonedark;

    public DefaultHDLighting(DynmapCore core, ConfigurationNode configuration) {
        name = (String) configuration.get("name");
        grayscale = configuration.getBoolean("grayscale", false);
        graytone = configuration.getColor("graytone", "#FFFFFF");
        graytonedark = configuration.getColor("graytonedark", "#000000");
        blackandwhite = configuration.getBoolean("blackandwhite", false);
        if (blackandwhite) grayscale = false;
        blackthreshold = configuration.getInteger("blackthreshold",  0x40);
    }
    
    protected void checkGrayscale(Color[] colors) {
        if (grayscale) {
            Arrays.stream(colors).forEachOrdered(color -> {
                color.setGrayscale();
                color.scaleColor(graytonedark, graytone);
            });
        } else if (blackandwhite) {
            Arrays.stream(colors).forEachOrdered(color -> {
                color.setGrayscale();
                color.setColor(color.getRed() > blackthreshold ? graytone : graytonedark);
            });
        }
    }

    /* Get lighting name */
    public String getName() { return name; }
    
    /* Apply lighting to given pixel colors (1 outcolor if normal, 2 if night/day) */
    public void    applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor) {
        Arrays.stream(outcolor).forEachOrdered(color -> color.setColor(incolor));
        checkGrayscale(outcolor);
    }
    
    /* Test if Biome Data is needed for this renderer */
    public boolean isBiomeDataNeeded() { return false; }
    
    /* Test if raw biome temperature/rainfall data is needed */
    public boolean isRawBiomeDataNeeded() { return false; }
    
    /* Test if highest block Y data is needed */
    public boolean isHightestBlockYDataNeeded() { return false; }
    
    /* Tet if block type data needed */
    public boolean isBlockTypeDataNeeded() { return false; }
    
    /* Test if night/day is enabled for this renderer */
    public boolean isNightAndDayEnabled() { return false; }
    
    /* Test if sky light level needed */
    public boolean isSkyLightLevelNeeded() { return false; }
    
    /* Test if emitted light level needed */
    public boolean isEmittedLightLevelNeeded() { return false; }
    
    /* Add shader's contributions to JSON for map object */
    public void addClientConfiguration(JSONObject mapObject) {
        JSONUtils.setValue(mapObject, "lighting", name);
        JSONUtils.setValue(mapObject, "nightandday", isNightAndDayEnabled());
    }

    @Override
    public int[] getBrightnessTable(DynmapWorld world) {
        return null;
    }
}

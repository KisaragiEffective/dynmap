package org.dynmap.hdmap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.common.BiomeMap;
import org.dynmap.exporter.OBJExport;
import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.DynIntHashMap;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.EnumerationIntoIterator;
import org.dynmap.utils.ForgeConfigFile;
import org.dynmap.utils.MapIterator;

/**
 * Loader and processor class for minecraft texture packs
 *  Texture packs are found in dynmap/texturepacks directory, and either are either ZIP files
 *  or are directories whose content matches the structure of a zipped texture pack:
 *    misc/grasscolor.png - tone for grass color, biome sensitive (required)
 *    misc/foliagecolor.png - tone for leaf color, biome sensitive (required)
 *    custom_lava_still.png - custom still lava animation (optional)
 *    custom_lava_flowing.png - custom flowing lava animation (optional)
 *    custom_water_still.png - custom still water animation (optional)
 *    custom_water_flowing.png - custom flowing water animation (optional)
 *    misc/watercolorX.png - custom water color multiplier (optional)
 *    misc/swampgrasscolor.png - tone for grass color in swamps (optional)
 *    misc/swampfoliagecolor.png - tone for leaf color in swamps (optional)
 */

public class TexturePack {
    /* Loaded texture packs */
    private static final Map<String, TexturePack> packs = new HashMap<>();
    private static final Object textureMutex = new Object();

    private static final String GRASSCOLOR_PNG = "misc/grasscolor.png";
    private static final String GRASSCOLOR_RP_PNG = "assets/minecraft/textures/colormap/grass.png";
    private static final String FOLIAGECOLOR_PNG = "misc/foliagecolor.png";
    private static final String FOLIAGECOLOR_RP_PNG = "assets/minecraft/textures/colormap/foliage.png";
    private static final String WATERCOLORX_PNG = "misc/watercolorX.png";
    private static final String WATERCOLORX_RP_PNG = "assets/minecraft/mcpatcher/colormap/watercolorX.png";
    private static final String WATERCOLORX2_RP_PNG = "assets/minecraft/mcpatcher/colormap/water.png";
    private static final String CUSTOMLAVASTILL_PNG = "custom_lava_still.png";
    private static final String CUSTOMLAVAFLOWING_PNG = "custom_lava_flowing.png";
    private static final String CUSTOMWATERSTILL_PNG = "custom_water_still.png";
    private static final String CUSTOMWATERFLOWING_PNG = "custom_water_flowing.png";
    private static final String SWAMPGRASSCOLOR_PNG = "misc/swampgrasscolor.png";
    private static final String SWAMPGRASSCOLOR_RP_PNG = "assets/minecraft/mcpatcher/colormap/swampgrass.png";
    private static final String SWAMPFOLIAGECOLOR_PNG = "misc/swampfoliagecolor.png";
    private static final String SWAMPFOLIAGECOLOR_RP_PNG = "assets/minecraft/mcpatcher/colormap/swampfoliage.png";
    private static final String PINECOLOR_PNG = "misc/pinecolor.png";
    private static final String PINECOLOR_RP_PNG = "assets/minecraft/mcpatcher/colormap/pine.png";
    private static final String BIRCHCOLOR_PNG = "misc/birchcolor.png";
    private static final String BIRCHCOLOR_RP_PNG = "assets/minecraft/mcpatcher/colormap/birch.png";

    /* Color modifier codes (x1000 for value in definition file, x1000000 for internal value) */
    //private static final int COLORMOD_NONE = 0;
    public static final int COLORMOD_GRASSTONED = 1;
    public static final int COLORMOD_FOLIAGETONED = 2;
    public static final int COLORMOD_WATERTONED = 3;
    public static final int COLORMOD_ROT90 = 4;
    public static final int COLORMOD_ROT180 = 5;
    public static final int COLORMOD_ROT270 = 6;
    public static final int COLORMOD_FLIPHORIZ = 7;
    public static final int COLORMOD_SHIFTDOWNHALF = 8;
    public static final int COLORMOD_SHIFTDOWNHALFANDFLIPHORIZ = 9;
    public static final int COLORMOD_INCLINEDTORCH = 10;
    public static final int COLORMOD_GRASSSIDE = 11;
    public static final int COLORMOD_CLEARINSIDE = 12;
    public static final int COLORMOD_PINETONED = 13;
    public static final int COLORMOD_BIRCHTONED = 14;
    public static final int COLORMOD_LILYTONED = 15;
    //private static final int COLORMOD_OLD_WATERSHADED = 16;
    public static final int COLORMOD_MULTTONED = 17;   /* Toned with colorMult or custColorMult - not biome-style */
    public static final int COLORMOD_GRASSTONED270 = 18; // GRASSTONED + ROT270
    public static final int COLORMOD_FOLIAGETONED270 = 19; // FOLIAGETONED + ROT270
    public static final int COLORMOD_WATERTONED270 = 20; // WATERTONED + ROT270 
    public static final int COLORMOD_MULTTONED_CLEARINSIDE = 21; // MULTTONED + CLEARINSIDE
    public static final int COLORMOD_FOLIAGEMULTTONED = 22; // FOLIAGETONED + colorMult or custColorMult
    
    private static final int COLORMOD_MULT_FILE = 1000;
    private static final int COLORMOD_MULT_INTERNAL = 1000000;
    /* Special tile index values */
    public static final int TILEINDEX_BLANK = -1;
    private static final int TILEINDEX_GRASS = 0;
    private static final int TILEINDEX_GRASSMASK = 38;
    private static final int TILEINDEX_SNOW = 66;
    private static final int TILEINDEX_SNOWSIDE = 68;
    private static final int TILEINDEX_PISTONSIDE = 108;
    private static final int TILEINDEX_GLASSPANETOP = 148;
    private static final int TILEINDEX_AIRFRAME = 158;
    private static final int TILEINDEX_REDSTONE_NSEW_TONE = 164;
    private static final int TILEINDEX_REDSTONE_EW_TONE = 165;
    private static final int TILEINDEX_EYEOFENDER = 174;
    private static final int TILEINDEX_REDSTONE_NSEW = 180;
    private static final int TILEINDEX_REDSTONE_EW = 181;
    private static final int TILEINDEX_STATIONARYWATER = 257;
    private static final int TILEINDEX_MOVINGWATER = 258;
    private static final int TILEINDEX_STATIONARYLAVA = 259;
    private static final int TILEINDEX_MOVINGLAVA = 260;
    private static final int TILEINDEX_PISTONEXTSIDE = 261;
    private static final int TILEINDEX_PISTONSIDE_EXT = 262;
    private static final int TILEINDEX_PANETOP_X = 263;
    private static final int TILEINDEX_AIRFRAME_EYE = 264;
    private static final int TILEINDEX_WHITE = 267; // Pure white tile
    private static final int MAX_TILEINDEX = 267;  /* Index of last static tile definition */

    /* Indexes of faces in a CHEST format tile file */
    private static final int TILEINDEX_CHEST_TOP = 0;
    private static final int TILEINDEX_CHEST_LEFT = 1;
    private static final int TILEINDEX_CHEST_RIGHT = 2;
    private static final int TILEINDEX_CHEST_FRONT = 3;
    private static final int TILEINDEX_CHEST_BACK = 4;
    private static final int TILEINDEX_CHEST_BOTTOM = 5;
    private static final int TILEINDEX_CHEST_COUNT = 6;

    /* Indexes of faces in a BIGCHEST format tile file */
    private static final int TILEINDEX_BIGCHEST_TOPLEFT = 0;
    private static final int TILEINDEX_BIGCHEST_TOPRIGHT = 1;
    private static final int TILEINDEX_BIGCHEST_FRONTLEFT = 2;
    private static final int TILEINDEX_BIGCHEST_FRONTRIGHT = 3;
    private static final int TILEINDEX_BIGCHEST_LEFT = 4;
    private static final int TILEINDEX_BIGCHEST_RIGHT = 5;
    private static final int TILEINDEX_BIGCHEST_BACKLEFT = 6;
    private static final int TILEINDEX_BIGCHEST_BACKRIGHT = 7;
    private static final int TILEINDEX_BIGCHEST_BOTTOMLEFT = 8;
    private static final int TILEINDEX_BIGCHEST_BOTTOMRIGHT = 9;
    private static final int TILEINDEX_BIGCHEST_COUNT = 10;

    /* Indexes of faces in the SIGN format tile file */
    private static final int TILEINDEX_SIGN_FRONT = 0;
    private static final int TILEINDEX_SIGN_BACK = 1;
    private static final int TILEINDEX_SIGN_TOP = 2;
    private static final int TILEINDEX_SIGN_BOTTOM = 3;
    private static final int TILEINDEX_SIGN_LEFTSIDE = 4;
    private static final int TILEINDEX_SIGN_RIGHTSIDE = 5;
    private static final int TILEINDEX_SIGN_POSTFRONT = 6;
    private static final int TILEINDEX_SIGN_POSTBACK = 7;
    private static final int TILEINDEX_SIGN_POSTLEFT = 8;
    private static final int TILEINDEX_SIGN_POSTRIGHT = 9;
    private static final int TILEINDEX_SIGN_COUNT = 10;

    /* Indexes of faces in the SKIN format tile file */
    private static final int TILEINDEX_SKIN_FACEFRONT = 0;
    private static final int TILEINDEX_SKIN_FACELEFT = 1;
    private static final int TILEINDEX_SKIN_FACERIGHT = 2;
    private static final int TILEINDEX_SKIN_FACEBACK = 3;
    private static final int TILEINDEX_SKIN_FACETOP = 4;
    private static final int TILEINDEX_SKIN_FACEBOTTOM = 5;
    private static final int TILEINDEX_SKIN_COUNT = 6;

    /* Indexes of faces in the SHULKER format tile file */
    private static final int TILEINDEX_SHULKER_TOP = 0;
    private static final int TILEINDEX_SHULKER_LEFT = 1;
    private static final int TILEINDEX_SHULKER_RIGHT = 2;
    private static final int TILEINDEX_SHULKER_FRONT = 3;
    private static final int TILEINDEX_SHULKER_BACK = 4;
    private static final int TILEINDEX_SHULKER_BOTTOM = 5;
    private static final int TILEINDEX_SHULKER_COUNT = 6;
    
    /* Indexes of faces in the BED format tile file */
    //private static final int TILEINDEX_BED_HEAD_TOP = 0;
    //private static final int TILEINDEX_BED_HEAD_BOTTOM = 1;
    //private static final int TILEINDEX_BED_HEAD_LEFT = 2;
    //private static final int TILEINDEX_BED_HEAD_RIGHT = 3;
    //private static final int TILEINDEX_BED_HEAD_END = 4;
    //private static final int TILEINDEX_BED_FOOT_TOP = 5;
    //private static final int TILEINDEX_BED_FOOT_BOTTOM = 6;
    //private static final int TILEINDEX_BED_FOOT_LEFT = 7;
    //private static final int TILEINDEX_BED_FOOT_RIGHT = 8;
    //private static final int TILEINDEX_BED_FOOT_END = 9;
    //private static final int TILEINDEX_BED_HEAD_LEFTLEG_1 = 10;
    //private static final int TILEINDEX_BED_HEAD_LEFTLEG_2 = 11;
    //private static final int TILEINDEX_BED_HEAD_RIGHTLEG_1 = 12;
    //private static final int TILEINDEX_BED_HEAD_RIGHTLEG_2 = 13;
    //private static final int TILEINDEX_BED_FOOT_LEFTLEG_1 = 14;
    //private static final int TILEINDEX_BED_FOOT_LEFTLEG_2 = 15;
    //private static final int TILEINDEX_BED_FOOT_RIGHTLEG_1 = 16;
    //private static final int TILEINDEX_BED_FOOT_RIGHTLEG_2 = 17;
    private static final int TILEINDEX_BED_COUNT = 18;
    
    public static enum TileFileFormat {
        GRID,
        CHEST,
        BIGCHEST,
        SIGN,
        SKIN,
        SHULKER,
        CUSTOM,
        TILESET,
        BIOME,
        BED	// 1.13 bed texture
    }

    // Material type: used for setting advanced rendering/export characteristics for image in given file
    // (e.g. reflective surfaces, index of refraction, etc)
    public static enum MaterialType {
        GLASS(1.5, 200, 3),  // Glass material: Ni=1.5, Ns=100, illum=3
        WATER(1.33, 100, 3);  // Water material: Ni=1.33, Ns=95, illum=3
        
        public final double Ni;
        public final double Ns;
        public final int illum;
        MaterialType(double Ni, double Ns, int illum) {
            this.Ni = Ni;
            this.Ns = Ns;
            this.illum = illum;
        }
    }
    
    /* Map of 1.5 texture files to 0-255 texture indices */
    private static final String[] terrainMap = {
        "grass_top", "stone", "dirt", "grass_side", "wood", "stoneslab_side", "stoneslab_top", "brick", 
        "tnt_side", "tnt_top", "tnt_bottom", "web", "rose", "flower", "portal", "sapling",
        "stonebrick", "bedrock", "sand", "gravel", "tree_side", "tree_top", "blockIron", "blockGold",
        "blockDiamond", "blockEmerald", null, null, "mushroom_red", "mushroom_brown", "sapling_jungle", null,
        "oreGold", "oreIron", "oreCoal", "bookshelf", "stoneMoss", "obsidian", "grass_side_overlay", "tallgrass",
        null, "beacon", null, "workbench_top", "furnace_front", "furnace_side", "dispenser_front", null,
        "sponge", "glass", "oreDiamond", "oreRedstone", "leaves", "leaves_opaque", "stonebricksmooth", "deadbush",
        "fern", null, null, "workbench_side", "workbench_front", "furnace_front_lit", "furnace_top", "sapling_spruce",
        "cloth_0", "mobSpawner", "snow", "ice", "snow_side", "cactus_top", "cactus_side", "cactus_bottom",
        "clay", "reeds", "musicBlock", "jukebox_top", "waterlily", "mycel_side", "mycel_top", "sapling_birch",
        "torch", "doorWood_upper", "doorIron_upper", "ladder", "trapdoor", "fenceIron", "farmland_wet", "farmland_dry",
        "crops_0", "crops_1", "crops_2", "crops_3", "crops_4", "crops_5", "crops_6", "crops_7",
        "lever", "doorWood_lower", "doorIron_lower", "redtorch_lit", "stonebricksmooth_mossy", "stonebricksmooth_cracked", "pumpkin_top", "hellrock",
        "hellsand", "lightgem", "piston_top_sticky", "piston_top", "piston_side", "piston_bottom", "piston_inner_top", "stem_straight",
        "rail_turn", "cloth_15", "cloth_7", "redtorch", "tree_spruce", "tree_birch", "pumpkin_side", "pumpkin_face",
        "pumpkin_jack", "cake_top", "cake_side", "cake_inner", "cake_bottom", "mushroom_skin_red", "mushroom_skin_brown", "stem_bent",
        "rail", "cloth_14", "cloth_6", "repeater", "leaves_spruce", "leaves_spruce_opaque", "bed_feet_top", "bed_head_top",
        "melon_side", "melon_top", "cauldron_top", "cauldron_inner", null, "mushroom_skin_stem", "mushroom_inside", "vine",
        "blockLapis", "cloth_13", "cloth_5", "repeater_lit", "thinglass_top", "bed_feet_end", "bed_feet_side", "bed_head_side",
        "bed_head_end", "tree_jungle", "cauldron_side", "cauldron_bottom", "brewingStand_base", "brewingStand", "endframe_top", "endframe_side",
        "oreLapis", "cloth_12", "cloth_4", "goldenRail", "redstoneDust_cross", "redstoneDust_line", "enchantment_top", "dragonEgg",
        "cocoa_2", "cocoa_1", "cocoa_0", "oreEmerald", "tripWireSource", "tripWire", "endframe_eye", "whiteStone",
        "sandstone_top", "cloth_11", "cloth_3", "goldenRail_powered", "redstoneDust_cross_overlay", "redstoneDust_line_overlay", "enchantment_side", "enchantment_bottom",
        "commandBlock", "itemframe_back", "flowerPot", null, null, null, null, null,
        "sandstone_side", "cloth_10", "cloth_2", "detectorRail", "leaves_jungle", "leaves_jungle_opaque", "wood_spruce", "wood_jungle",
        "carrots_0", "carrots_1", "carrots_2", "carrots_3", "potatoes_3", null, null, null,
        "sandstone_bottom", "cloth_9", "cloth_1", "redstoneLight", "redstoneLight_lit", "stonebricksmooth_carved", "wood_birch", "anvil_base",
        "anvil_top_damaged_1", null, null, null, null, null, null, null,
        "netherBrick", "cloth_8", "netherStalk_0", "netherStalk_1", "netherStalk_2", "sandstone_carved", "sandstone_smooth", "anvil_top",
        "anvil_top_damaged_2", null, null, null, null, null, null, null,
        "destroy_0", "destroy_1", "destroy_2", "destroy_3", "destroy_4", "destroy_5", "destroy_6", "destroy_7",
        "destroy_8", "destroy_9", null, null, null, null, null, null,
        /* Extra 1.5-based textures: starting at 256 (corresponds to TILEINDEX_ values) */
        null, "water", "water_flow", "lava", "lava_flow", null, null, null, 
        null, "fire_0", "portal"
    };

    /* Map of 1.6 resource files to 0-255 texture indices */
    private static final String[] terrainRPMap = {
        "grass_top", "stone", "dirt", "grass_side", "planks_oak", "stone_slab_side", "stone_slab_top", "brick", 
        "tnt_side", "tnt_top", "tnt_bottom", "web", "flower_rose", "flower_dandelion", "portal", "sapling_oak",
        "cobblestone", "bedrock", "sand", "gravel", "log_oak", "log_oak_top", "iron_block", "gold_block",
        "diamond_block", "emerald_block", null, null, "mushroom_red", "mushroom_brown", "sapling_jungle", null,
        "gold_ore", "iron_ore", "coal_ore", "bookshelf", "cobblestone_mossy", "obsidian", "grass_side_overlay", "tallgrass",
        null, "beacon", null, "crafting_table_top", "furnace_front_off", "furnace_side", "dispenser_front_horizontal", null,
        "sponge", "glass", "diamond_ore", "redstone_ore", "leaves_oak", "leaves_oak_opaque", "stonebrick", "deadbush",
        "fern", null, null, "crafting_table_side", "crafting_table_front", "furnace_front_on", "furnace_top", "sapling_spruce",
        "wool_colored_white", "mob_spawner", "snow", "ice", "grass_side_snowed", "cactus_top", "cactus_side", "cactus_bottom",
        "clay", "reeds", "jukebox_side", "jukebox_top", "waterlily", "mycelium_side", "mycelium_top", "sapling_birch",
        "torch_on", "door_wood_upper", "door_iron_upper", "ladder", "trapdoor", "iron_bars", "farmland_wet", "farmland_dry",
        "wheat_stage_0", "wheat_stage_1", "wheat_stage_2", "wheat_stage_3", "wheat_stage_4", "wheat_stage_5", "wheat_stage_6", "wheat_stage_7",
        "lever", "door_wood_lower", "door_iron_lower", "redstone_torch_on", "stonebrick_mossy", "stonebrick_cracked", "pumpkin_top", "netherrack",
        "soul_sand", "glowstone", "piston_top_sticky", "piston_top_normal", "piston_side", "piston_bottom", "piston_inner", "pumpkin_stem_disconnected",
        "rail_normal_turned", "wool_colored_black", "wool_colored_gray", "redstone_torch_off", "log_spruce", "log_birch", "pumpkin_side", "pumpkin_face_off",
        "pumpkin_face_on", "cake_top", "cake_side", "cake_inner", "cake_bottom", "mushroom_block_skin_red", "mushroom_block_skin_brown", "pumpkin_stem_connected",
        "rail_normal", "wool_colored_red", "wool_colored_pink", "repeater_off", "leaves_spruce", "leaves_spruce_opaque", "bed_feet_top", "bed_head_top",
        "melon_side", "melon_top", "cauldron_top", "cauldron_inner", null, "mushroom_block_skin_stem", "mushroom_block_inside", "vine",
        "lapis_block", "wool_colored_green", "wool_colored_lime", "repeater_on", "glass_pane_top", "bed_feet_end", "bed_feet_side", "bed_head_side",
        "bed_head_end", "log_jungle", "cauldron_side", "cauldron_bottom", "brewing_stand_base", "brewing_stand", "endframe_top", "endframe_side",
        "lapis_ore", "wool_colored_brown", "wool_colored_yellow", "rail_golden", "redstone_dust_cross", "redstone_dust_line", "enchanting_table_top", "dragon_egg",
        "cocoa_stage_2", "cocoa_stage_1", "cocoa_stage_0", "emerald_ore", "trip_wire_source", "trip_wire", "endframe_eye", "end_stone",
        "sandstone_top", "wool_colored_blue", "wool_colored_light_blue", "rail_golden_powered", "redstone_dust_cross_overlay", "redstone_dust_line_overlay", "enchanting_table_side", "enchanting_table_bottom",
        "command_block", "itemframe_background", "flower_pot", null, null, null, null, null,
        "sandstone_normal", "wool_colored_purple", "wool_colored_magenta", "rail_detector", "leaves_jungle", "leaves_jungle_opaque", "planks_spruce", "planks_jungle",
        "carrots_stage_0", "carrots_stage_1", "carrots_stage_2", "carrots_stage_3", "potatoes_stage_3", null, null, null,
        "sandstone_bottom", "wool_colored_cyan", "wool_colored_orange", "redstone_lamp_off", "redstone_lamp_on", "stonebrick_carved", "planks_birch", "anvil_base",
        "anvil_top_damaged_1", null, null, null, null, null, null, null,
        "nether_brick", "wool_colored_silver", "nether_wart_stage_0", "nether_wart_stage_1", "nether_wart_stage_2", "sandstone_carved", "sandstone_smooth", "anvil_top_damaged_0",
        "anvil_top_damaged_2", null, null, null, null, null, null, null,
        "destroy_stage_0", "destroy_stage_1", "destroy_stage_2", "destroy_stage_3", "destroy_stage_4", "destroy_stage_5", "destroy_stage_6", "destroy_stage_7",
        "destroy_stage_8", "destroy_stage_9", null, null, null, null, null, null,
        /* Extra 1.5-based textures: starting at 256 (corresponds to TILEINDEX_ values) */
        null, "water_still", "water_flow", "lava_still", "lava_flow", null, null, null, 
        null, "fire_layer_0", "portal"
    };

    private static class CustomTileRec {
        int srcx, srcy, width, height, targetx, targety;
        public CustomTileRec() {}
        public CustomTileRec(int srcx, int srcy, int width, int height, int targetx, int targety) {
        	this.srcx = srcx; this.srcy = srcy;
        	this.width = width; this.height = height;
        	this.targetx = targetx; this.targety = targety;
        }
        public CustomTileRec(int srcx, int srcy, int width, int height) {
        	this(srcx, srcy, width, height, 0, 0);
        }
    }
    
    private static int next_dynamic_tile = MAX_TILEINDEX+1;
    
    private static class DynamicTileFile {
        int idx;                    /* Index of tile in addonfiles */
        String filename;
        String modname;             /* Modname associated with file, if any */
        int tilecnt_x, tilecnt_y;   /* Number of tiles horizontally and vertically */
        int[] tile_to_dyntile;      /* Mapping from tile index in tile file to dynamic ID in global tile table (terrain_argb): 0=unassigned */
        TileFileFormat format;
        List<CustomTileRec> cust;
        String[] tilenames;         /* For TILESET, array of tilenames, indexed by tile index */
        boolean used;               // Set to true if any active references to the file
        MaterialType material;      // Material type, if specified
    }
    private static final ArrayList<DynamicTileFile> addonfiles = new ArrayList<>();
    private static final Map<String, DynamicTileFile> addonFilesByName = new HashMap<>();
    private final Map<Integer, MaterialType> materialbytileid = new HashMap<>();
    private final Map<Integer, String> matIDByTileID = new HashMap<>();
    private final Map<String, Integer> tileIDByMatID = new HashMap<>();
    // Mods supplying their own texture files
    private static final HashSet<String> loadedmods = new HashSet<>();
    
    private static String getBlockFileName(int idx) {
        if ((idx >= 0) && (idx < terrainMap.length) && (terrainMap[idx] != null)) {
            return "textures/blocks/" + terrainMap[idx] + ".png";
        }
        return null;
    }

    private static String getRPFileName(int idx) {
        if ((idx >= 0) && (idx < terrainRPMap.length) && (terrainRPMap[idx] != null)) {
            return "assets/minecraft/textures/blocks/" + terrainRPMap[idx] + ".png";
        }
        return null;
    }

    /* Reset add-on tile data */
    private static void resetFiles(DynmapCore core) {
        synchronized(textureMutex) {
            packs.clear();
        }
        addonfiles.clear();
        addonFilesByName.clear();
        loadedmods.clear();
        next_dynamic_tile = MAX_TILEINDEX+1;
        
        /* Now, load entries for vanilla v1.6.x RP files */
        for(int i = 0; i < terrainRPMap.length; i++) {
            String fn = getRPFileName(i);
            if (fn != null) {
                int idx = findOrAddDynamicTileFile(fn, null, 1, 1, TileFileFormat.GRID, new String[0]);
                DynamicTileFile dtf = addonfiles.get(idx);
                if (dtf != null) {  // Fix mapping of tile ID to global table index
                    dtf.tile_to_dyntile[0] = i;
                    dtf.used = true;
                }
            }
        }
        /* Now, load entries for vanilla v1.5.x files (put second so that add-on TP overrides built in RP) */
        for(int i = 0; i < terrainMap.length; i++) {
            String fn = getBlockFileName(i);
            if (fn != null) {
                int idx = findOrAddDynamicTileFile(fn, null, 1, 1, TileFileFormat.GRID, new String[0]);
                DynamicTileFile dtf = addonfiles.get(idx);
                if (dtf != null) {  // Fix mapping of tile ID to global table index
                    dtf.used = true;
                    dtf.tile_to_dyntile[0] = i;
                }
            }
        }
    }
    
    private static class LoadedImage {
        int[] argb;
        int width, height;
        int trivial_color;
        boolean isLoaded;
        @SuppressWarnings("unused")
		String fname, modid;
    }    
    
    private int[][] tileARGB;
    private int[] blank;
    private int nativeScale;
    private CTMTexturePack ctm;
//    private static BitSet hasBaseBlockColoring = new BitSet(); // Quick lookup - (blockID << 4) + blockMeta - set if custom colorizer
//    private static DynIntHashMap baseBlockColoring = new DynIntHashMap();   // Base block coloring (RP independent)
    // Need copy, since RP can change this....
    private ColorizingData blockColoring = HDBlockStateTextureMap.getColorizingData();

    private final int colorMultBirch = 0x80a755;  /* From ColorizerFoliage.java in MCP */
    private final int colorMultPine = 0x619961;   /* From ColorizerFoliage.java in MCP */
    private final int colorMultLily = 0x208030;   /* from BlockLilyPad.java in MCP */
    
    private static final int IMG_GRASSCOLOR = 0;
    private static final int IMG_FOLIAGECOLOR = 1;
    private static final int IMG_CUSTOMWATERMOVING = 2;
    private static final int IMG_CUSTOMWATERSTILL = 3;
    private static final int IMG_CUSTOMLAVAMOVING = 4;
    private static final int IMG_CUSTOMLAVASTILL = 5;
    private static final int IMG_WATERCOLORX = 6;
    private static final int IMG_SWAMPGRASSCOLOR = 7;
    private static final int IMG_SWAMPFOLIAGECOLOR = 8;
    private static final int IMG_PINECOLOR = 9;
    private static final int IMG_BIRCHCOLOR = 10;
    
    private static final int IMG_CNT = 11;
    /** 0-(IMG_CNT-1) are fixed, IMG_CNT+x is dynamic file x */
    private LoadedImage[] imgs;

    private Map<Integer, TexturePack> scaledTextures;
    private final Object scaledMutex = new Object();
    
    public enum BlockTransparency {
        /** Block is opaque - blocks light - lit by light from adjacent blocks */
        OPAQUE,
        /** Block is transparent - passes light - lit by light level in own block */
        TRANSPARENT,
        /** Opaque block that doesn't block all rays (steps, slabs) - use light above for face lighting on opaque blocks */
        SEMITRANSPARENT,
        /** Special case of transparent, to work around lighting errors in SpoutPlugin */
        LEAVES
    }
    
    public static class ColorizingData {
        private final DynIntHashMap map = new DynIntHashMap();
        
        public void setBlkStateValue(DynmapBlockState blk, Integer mapidx) {
            int idx = blk.globalStateIndex;
            if (mapidx == null) {
                map.remove(idx);
            } else {
                map.put(idx,  mapidx);
            }
        }
        public Integer getBlkStateValue(DynmapBlockState blk) {
            return (Integer) map.get(blk.globalStateIndex);
        }
        public boolean hasBlkStateValue(DynmapBlockState blk) {
            return map.containsKey(blk.globalStateIndex);
        }
        public void scrubValues(Integer val) {
            map.keysWithValue(val).forEach(map::remove);
        }
    }
    
    /**
     * Texture map - used for accumulation of textures from different sources, keyed by lookup value
     */
    public static class TextureMap {
        private final Map<Integer, Integer> keyToIndex = new HashMap<>();
        private final List<Integer> textureIds = new ArrayList<>();
        private List<String> blocknames = new ArrayList<>();
        private BitSet stateids = new BitSet();
        private BlockTransparency trans = BlockTransparency.OPAQUE;
        private int colorMult = 0;
        private CustomColorMultiplier custColorMult = null;
        private String blockset;

        public TextureMap() { }
        
        public int addTextureByKey(int key, int textureid) {
            int off = textureIds.size();   /* Next index in array is texture index */
            textureIds.add(textureid); /* Add texture ID to list */
            keyToIndex.put(key, off);   /* Add texture index to lookup by key */
            return off;
        }
    }
    private static final Map<String, TextureMap> textMapById = new HashMap<>();
    
    /**
     * Set tile ARGB buffer at index
     * @param idx - index of tile
     * @param buf - buffer to be set
     */
    public final void setTileARGB(int idx, int[] buf) {
        if (idx >= tileARGB.length) {
            tileARGB = Arrays.copyOf(tileARGB, 3*idx/2);
        }
        tileARGB[idx] = buf;
    }
    /**
     * Get number of entries in tile list
     * @return length of tile list
     */
    public final int getTileARGBCount() {
        return tileARGB.length;
    }
    /**
     * Get tile ARGB buffer at index
     * @param idx - tile index
     * @return ARGB array for tile, or blank array if not found
     */
    public final int[] getTileARGB(int idx) {
        int[] rslt = blank;
        if (idx < tileARGB.length) {
            rslt = tileARGB[idx];
            if (rslt == null) {
                rslt = tileARGB[idx] = blank;
            }
        }
        return rslt;
    }
    /**
     * Add texture to texture map
     */
    private static int addTextureByKey(String id, int key, int textureid) {
        TextureMap idx = textMapById.get(id);
        if(idx == null) {   /* Add empty one, if not found */
            idx = new TextureMap();
            textMapById.put(id,  idx);
        }
        return idx.addTextureByKey(key, textureid);
    }
    /**
     * Add settings for texture map
     */
    private static void addTextureIndex(String id, List<String> blockNames, BitSet stateids, BlockTransparency trans, int colorMult, CustomColorMultiplier custColorMult, String blockset) {
        TextureMap idx = textMapById.get(id);
        //Add empty one, if not found
        if(idx == null) {
            idx = new TextureMap();
            textMapById.put(id,  idx);
        }
        idx.blocknames = blockNames;
        idx.stateids = stateids;
        idx.trans = trans;
        idx.colorMult = colorMult;
        idx.custColorMult = custColorMult;
    }
    /**
     * Finish processing of texture indexes - add to texture maps
     */
    private static void processTextureMaps() {
        textMapById.values()
                .stream()
                .filter(ti -> !ti.blocknames.isEmpty())
                .forEachOrdered(ti -> {
                    int[] txtids = ti.textureIds
                            .stream()
                            // unboxing
                            .mapToInt(integer -> integer)
                            .toArray();
                    HDBlockStateTextureMap map = new HDBlockStateTextureMap(txtids, null, ti.colorMult, ti.custColorMult, ti.blockset, true, null, ti.trans);
                    map.addToTable(ti.blocknames, ti.stateids);
                });
    }
    /**
     * Get index of texture in texture map
     * @param id - texture pack id
     * @param key - key for texture
     * @return index of texture, or -1 if not found
     */
    public static int getTextureIndexFromTextureMap(String id, int key) {
        int idx = -1;
        TextureMap map = textMapById.get(id);
        if(map != null) {
            Integer txtidx = map.keyToIndex.get(key);
            if(txtidx != null) {
                idx = txtidx;
            }
        }
        return idx;
    }
    /**
     * Get count of textures in given texture map
     * @param id - texture pack ID
     * @return length of texture list, or -1 if error
     */
    public static int getTextureMapLength(String id) {
        TextureMap map = textMapById.get(id);
        return map != null ? map.textureIds.size() : -1;
    }
    /** 
     * Get or load texture pack
     * @param core - core object
     * @param tpname - texture pack name
     * @return loaded texture pack, or null if error
     */
    public static TexturePack getTexturePack(DynmapCore core, String tpname) {
        synchronized(textureMutex) {
            TexturePack tp = packs.get(tpname);
            if(tp != null)
                return tp;
            /* Attempt to load pack */
            TexturePack texturePack = new TexturePack(core, tpname);
            packs.put(tpname, texturePack);
            return texturePack;
        }
    }
    
    /**
     * Constructor for texture pack, by name
     */
    private TexturePack(DynmapCore core, String packName) {
        File packDirectory = getTexturePackDirectory(core);
        /* Set up for enough files */
        imgs = new LoadedImage[IMG_CNT + addonfiles.size()];

        // Get texture pack
        File f = new File(packDirectory, packName);
        // Build loader
        TexturePackLoader packLoader = new TexturePackLoader(f, core);
        try {
            boolean isResourcePack;
            try (InputStream mcMeta = packLoader.openTPResource("pack.mcmeta")) {
                /* Check if resource pack */
                if (mcMeta != null) {
                    packLoader.closeResource(mcMeta);
                    isResourcePack = true;
                    Log.info("Loading resource pack " + f.getName());
                } else if (packName.equals("standard")) { // Built in is RP
                    isResourcePack = true;
                    Log.info("Loading default resource pack");
                } else {
                    isResourcePack = false;
                    Log.info("Loading texture pack " + f.getName());
                }
            }
            /* Load CTM support, if enabled */
            if(core.isCTMSupportEnabled()) {
                ctm = new CTMTexturePack(packLoader, this, core, isResourcePack);
                if(!ctm.isValid()) {
                    ctm = null;
                }
            }
            /* Load custom colors support, if enabled */
            if(core.isCustomColorsSupportEnabled()) {
                String filePath = (isResourcePack ? "assets/minecraft/mcpatcher/color.properties" : "color.properties");
                try (InputStream mcMetazz = packLoader.openTPResource(filePath)) {
                    if (mcMetazz != null) {
                        Properties p = new Properties();
                        p.load(mcMetazz);
                        processCustomColors(p);
                    }
                }
            }
            /* Loop through dynamic files */
            for(int i = 0; i < addonfiles.size(); i++) {
                DynamicTileFile dtf = addonfiles.get(i);
                // Not used, skip it - save memory and avoid errors for downlevel mods and such
                if (!dtf.used) {
                    continue;
                }

                try (InputStream mcmeta = packLoader.openModTPResource(dtf.filename, dtf.modname)) {
                    if (dtf.format == TileFileFormat.BIOME) {
                        loadBiomeShadingImage(mcmeta, i + IMG_CNT, dtf.filename, dtf.modname);
                    } else {
                        loadImage(mcmeta, i + IMG_CNT, dtf.filename, dtf.modname);
                    }
                }
            }
            /* Find and load terrain */
            loadTerrain(isResourcePack);
            /* Try to find and load misc/grasscolor.png */
            try (InputStream mcMeta = packLoader.openTPResource(GRASSCOLOR_PNG, GRASSCOLOR_RP_PNG)) {
                if (mcMeta != null) {
                    loadBiomeShadingImage(mcMeta, IMG_GRASSCOLOR, GRASSCOLOR_RP_PNG, "minecraft");
                    packLoader.closeResource(mcMeta);
                }
            }
            /* Try to find and load misc/foliagecolor.png */
            try (InputStream mcMeta = packLoader.openTPResource(FOLIAGECOLOR_PNG, FOLIAGECOLOR_RP_PNG)) {
                if (mcMeta != null) {
                    loadBiomeShadingImage(mcMeta, IMG_FOLIAGECOLOR, FOLIAGECOLOR_RP_PNG, "minecraft");
                    packLoader.closeResource(mcMeta);
                }
            }
            /* Try to find and load misc/swampgrasscolor.png */
            try (InputStream mcMeta = packLoader.openTPResource(SWAMPGRASSCOLOR_PNG, SWAMPGRASSCOLOR_RP_PNG)) {
                if (mcMeta != null) {
                    loadBiomeShadingImage(mcMeta, IMG_SWAMPGRASSCOLOR, SWAMPGRASSCOLOR_RP_PNG, "minecraft");
                    packLoader.closeResource(mcMeta);
                }
            }
            /* Try to find and load misc/swampfoliagecolor.png */
            try (InputStream mcMeta = packLoader.openTPResource(SWAMPFOLIAGECOLOR_PNG, SWAMPFOLIAGECOLOR_RP_PNG)) {
                if (mcMeta != null) {
                    loadBiomeShadingImage(mcMeta, IMG_SWAMPFOLIAGECOLOR, SWAMPFOLIAGECOLOR_RP_PNG, "minecraft");
                    packLoader.closeResource(mcMeta);
                }
            }
            /* Try to find and load misc/watercolor.png */
            try (InputStream mcMeta2 = packLoader.openTPResource(WATERCOLORX_PNG, WATERCOLORX_RP_PNG)) {
                InputStream actual = null;
                if (mcMeta2 == null) {
                    /* Try to find and load colormap/water.png */
                    actual = packLoader.openTPResource(WATERCOLORX_PNG, WATERCOLORX2_RP_PNG);
                }

                if (actual != null) {
                    loadBiomeShadingImage(actual, IMG_WATERCOLORX, WATERCOLORX_RP_PNG, "minecraft");
                    packLoader.closeResource(actual);
                }
            }
            /* Try to find pine.png */
            try (InputStream mcMeta = packLoader.openTPResource(PINECOLOR_PNG, PINECOLOR_RP_PNG)) {
                if (mcMeta != null) {
                    loadBiomeShadingImage(mcMeta, IMG_PINECOLOR, PINECOLOR_RP_PNG, "minecraft");
                    packLoader.closeResource(mcMeta);
                }
            }
            /* Try to find birch.png */
            InputStream inputStream = packLoader.openTPResource(BIRCHCOLOR_PNG, BIRCHCOLOR_RP_PNG);
            if (inputStream != null) {
                loadBiomeShadingImage(inputStream, IMG_BIRCHCOLOR, BIRCHCOLOR_RP_PNG, "minecraft");
                packLoader.closeResource(inputStream);
            }
            /* Optional files - process if they exist */
            try (InputStream choice = packLoader.openTPResource(CUSTOMLAVASTILL_PNG)) {
                InputStream actual = choice;
                if (choice == null) {
                    actual = packLoader.openTPResource("anim/" + CUSTOMLAVASTILL_PNG);
                }
                if (actual != null) {
                    loadImage(actual, IMG_CUSTOMLAVASTILL, CUSTOMLAVASTILL_PNG, "minecraft");
                    packLoader.closeResource(actual);
                    patchTextureWithImage(IMG_CUSTOMLAVASTILL, TILEINDEX_STATIONARYLAVA);
                    patchTextureWithImage(IMG_CUSTOMLAVASTILL, TILEINDEX_MOVINGLAVA);
                }
            }
            try (InputStream choice = packLoader.openTPResource(CUSTOMLAVAFLOWING_PNG)) {
                InputStream actual = choice;
                if (choice == null) {
                    actual = packLoader.openTPResource("anim/" + CUSTOMLAVAFLOWING_PNG);
                }
                if (actual != null) {
                    loadImage(actual, IMG_CUSTOMLAVAMOVING, CUSTOMLAVAFLOWING_PNG, "minecraft");
                    packLoader.closeResource(actual);
                    patchTextureWithImage(IMG_CUSTOMLAVAMOVING, TILEINDEX_MOVINGLAVA);
                }
            }
            try (InputStream choice = packLoader.openTPResource(CUSTOMWATERSTILL_PNG)) {
                InputStream actual = choice;
                if (choice == null) {
                    actual = packLoader.openTPResource("anim/" + CUSTOMWATERSTILL_PNG);
                }
                if (actual != null) {
                    loadImage(actual, IMG_CUSTOMWATERSTILL, CUSTOMWATERSTILL_PNG, "minecraft");
                    packLoader.closeResource(actual);
                    patchTextureWithImage(IMG_CUSTOMWATERSTILL, TILEINDEX_STATIONARYWATER);
                    patchTextureWithImage(IMG_CUSTOMWATERSTILL, TILEINDEX_MOVINGWATER);
                }
            }
            try (InputStream choice = packLoader.openTPResource(CUSTOMWATERFLOWING_PNG)) {
                InputStream actual = choice;
                if (choice == null) {
                    actual = packLoader.openTPResource("anim/" + CUSTOMWATERFLOWING_PNG);
                }
                if (actual != null) {
                    loadImage(actual, IMG_CUSTOMWATERMOVING, CUSTOMWATERSTILL_PNG, "minecraft");
                    packLoader.closeResource(actual);
                    patchTextureWithImage(IMG_CUSTOMWATERMOVING, TILEINDEX_MOVINGWATER);
                }
            }
            /* Loop through dynamic files */
            for(int i = 0; i < addonfiles.size(); i++) {
                DynamicTileFile dtf = addonfiles.get(i);
                processDynamicImage(i, dtf.format);
            }
        } catch (IOException iox) {
            Log.severe("Error loadling texture pack", iox);
        } finally {
            packLoader.close();
        }
    }
    /**
     * Copy subimage from portions of given image
     * @param imgID - image ID of raw image
     * @param from_x - top-left X
     * @param from_y - top-left Y
     * @param to_x - dest topleft
     * @param to_y - dest topleft
     * @param width - width to copy
     * @param height - height to copy
     * @param dest_argb - destination tile buffer
     * @param dest_width - width of destination tile buffer
     */
    private void copySubimageFromImage(int imgID, int from_x, int from_y, int to_x, int to_y, int width, int height, int[] dest_argb, int dest_width) {
        for(int h = 0; h < height; h++) {
            System.arraycopy(imgs[imgID].argb, (h+from_y)*imgs[imgID].width + from_x, dest_argb, dest_width*(h+to_y) + to_x, width);
        }
    }
    /**
     * Combine non-transparent portions of given image onto destination
     * @param imgID - image ID of raw image
     * @param from_x - top-left X
     * @param from_y - top-left Y
     * @param to_x - dest topleft
     * @param to_y - dest topleft
     * @param width - width to copy
     * @param height - height to copy
     * @param destARGB - destination tile buffer
     * @param dest_width - width of destination tile buffer
     */
    private void combineSubimageFromImage(int imgID, int from_x, int from_y, int to_x, int to_y, int width, int height, int[] destARGB, int dest_width) {
        for(int h = 0; h < height; h++) {
            for(int w = 0; w < width; w++) {
                int srcARGB = imgs[imgID].argb[(h+from_y)*imgs[imgID].width + (w+from_x)];
                // Apply only solid pixels
                if ( ((srcARGB >> 24) & 0xFF) == 0xFF ) {
                    destARGB[dest_width * (h + to_y) + (w + to_x)] = srcARGB;
                }
            }
        }
    }
    private enum HandlePos { CENTER, LEFT, RIGHT, NONE, LEFTFRONT, RIGHTFRONT }

    private int[] allocateNewARGBBuffer() {
        return new int[nativeScale * nativeScale];
    }
    /**
     * Make chest side image (based on chest and largechest layouts)
     * @param imgID - source image ID
     * @param destIndex - destination tile index
     * @param src_x - starting X of source (scaled based on 64 high)
     * @param width - width to copy (scaled based on 64 high)
     * @param dest_x - destination X (scaled based on 64 high)
     * @param handlepos - 0=middle,1=leftedge,2=rightedge
     */
    private void makeChestSideImage(int imgID, int destIndex, int src_x, int width, int dest_x, HandlePos handlepos) {
        if(destIndex <= 0) return;
        int mult = imgs[imgID].height / 64; /* Nominal height for chest images is 64 */
        int[] tile = new int[16 * 16 * mult * mult];    /* Make image */
        /* Copy top part */
        copySubimageFromImage(imgID, src_x * mult, 14 * mult, dest_x * mult, 2 * mult, width * mult, 5 * mult, tile, 16 * mult);
        /* Copy bottom part */
        copySubimageFromImage(imgID, src_x * mult, 34 * mult, dest_x * mult, 7 * mult, width * mult, 9 * mult, tile, 16 * mult);
        /* Handle the handle image */
        switch (handlepos) {
            case CENTER:     /* Middle */
                copySubimageFromImage(imgID, 1 * mult, 1 * mult, 7 * mult, 4 * mult, 2 * mult, 4 * mult, tile, 16 * mult);
                break;
            case LEFT:    /* left edge */
                copySubimageFromImage(imgID, 3 * mult, 1 * mult, 0 * mult, 4 * mult, 1 * mult, 4 * mult, tile, 16 * mult);
                break;
            case LEFTFRONT:    /* left edge - front of handle */
                copySubimageFromImage(imgID, 2 * mult, 1 * mult, 0 * mult, 4 * mult, 1 * mult, 4 * mult, tile, 16 * mult);
                break;
            case RIGHT:   /* Right */
                copySubimageFromImage(imgID, 0 * mult, 1 * mult, 15 * mult, 4 * mult, 1 * mult, 4 * mult, tile, 16 * mult);
                break;
            case RIGHTFRONT:   /* Right - front of handle */
                copySubimageFromImage(imgID, 1 * mult, 1 * mult, 15 * mult, 4 * mult, 1 * mult, 4 * mult, tile, 16 * mult);
                break;
        }
        /* Put scaled result into tile buffer */
        int[] newARGB = allocateNewARGBBuffer();
        scaleTerrainPNGSubImage(16*mult, nativeScale, tile, newARGB);
        setTileARGB(destIndex, newARGB);
    }
    /**
     * Make chest top/bottom image (based on chest and largechest layouts)
     * @param imageId - source image ID
     * @param destIndex - destination tile index
     * @param src_x - starting X of source (scaled based on 64 high)
     * @param src_y - starting Y of source (scaled based on 64 high)
     * @param width - width to copy (scaled based on 64 high)
     * @param dest_x - destination X (scaled based on 64 high)
     * @param handlepos - 0=middle,1=left-edge (righttop),2=right-edge (lefttop)
     */
    private void makeChestTopBottomImage(int imageId, int destIndex, int src_x, int src_y, int width, int dest_x, HandlePos handlepos) {
        if(destIndex <= 0) return;
        
        int mult = imgs[imageId].height / 64; /* Nominal height for chest images is 64 */
        int[] tile = new int[16 * 16 * mult * mult];    /* Make image */
        copySubimageFromImage(imageId, src_x * mult, src_y * mult, dest_x * mult, 1 * mult, width * mult, 14 * mult, tile, 16 * mult);
        /* Handle the handle image */
        switch (handlepos) {
            case CENTER:     /* Middle */
                copySubimageFromImage(imageId, 1 * mult, 0, 7 * mult, 15 * mult, 2 * mult, 1 * mult, tile, 16 * mult);
                break;
            case LEFT:    /* left edge */
                copySubimageFromImage(imageId, 2 * mult, 0, 0 * mult, 15 * mult, 1 * mult, 1 * mult, tile, 16 * mult);
                break;
            case RIGHT:   /* Right */
                copySubimageFromImage(imageId, 1 * mult, 0, 15 * mult, 15 * mult, 1 * mult, 1 * mult, tile, 16 * mult);
                break;
        }
        /* Put scaled result into tile buffer */
        int[] newARGB = allocateNewARGBBuffer();
        scaleTerrainPNGSubImage(16*mult, nativeScale, tile, newARGB);
        setTileARGB(destIndex, newARGB);
    }
    /**
     * Patch tiles based on image with chest-style layout
     */
    private void patchChestImages(int imgId, int tile_top, int tile_bottom, int tile_front, int tile_back, int tile_left, int tile_right) {
        makeChestSideImage(imgId, tile_front, 14, 14, 1, HandlePos.CENTER);
        makeChestSideImage(imgId, tile_back, 42, 14, 1, HandlePos.NONE);
        makeChestSideImage(imgId, tile_left, 0, 14, 1, HandlePos.RIGHT);
        makeChestSideImage(imgId, tile_right, 28, 14, 1, HandlePos.LEFT);
        makeChestTopBottomImage(imgId, tile_top, 14, 0, 14, 1, HandlePos.CENTER);
        makeChestTopBottomImage(imgId, tile_bottom, 28, 19, 14, 1, HandlePos.CENTER);
    }
    /**
     * Patch tiles based on image with large-chest-style layout
     */
    private void patchLargeChestImages(int imageId, int tile_topright, int tile_topleft, int tile_bottomright, int tile_bottomleft, int tile_right, int tile_left, int tile_frontright, int tile_frontleft, int tile_backright, int tile_backleft) {
        makeChestSideImage(imageId, tile_frontleft, 14, 15, 1, HandlePos.RIGHTFRONT);
        makeChestSideImage(imageId, tile_frontright, 29, 15, 0, HandlePos.LEFTFRONT);
        makeChestSideImage(imageId, tile_left, 0, 14, 1, HandlePos.RIGHT);
        makeChestSideImage(imageId, tile_right, 44, 14, 1, HandlePos.LEFT);
        makeChestSideImage(imageId, tile_backright, 58, 15, 1, HandlePos.NONE);
        makeChestSideImage(imageId, tile_backleft, 73, 15, 0, HandlePos.NONE);
        makeChestTopBottomImage(imageId, tile_topleft, 14, 0, 15, 1, HandlePos.RIGHT);
        makeChestTopBottomImage(imageId, tile_topright, 29, 0, 15, 0, HandlePos.LEFT);
        makeChestTopBottomImage(imageId, tile_bottomleft, 34, 19, 15, 1, HandlePos.RIGHT);
        makeChestTopBottomImage(imageId, tile_bottomright, 49, 19, 15, 0, HandlePos.LEFT);
    }

    /**
     * Make sign image (based on sign layouts)
     * @param img_id - source image ID
     * @param dest_idx - destination tile index
     * @param src_x - starting X of source (scaled based on 32 high)
     * @param src_y - starting Y of source (scaled based on 32 high)
     * @param width - width to copy (scaled based on 32 high)
     * @param height - height to copy (scaled based on 32 high)
     */
    private void makeSignImage(int img_id, int dest_idx, int src_x, int src_y, int width, int height) {
        int mult = imgs[img_id].height / 32; /* Nominal height for sign images is 32 */
        int[] tile = new int[24 * 24 * mult * mult];    /* Make image (all are 24x24) */
        copySubimageFromImage(img_id, src_x * mult, src_y * mult, 0, (24-height)*mult, width * mult, height * mult, tile, 24 * mult);
        /* Put scaled result into tile buffer */
        int[] new_argb = allocateNewARGBBuffer();
        scaleTerrainPNGSubImage(24*mult, nativeScale, tile, new_argb);
        setTileARGB(dest_idx, new_argb);
    }

    private void patchSignImages(int img, int sign_front, int sign_back, int sign_top, int sign_bottom, int sign_left, int sign_right, int post_front, int post_back, int post_left, int post_right)
    {
        /* Load images at lower left corner of each tile */
        makeSignImage(img, sign_front, 2, 2, 24, 12);
        makeSignImage(img, sign_back, 28, 2, 24, 12);
        makeSignImage(img, sign_top, 2, 0, 24, 2);
        makeSignImage(img, sign_left, 0, 2, 2, 12);
        makeSignImage(img, sign_right, 26, 2, 2, 12);
        makeSignImage(img, sign_bottom, 26, 0, 24, 2);
        makeSignImage(img, post_front, 0, 16, 2, 14);
        makeSignImage(img, post_right, 2, 16, 2, 14);
        makeSignImage(img, post_back, 4, 16, 2, 14);
        makeSignImage(img, post_left, 6, 16, 2, 14);
    }

    /**
     * Make face image (based on skin layouts)
     * @param img_id - source image ID
     * @param dest_idx - destination tile index
     * @param src_x - starting X of source (scaled based on 32 high)
     * @param src_y - starting Y of source (scaled based on 32 high)
     */
    private void makeFaceImage(int img_id, int dest_idx, int src_x, int src_y) {
        int mult = imgs[img_id].width / 64; /* Nominal height for skin images is 32 */
        int[] tile = new int[8 * 8 * mult * mult];    /* Make image (all are 8x8) */
        copySubimageFromImage(img_id, src_x * mult, src_y * mult, 0, 0, 8 * mult, 8 * mult, tile, 8 * mult);
        /* Put scaled result into tile buffer */
        int[] new_argb = allocateNewARGBBuffer();
        scaleTerrainPNGSubImage(8 * mult, nativeScale, tile, new_argb);
        setTileARGB(dest_idx, new_argb);
    }
    
    private void patchSkinImages(int img, int face_front, int face_left, int face_right, int face_back, int face_top, int face_bottom)
    {
        makeFaceImage(img, face_front, 8, 8);
        makeFaceImage(img, face_left, 16, 8);
        makeFaceImage(img, face_right, 0, 8);
        makeFaceImage(img, face_back, 24, 8);
        makeFaceImage(img, face_top, 8, 0);
        makeFaceImage(img, face_bottom, 16, 0);
    }

    /**
     * Make shulker side image from top and bottom images (based on shulker layouts)
     * @param img_id - source image ID
     * @param dest_idx - destination tile index
     * @param src_x - starting X of source column (scaled based on 64 high)
     */
    private void makeShulkerSideImage(int img_id, int dest_idx, int src_x) {
        int mult = imgs[img_id].width / 64; /* Nominal height for shulker images is 64 */
        int src_y_top = 16;
        int[] tile = new int[16 * 16 * mult * mult];    /* Make image (all are 16x16) */
        /* Top half of the shulker */
        copySubimageFromImage(img_id, src_x * mult, src_y_top * mult, 0, 0, 16 * mult, 12 * mult, tile, 16 * mult);
        /* Bottom half of the shulker */
        int src_y_btm = 44;
        combineSubimageFromImage(img_id, src_x * mult, src_y_btm * mult, 0, 8 * mult, 16 * mult, 8 * mult, tile, 16 * mult);
        /* Put scaled result into tile buffer */
        int[] new_argb = allocateNewARGBBuffer();
        scaleTerrainPNGSubImage(16 * mult, nativeScale, tile, new_argb);
        setTileARGB(dest_idx, new_argb);
    }

    /**
     * Make shulker top/bottom image (based on shulker layouts)
     * @param imgId - source image ID
     * @param dest_idx - destination tile index
     * @param src_x - starting X of source (scaled based on 64 high)
     * @param src_y - starting Y of source (scaled based on 64 high)
     */
    private void makeShulkerTopBottomImage(int imgId, int dest_idx, int src_x, int src_y) {
        int mult = imgs[imgId].width / 64; /* Nominal height for shulker images is 64 */
        int[] tile = new int[16 * 16 * mult * mult];    /* Make image (all are 16x16) */
        copySubimageFromImage(imgId, src_x * mult, src_y * mult, 0, 0, 16 * mult, 16 * mult, tile, 16 * mult);
        /* Put scaled result into tile buffer */
        int[] new_argb = allocateNewARGBBuffer();
        scaleTerrainPNGSubImage(16 * mult, nativeScale, tile, new_argb);
        setTileARGB(dest_idx, new_argb);
    }

    /**
     * Patch tiles based on image with shulker-style layout
     */
    private void patchShulkerImages(int imageId, int tile_top, int tile_bottom, int tile_front, int tile_back, int tile_left, int tile_right) {
        makeShulkerSideImage(imageId, tile_front, 0);
        makeShulkerSideImage(imageId, tile_back, 16);
        makeShulkerSideImage(imageId, tile_left, 32);
        makeShulkerSideImage(imageId, tile_right, 48);
        makeShulkerTopBottomImage(imageId, tile_top, 16, 0);
        makeShulkerTopBottomImage(imageId, tile_bottom, 32, 28);
    }

    private void patchCustomImages(int imageId, int[] imgids, List<CustomTileRec> recs, int xcnt, int ycnt)
    {
        int mult = imgs[imageId].height / (ycnt * 16); /* Compute scale based on nominal tile count vertically (ycnt * 16) */
        for(int i = 0; i < imgids.length; i++) {
            if(imgids[i] <= 0) continue;
            CustomTileRec ctr = recs.get(i);
            if(ctr == null) continue;
            int[] tile = new int[16 * 16 * mult * mult];    /* Make image */
            copySubimageFromImage(imageId, ctr.srcx * mult, ctr.srcy * mult, ctr.targetx * mult, ctr.targety * mult, 
                    ctr.width * mult, ctr.height * mult, tile, 16 * mult);
            /* Put scaled result into tile buffer */
            int[] new_argb = allocateNewARGBBuffer();
            scaleTerrainPNGSubImage(16*mult, nativeScale, tile, new_argb);
            setTileARGB(imgids[i], new_argb);
        }
    }

    private static final List<CustomTileRec> bed_patches =
		Arrays.asList(
		        // TILEINDEX_BED_HEAD_TOP
                new CustomTileRec(6, 6, 16, 16),
                // TILEINDEX_BED_HEAD_BOTTOM
                new CustomTileRec(28, 6, 16, 16),
                // TILEINDEX_BED_HEAD_LEFT
                new CustomTileRec(0, 6, 6, 16),
                // TILEINDEX_BED_HEAD_RIGHT
                new CustomTileRec(22, 6, 6, 16),
                // TILEINDEX_BED_HEAD_END
                new CustomTileRec(6, 0, 16, 6),
                // TILEINDEX_BED_FOOT_TOP
                new CustomTileRec(6, 28, 16, 16),
                // TILEINDEX_BED_FOOT_BOTTOM
                new CustomTileRec(28, 28, 16, 16),
                // TILEINDEX_BED_FOOT_LEFT
                new CustomTileRec(0, 28, 6, 16),
                // TILEINDEX_BED_FOOT_RIGHT
                new CustomTileRec(22, 28, 6, 16),
                // TILEINDEX_BED_FOOT_END
                new CustomTileRec(22, 22, 16, 6),
                // TILEINDEX_BED_HEAD_LEFTLEG_1
                new CustomTileRec(50, 0, 6, 6),
                // TILEINDEX_BED_HEAD_LEFTLEG_2
                new CustomTileRec(56, 0, 6, 6),
                // TILEINDEX_BED_HEAD_RIGHTLEG_1
                new CustomTileRec(50, 6, 6, 6),
                // TILEINDEX_BED_HEAD_RIGHTLEG_2
                new CustomTileRec(56, 6, 6, 6),
                // TILEINDEX_BED_FOOT_LEFTLEG_1
                new CustomTileRec(50, 12, 6, 6),
                // TILEINDEX_BED_FOOT_LEFTLEG_2
                new CustomTileRec(56, 12, 6, 6),
                // TILEINDEX_BED_FOOT_RIGHTLEG_1
                new CustomTileRec(50, 18, 6, 6),
                // TILEINDEX_BED_FOOT_RIGHTLEG_2
                new CustomTileRec(56, 18, 6, 6)
        );
    
    private void patchBedImages(int img_id, int[] imgids) {
        patchCustomImages(img_id, imgids, bed_patches, 64, 64);
    }
    
    /* Copy texture pack */
    private TexturePack(TexturePack tp) {
        this.tileARGB = Arrays.copyOf(tp.tileARGB, tp.tileARGB.length);
        this.nativeScale = tp.nativeScale;
        this.ctm = tp.ctm;
        this.imgs = tp.imgs;
        this.blockColoring = tp.blockColoring;
    }
    
    /* Load terrain */
    private void loadTerrain(boolean resourcePack) throws IOException {
        /* Load image */
        ImageIO.setUseCache(false);
        tileARGB = new int[MAX_TILEINDEX][];
        nativeScale = 16;
        int i;
        if (resourcePack) {
            /* Loop through textures - find size of first one one */
            for(i = 0; i < terrainRPMap.length; i++) {
                String fn = getRPFileName(i);
                if (fn == null) continue;
                DynamicTileFile dtf = addonFilesByName.get(fn);
                if (dtf == null) continue;
                LoadedImage li = imgs[dtf.idx+IMG_CNT];
                if(li != null) {
                    nativeScale = li.width;
                    break;
                }
            }
            blank = allocateNewARGBBuffer();
            /* Now, load scaled images */
            for(i = 0; i < terrainRPMap.length; i++) {
                String fn = getRPFileName(i);
                if (fn == null) continue;
                DynamicTileFile dtf = addonFilesByName.get(fn);
                if (dtf == null) continue;
                LoadedImage li = imgs[dtf.idx + IMG_CNT];
                if(li != null) {
                    int[] buf =  allocateNewARGBBuffer();
                    scaleTerrainPNGSubImage(li.width, nativeScale, li.argb, buf);
                    setTileARGB(i, buf);
                }
            }
        } else {
            /* Loop through textures - find biggest one */
            for(i = 0; i < terrainMap.length; i++) {
                String fn = getBlockFileName(i);
                if (fn == null) continue;
                DynamicTileFile dtf = addonFilesByName.get(fn);
                if (dtf == null) continue;
                LoadedImage li = imgs[dtf.idx+IMG_CNT];
                if(li != null) {
                    if(nativeScale < li.width) nativeScale = li.width;
                }
            }
            blank = allocateNewARGBBuffer();
            /* Now, load scaled images */
            for(i = 0; i < terrainMap.length; i++) {
                String fn = getBlockFileName(i);
                if (fn == null) continue;
                DynamicTileFile dtf = addonFilesByName.get(fn);
                if (dtf == null) continue;
                LoadedImage li = imgs[dtf.idx + IMG_CNT];
                if(li != null) {
                    int[] buf = allocateNewARGBBuffer();
                    scaleTerrainPNGSubImage(li.width, nativeScale, li.argb, buf);
                    setTileARGB(i, buf);
                }
            }
        }
        /* Now, build redstone textures with active wire color (since we're not messing with that) */
        Color tc = new Color();
        int[] red_nsew_tone = getTileARGB(TILEINDEX_REDSTONE_NSEW_TONE);
        int[] red_nsew = getTileARGB(TILEINDEX_REDSTONE_NSEW);
        int[] red_ew_tone = getTileARGB(TILEINDEX_REDSTONE_EW_TONE);
        int[] red_ew = getTileARGB(TILEINDEX_REDSTONE_EW);
        
        for(i = 0; i < nativeScale * nativeScale; i++) {
            if(red_nsew_tone[i] != 0) {
                /* Overlay NSEW redstone texture with toned wire color */
                tc.setARGB(red_nsew_tone[i]);
                tc.blendColor(0xFFC00000);  /* Blend in red */
                red_nsew[i] = tc.getARGB();
            }
            if(red_ew_tone[i] != 0) {
                /* Overlay NSEW redstone texture with toned wire color */
                tc.setARGB(red_ew_tone[i]);
                tc.blendColor(0xFFC00000);  /* Blend in red */
                red_ew[i] = tc.getARGB();
            }
        }
        /* Build extended piston side texture - take top 1/4 of piston side, use to make piston extension */
        int[] buf = allocateNewARGBBuffer();
        setTileARGB(TILEINDEX_PISTONEXTSIDE, buf);
        int[] pistonSide = getTileARGB(TILEINDEX_PISTONSIDE);
        System.arraycopy(pistonSide, 0, buf, 0, nativeScale * nativeScale / 4);
        int j;
        for(i = 0; i < nativeScale /4; i++) {
            for(j = 0; j < (3* nativeScale /4); j++) {
                buf[nativeScale *(nativeScale /4 + j) + (3* nativeScale /8 + i)] = pistonSide[nativeScale *i + j];
            }
        }
        /* Build piston side while extended (cut off top 1/4, replace with rotated top for extension */
        int[] ints = allocateNewARGBBuffer();
        setTileARGB(TILEINDEX_PISTONSIDE_EXT, ints);
        System.arraycopy(pistonSide, nativeScale * nativeScale /4, ints, nativeScale * nativeScale /4,
             3 * nativeScale * nativeScale / 4);  /* Copy bottom 3/4 */
        for(i = 0; i < nativeScale /4; i++) {
            for(j = 3* nativeScale /4; j < nativeScale; j++) {
                ints[nativeScale *(j - 3* nativeScale /4) + (3* nativeScale /8 + i)] =
                    pistonSide[nativeScale *i + j];
            }
        }
        /* Build glass pane top in NSEW config (we use model to clip it) */
        int[] ints1 = allocateNewARGBBuffer();
        setTileARGB(TILEINDEX_PANETOP_X, ints1);
        int[] glassPaneTop = getTileARGB(TILEINDEX_GLASSPANETOP);
        System.arraycopy(glassPaneTop, 0, ints1, 0, nativeScale * nativeScale);
        for(i = nativeScale *7/16; i < nativeScale *9/16; i++) {
            for(j = 0; j < nativeScale; j++) {
                ints1[nativeScale *i + j] = ints1[nativeScale *j + i];
            }
        }
        /* Build air frame with eye overlay */
        int[] ints2 = allocateNewARGBBuffer();
        setTileARGB(TILEINDEX_AIRFRAME_EYE, ints2);
        int[] airframe = getTileARGB(TILEINDEX_AIRFRAME);
        int[] eyeofender = getTileARGB(TILEINDEX_EYEOFENDER);
        System.arraycopy(airframe, 0, ints2, 0, nativeScale * nativeScale);
        for(i = nativeScale /4; i < nativeScale *3/4; i++) {
            for(j = nativeScale /4; j < nativeScale *3/4; j++) {
                ints2[nativeScale *i + j] = eyeofender[nativeScale *i + j];
            }
        }
        /* Build white tile */
        ints2 = allocateNewARGBBuffer();
        setTileARGB(TILEINDEX_WHITE, ints2);
        Arrays.fill(ints2, 0xFFFFFFFF);
    }
    
    /* Load image into image array */
    private void loadImage(InputStream is, int idx, String fname, String modid) throws IOException {
        BufferedImage img = null;
        /* Load image */
        if(is != null) {
            ImageIO.setUseCache(false);
            img = ImageIO.read(is);
            if(img == null) { throw new FileNotFoundException(); }
        }
        if(idx >= imgs.length) {
            LoadedImage[] newimgs = new LoadedImage[idx+1];
            System.arraycopy(imgs, 0, newimgs, 0, imgs.length);
            imgs = newimgs;
        }
        imgs[idx] = new LoadedImage();
        imgs[idx].width = img != null ? img.getWidth() : 16;
        imgs[idx].height = img != null ? img.getHeight() : 16;
        imgs[idx].argb = new int[imgs[idx].width * imgs[idx].height];
        if (img != null) {
            img.getRGB(0, 0, imgs[idx].width, imgs[idx].height, imgs[idx].argb, 0, imgs[idx].width);
            img.flush();
            imgs[idx].isLoaded = true;
        } else {  // Pad with blank image
        }

        imgs[idx].fname = fname;
        imgs[idx].modid = modid;
    }
        

    /* Process dynamic texture files, and patch into terrain_argb */
    private void processDynamicImage(int idx, TileFileFormat format) {
        DynamicTileFile dtf = addonfiles.get(idx);  /* Get tile file definition */
        LoadedImage li = imgs[idx+IMG_CNT];
        if (li == null) return;
        
        switch(format) {
            case GRID:  /* If grid format tile file */
                int dim = li.width / dtf.tilecnt_x; /* Dimension of each tile */
                int dim2 = li.height / dtf.tilecnt_y;
                if (dim2 < dim) dim = dim2;
                int[] old_argb = new int[dim*dim];
                for(int x = 0; x < dtf.tilecnt_x; x++) {
                    for(int y = 0; y < dtf.tilecnt_y; y++) {
                        int tileidx = dtf.tile_to_dyntile[y*dtf.tilecnt_x + x];
                        if (tileidx < 0) continue;
                        if((tileidx >= terrainMap.length) || (terrainMap[tileidx] == null)) {    /* dynamic ID? */
                            /* Copy source tile */
                            for(int j = 0; j < dim; j++) {
                                System.arraycopy(li.argb, (y*dim+j)*li.width + (x*dim), old_argb, j*dim, dim); 
                            }
                            /* Rescale to match rest of terrain PNG */
                            int[] new_argb = allocateNewARGBBuffer();
                            scaleTerrainPNGSubImage(dim, nativeScale, old_argb, new_argb);
                            setTileARGB(tileidx, new_argb);
                        }
                    }
                }
                break;
            case CHEST:
                patchChestImages(idx+IMG_CNT, dtf.tile_to_dyntile[TILEINDEX_CHEST_TOP], dtf.tile_to_dyntile[TILEINDEX_CHEST_BOTTOM], dtf.tile_to_dyntile[TILEINDEX_CHEST_FRONT], dtf.tile_to_dyntile[TILEINDEX_CHEST_BACK], dtf.tile_to_dyntile[TILEINDEX_CHEST_LEFT], dtf.tile_to_dyntile[TILEINDEX_CHEST_RIGHT]);
                break;
            case BIGCHEST:
                patchLargeChestImages(idx+IMG_CNT, dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_TOPRIGHT], dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_TOPLEFT], dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_BOTTOMRIGHT], dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_BOTTOMLEFT], dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_RIGHT], dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_LEFT], dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_FRONTRIGHT], dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_FRONTLEFT], dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_BACKRIGHT], dtf.tile_to_dyntile[TILEINDEX_BIGCHEST_BACKLEFT]);
                break;
            case SIGN:
                patchSignImages(idx+IMG_CNT, dtf.tile_to_dyntile[TILEINDEX_SIGN_FRONT], dtf.tile_to_dyntile[TILEINDEX_SIGN_BACK], dtf.tile_to_dyntile[TILEINDEX_SIGN_TOP], dtf.tile_to_dyntile[TILEINDEX_SIGN_BOTTOM], dtf.tile_to_dyntile[TILEINDEX_SIGN_LEFTSIDE], dtf.tile_to_dyntile[TILEINDEX_SIGN_RIGHTSIDE], dtf.tile_to_dyntile[TILEINDEX_SIGN_POSTFRONT], dtf.tile_to_dyntile[TILEINDEX_SIGN_POSTBACK], dtf.tile_to_dyntile[TILEINDEX_SIGN_POSTLEFT], dtf.tile_to_dyntile[TILEINDEX_SIGN_POSTRIGHT]);
                break;
            case SKIN:
                patchSkinImages(idx+IMG_CNT, dtf.tile_to_dyntile[TILEINDEX_SKIN_FACEFRONT], dtf.tile_to_dyntile[TILEINDEX_SKIN_FACELEFT], dtf.tile_to_dyntile[TILEINDEX_SKIN_FACERIGHT], dtf.tile_to_dyntile[TILEINDEX_SKIN_FACEBACK], dtf.tile_to_dyntile[TILEINDEX_SKIN_FACETOP], dtf.tile_to_dyntile[TILEINDEX_SKIN_FACEBOTTOM]);
                break;
            case SHULKER:
                patchShulkerImages(idx+IMG_CNT, dtf.tile_to_dyntile[TILEINDEX_SHULKER_TOP], dtf.tile_to_dyntile[TILEINDEX_SHULKER_BOTTOM], dtf.tile_to_dyntile[TILEINDEX_SHULKER_FRONT], dtf.tile_to_dyntile[TILEINDEX_SHULKER_BACK], dtf.tile_to_dyntile[TILEINDEX_SHULKER_LEFT], dtf.tile_to_dyntile[TILEINDEX_SHULKER_RIGHT]);
                break;
            case CUSTOM:
                patchCustomImages(idx+IMG_CNT, dtf.tile_to_dyntile, dtf.cust, dtf.tilecnt_x, dtf.tilecnt_y);
                break;
            case BED:
                patchBedImages(idx+IMG_CNT, dtf.tile_to_dyntile);
                break;
            case TILESET:
                break;
            default:
                break;
        }
        if (dtf.tile_to_dyntile != null) {
            for (int i = 0; i < dtf.tile_to_dyntile.length; i++) {
                if (dtf.tile_to_dyntile[i] >= 0) {
                    if (dtf.material != null) {
                        materialbytileid.put(dtf.tile_to_dyntile[i], dtf.material);
                    }
                    setMatIDForTileID(dtf.filename, dtf.tile_to_dyntile[i]);
                }
            }
        }
    }
    /* Load biome shading image into image array */
    private void loadBiomeShadingImage(InputStream is, int idx, String fname, String modid) throws IOException {
        loadImage(is, idx, fname, modid); /* Get image */
        LoadedImage li = imgs[idx];
        if (li.width != 256) {  /* Required to be 256 x 256 */
            int[] scaled = new int[256*256];
            scaleTerrainPNGSubImage(li.width, 256, li.argb, scaled);
            li.argb = scaled;
            li.width = 256;
            li.height = 256;
        }
        /* Get trivial color for biome-shading image */
        int clr = li.argb[li.height*li.width*3/4 + li.width/2];
        boolean same = true;
        for(int j = 0; same && (j < li.height); j++) {
            for(int i = 0; same && (i <= j); i++) {
                if(li.argb[li.width*j+i] != clr)
                    same = false;
            }
        }
        /* All the same - no biome lookup needed */
        if(same) {
            li.trivial_color = clr;
        }
        else {  /* Else, calculate color average for lower left quadrant */
            int[] clr_scale = new int[16];
            scaleTerrainPNGSubImage(li.width, 4, li.argb, clr_scale);
            li.trivial_color = clr_scale[9];
        }
        // If we didn't actually load, don't use color lookup for this (handle broken RPs like John Smith)
        if (!li.isLoaded) {
            this.blockColoring.scrubValues(idx);
        }
    }
    
    /* Patch image into texture table */
    private void patchTextureWithImage(int image_idx, int block_idx) {
        /* Now, patch in to block table */
        int[] new_argb = allocateNewARGBBuffer();
        scaleTerrainPNGSubImage(imgs[image_idx].width, nativeScale, imgs[image_idx].argb, new_argb);
        setTileARGB(block_idx, new_argb);
    }

    /* Get texture pack directory */
    private static File getTexturePackDirectory(DynmapCore core) {
        return new File(core.getDataFolder(), "texturepacks");
    }

    /**
     * Resample terrain pack for given scale, and return copy using that scale
     * @param scale - scale
     * @return resampled texture pack
     */
    public TexturePack resampleTexturePack(int scale) {
        synchronized(scaledMutex) {
            if(scaledTextures == null) scaledTextures = new HashMap<>();
            TexturePack stp = scaledTextures.get(scale);
            if(stp != null)
                return stp;
            /* Make copy */
            TexturePack texturePack = new TexturePack(this);
            /* Scale terrain.png, if needed */
            if(texturePack.nativeScale != scale) {
                texturePack.nativeScale = scale;
                scaleTerrainPNG(texturePack);
            }
            /* Remember it */
            scaledTextures.put(scale, texturePack);
            return texturePack;
        }
    }
    /**
     * Scale our terrain_argb into the terrain_argb of the provided destination, matching the scale of that destination
     * @param tp
     */
    private void scaleTerrainPNG(TexturePack tp) {
        tp.tileARGB = new int[tileARGB.length][];
        /* Terrain.png is 16x16 array of images : process one at a time */
        for(int idx = 0; idx < tileARGB.length; idx++) {
            tp.tileARGB[idx] = new int[tp.nativeScale *tp.nativeScale];
            scaleTerrainPNGSubImage(nativeScale, tp.nativeScale, getTileARGB(idx),  tp.tileARGB[idx]);
        }
        /* Special case - some textures are used as masks - need pure alpha (00 or FF) */
        makeAlphaPure(tp.tileARGB[TILEINDEX_GRASSMASK]); /* Grass side mask */
    }
    public static void scaleTerrainPNGSubImage(int sourceScale, int destScale, int[] src_argb, int[] dest_argb) {
        Color c = new Color();
        /* Same size, so just copy */
        if(destScale == sourceScale) {
            System.arraycopy(src_argb, 0, dest_argb, 0, dest_argb.length);
        }
        /* If we're scaling larger source pixels into smaller pixels, each destination pixel
         * receives input from 1 or 2 source pixels on each axis
         */
        else if(destScale > sourceScale) {
            int[] weights = new int[destScale];
            int[] offsets = new int[destScale];
            /* LCM of resolutions is used as length of line (res * nativeres)
             * Each native block is (res) long, each scaled block is (nativeres) long
             * Each scaled block overlaps 1 or 2 native blocks: starting with native block 'offsets[]' with
             * 'weights[]' of its (res) width in the first, and the rest in the second
             */
            for(int v = 0, idx = 0; v < destScale * sourceScale; v += sourceScale, idx++) {
                offsets[idx] = (v/ destScale); /* Get index of the first native block we draw from */
                if((v+ sourceScale -1)/ destScale == offsets[idx]) {   /* If scaled block ends in same native block */
                    weights[idx] = sourceScale;
                }
                else {  /* Else, see how much is in first one */
                    weights[idx] = (offsets[idx]* destScale + destScale) - v;
                }
            }
            /* Now, use weights and indices to fill in scaled map */
            for(int y = 0; y < destScale; y++) {
                int ind_y = offsets[y];
                int wgt_y = weights[y];
                for(int x = 0; x < destScale; x++) {
                    int ind_x = offsets[x];
                    int wgt_x = weights[x];
                    double accum_red = 0;
                    double accum_green = 0;
                    double accum_blue = 0;
                    double accum_alpha = 0;
                    for(int xx = 0; xx < 2; xx++) {
                        int wx = (xx==0)?wgt_x:(sourceScale -wgt_x);
                        if(wx == 0) continue;
                        for(int yy = 0; yy < 2; yy++) {
                            int wy = (yy==0)?wgt_y:(sourceScale -wgt_y);
                            if(wy == 0) continue;
                            /* Accumulate */
                            c.setARGB(src_argb[(ind_y+yy)* sourceScale + ind_x + xx]);
                            int w = wx * wy;
                            double a = (double)w * (double)c.getAlpha();
                            accum_red += c.getRed() * a;
                            accum_green += c.getGreen() * a;
                            accum_blue += c.getBlue() * a;
                            accum_alpha += a;
                        }
                    }
                    double newalpha = accum_alpha;
                    if(newalpha == 0.0) newalpha = 1.0;
                    /* Generate weighted compnents into color */
                    c.setRGBA((int)(accum_red / newalpha), (int)(accum_green / newalpha), 
                              (int)(accum_blue / newalpha), (int)(accum_alpha / (sourceScale * sourceScale)));
                    dest_argb[(y* destScale) + x] = c.getARGB();
                }
            }
        }
        else {  /* nativeres > res */
            int[] weights = new int[sourceScale];
            int[] offsets = new int[sourceScale];
            /* LCM of resolutions is used as length of line (res * nativeres)
             * Each native block is (res) long, each scaled block is (nativeres) long
             * Each native block overlaps 1 or 2 scaled blocks: starting with scaled block 'offsets[]' with
             * 'weights[]' of its (res) width in the first, and the rest in the second
             */
            for(int v = 0, idx = 0; v < destScale * sourceScale; v += destScale, idx++) {
                offsets[idx] = (v/ sourceScale); /* Get index of the first scaled block we draw to */
                if((v+ destScale -1)/ sourceScale == offsets[idx]) {   /* If native block ends in same scaled block */
                    weights[idx] = destScale;
                }
                else {  /* Else, see how much is in first one */
                    weights[idx] = (offsets[idx]* sourceScale + sourceScale) - v;
                }
            }
            double[] accum_red = new double[destScale * destScale];
            double[] accum_green = new double[destScale * destScale];
            double[] accum_blue = new double[destScale * destScale];
            double[] accum_alpha = new double[destScale * destScale];
            
            /* Now, use weights and indices to fill in scaled map */
            for(int y = 0; y < sourceScale; y++) {
                int ind_y = offsets[y];
                int wgt_y = weights[y];
                for(int x = 0; x < sourceScale; x++) {
                    int ind_x = offsets[x];
                    int wgt_x = weights[x];
                    c.setARGB(src_argb[(y* sourceScale) + x]);
                    for(int xx = 0; xx < 2; xx++) {
                        int wx = (xx==0)?wgt_x:(destScale -wgt_x);
                        if(wx == 0) continue;
                        for(int yy = 0; yy < 2; yy++) {
                            int wy = (yy==0)?wgt_y:(destScale -wgt_y);
                            if(wy == 0) continue;
                            double w = wx * wy;
                            double a = w * c.getAlpha();
                            final int i = (ind_y + yy) * destScale + (ind_x + xx);
                            accum_red[i] += c.getRed() * a;
                            accum_green[i] += c.getGreen() * a;
                            accum_blue[i] += c.getBlue() * a;
                            accum_alpha[i] += a;
                        }
                    }
                }
            }
            /* Produce normalized scaled values */
            for(int y = 0; y < destScale; y++) {
                for(int x = 0; x < destScale; x++) {
                    int off = (y* destScale) + x;
                    double aa = accum_alpha[off];
                    if(aa == 0.0) aa = 1.0;
                    c.setRGBA((int)(accum_red[off]/aa), (int)(accum_green[off]/aa),
                          (int)(accum_blue[off]/aa), (int)(accum_alpha[off] / (sourceScale * sourceScale)));
                    dest_argb[y* destScale + x] = c.getARGB();
                }
            }
        }
    }
    private static void addFiles(List<String> tsfiles, List<String> txfiles, File dir, String path) {
        File[] listFiles = dir.listFiles();
        if(listFiles == null) return;
        Arrays.stream(listFiles).forEachOrdered(f -> {
            String fileName = f.getName();
            if (fileName.equals(".") || (fileName.equals(".."))) return;
            if (f.isFile()) {
                String ps1 = path + fileName;
                if (fileName.endsWith("-texture.txt")) {
                    txfiles.add(ps1);
                }
                if (fileName.endsWith("-tilesets.txt")) {
                    tsfiles.add(ps1);
                }
            } else if (f.isDirectory()) {
                addFiles(tsfiles, txfiles, f, path + f.getName() + "/");
            }
        });
    }
    /**
     * Load texture pack mappings
     * @param core - core object
     * @param config - configuration for texture mapping
     */
    public static void loadTextureMapping(DynmapCore core, ConfigurationNode config) {
        File datadir = core.getDataFolder();
        /* Start clean with texture packs - need to be loaded after mapping */
        resetFiles(core);
        /* Initialize map with blank map for all entries */
        HDBlockStateTextureMap.initializeTable();
        /* Load block textures (0-N) */
        int i = 0;
        boolean done = false;
        while (!done) {
            try {
                try (InputStream in = TexturePack.class.getResourceAsStream("/texture_" + i + ".txt")) {
                    if (in != null) {
                        loadTextureFile(in, "texture_" + i + ".txt", config, core, "core");
                    } else {
                        done = true;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            i++;
        }
        // Check mods to see if texture files defined there
        for (String modid : core.getServer().getModList()) {
            File f = core.getServer().getModContainerFile(modid);   // Get mod file
            if ((f != null) && f.isFile()) {
                try (ZipFile zf = new ZipFile(f)) {
                    String fn = "assets/" + modid.toLowerCase() + "/dynmap-texture.txt";
                    ZipEntry ze = zf.getEntry(fn);
                    if (ze != null) {
                        try (InputStream in = zf.getInputStream(ze)) {
                            loadTextureFile(in, fn, config, core, modid);
                        }
                        loadedmods.add(modid);  // Add to set: prevent others definitions for same mod
                    }
                } catch (IOException e) {
                }
            }
        }
        // Load external tile sets
        File renderdir = new File(datadir, "renderdata");
        List<String> tsFiles = new ArrayList<>();
        List<String> txFiles = new ArrayList<>();
        addFiles(tsFiles, txFiles, renderdir, "");
        for(String fileName : tsFiles) {
            File custom = new File(renderdir, fileName);
            if(custom.canRead()) {
                try(InputStream in2 = new FileInputStream(custom)) {
                    loadTileSetsFile(in2, custom.getPath(), config, core, HDBlockModels.getModIDFromFileName(fileName));
                } catch (IOException iox) {
                    Log.severe("Error loading " + custom.getPath() + " - " + iox);
                }
            }
        }
        // Load external texture files (before internals, to allow them to override them)
        for(String fileName : txFiles) {
            File custom = new File(renderdir, fileName);
            if(custom.canRead()) {
                try(InputStream in2 = new FileInputStream(custom) ) {
                    loadTextureFile(in2, custom.getPath(), config, core, HDBlockModels.getModIDFromFileName(fileName));
                } catch (IOException iox) {
                    Log.severe("Error loading " + custom.getPath() + " - " + iox);
                }
            }
        }

        // Load internal texture files (last, so that others can override)
        try (ZipFile zf = new ZipFile(core.getPluginJarFile())){
            EnumerationIntoIterator<ZipEntry> x = new EnumerationIntoIterator<>(zf.entries());
            while (x.hasNext()) {
                ZipEntry ze = x.next();
                String name = ze.getName();
                if (!name.startsWith("renderdata/")) continue;
                if (!name.endsWith("-texture.txt")) continue;
                try (InputStream in2 = zf.getInputStream(ze)) {
                    if (in2 != null) {
                        loadTextureFile(in2, name, config, core, HDBlockModels.getModIDFromFileName(name));
                    }
                }
            }
        } catch (IOException iox) {
            Log.severe("Error processing texture files");
        }
        /* Finish processing of texture maps */
        processTextureMaps();
        /* Check integrity of texture mappings versus models */
        for (int gidx = 0; gidx < DynmapBlockState.getGlobalIndexMax(); gidx++) {
            DynmapBlockState blk = DynmapBlockState.getStateByGlobalIndex(gidx);
            if (blk.isAir()) continue;
            HDBlockStateTextureMap tm = HDBlockStateTextureMap.getByBlockState(blk);
            if (tm == HDBlockStateTextureMap.BLANK) {
                Log.verboseinfo("Block " + blk + " - no texture mapping");
            }
            int cnt = HDBlockModels.getNeededTextureCount(blk);
            if(cnt > tm.faces.length){
                Log.severe("Block " + blk + " - not enough textures for faces (" + cnt + " > " + tm.faces.length + ")");
                tm.resizeFaces(cnt);
            }
        }
        // Check to see if any blocks exist without corresponding mappings
        if (core.dumpMissingBlocks()) {
            String missing = "";
            /* Check integrity of texture mappings versus models */
            for (int gidx = 0; gidx < DynmapBlockState.getGlobalIndexMax(); gidx++) {
                DynmapBlockState blk = DynmapBlockState.getStateByGlobalIndex(gidx);
                if (!blk.isNotAir()) continue;
                if (blk.stateIndex != 0) continue;
                boolean blank = true;
                for (int stateid = 0; blank && (stateid < blk.getStateCount()); stateid++) {
                    DynmapBlockState blk2 = blk.getState(stateid);
                    HDBlockStateTextureMap tm = HDBlockStateTextureMap.getByBlockState(blk2);
                    if (tm != HDBlockStateTextureMap.BLANK) {
                        blank = false;
                    }
                }
                if (blank) {
                    missing += blk.blockName + "\n";
                }
            }
            if (missing.length() > 0) {
                Log.warning("Blocks missing texture definition:\n" + missing);
            }
        }
    }

    private static String getBlockName(String modid, String val) throws NumberFormatException {
        char c = val.charAt(0);
        if(Character.isLetter(c) || (c == '%') || (c == '&')) {
            if ((c == '%') || (c == '&')) {
                val = val.substring(1);
            }
            int plusPos = val.indexOf('+');
            if (plusPos > 0) {
                val = val.substring(0,  plusPos);
            }
            if (val.indexOf(':') < 0) {
                val = modid + ":" + val;
            }
            return val;
        }
        else {
            throw new NumberFormatException("invalid ID - " + val);
        }
    }
    
    private static Integer getIntValue(Map<String,Integer> vars, String toParse) throws NumberFormatException {
        char c = toParse.charAt(0);
        if(Character.isLetter(c) || (c == '%') || (c == '&')) {
            int off = toParse.indexOf('+');
            int offset = 0;
            if (off > 0) {
                offset = Integer.parseInt(toParse.substring(off+1));
                toParse = toParse.substring(0,  off);
            }
            Integer v = vars.get(toParse);
            if(v == null) {
                if ((c == '%') || (c == '&')) {
                    vars.put(toParse, 0);
                    v = 0;
                }
                else {
                    throw new NumberFormatException("invalid ID - " + toParse);
                }
            }
            if((offset != 0) && (v > 0))
                v = v + offset;
            return v;
        }
        else {
            return Integer.valueOf(toParse);
        }
    }

    private static int parseTextureIndex(Map<String, Integer> fileToIndex, int srctxtid, String val) throws NumberFormatException {
        int off = val.indexOf(':');
        int txtid;
        if(off > 0) {
            String txt = val.substring(off+1);
            if(fileToIndex.containsKey(txt)) {
                srctxtid = fileToIndex.get(txt);
            } else {
                throw new NumberFormatException("Unknown attribute: " + txt);
            }
            txtid = Integer.parseInt(val.substring(0, off));
        } else {
            txtid = Integer.parseInt(val);
        }
        /* Shift function code from x1000 to x1000000 for internal processing */
        int funcid = (txtid / COLORMOD_MULT_FILE);
        txtid = txtid - (COLORMOD_MULT_FILE * funcid);
        /* If we have source texture, need to map values to dynamic ids */
        if((srctxtid >= 0) && (txtid >= 0)) {
            /* Map to assigned ID in global tile table: preserve modifier */
            txtid =findOrAddDynamicTile(srctxtid, txtid); 
        }
        if(srctxtid == TXTID_INVALID) {
            throw new NumberFormatException("Invalid texture ID: no default terrain.png: " + val);
        }
        return txtid + (COLORMOD_MULT_INTERNAL * funcid);
    }
    /**
     * Load texture pack mappings from tilesets.txt file
     */
    private static void loadTileSetsFile(InputStream txtfile, String txtname, ConfigurationNode config, DynmapCore core, String blockset) {
        try (
        LineNumberReader rdr = new LineNumberReader(new InputStreamReader(txtfile));
        ) {
            try {
                String line;
                DynamicTileFile tfile = null;
                while ((line = rdr.readLine()) != null) {
                    if (line.startsWith("#")) {
                    } else if (line.startsWith("tileset:")) { /* Start of tileset definition */
                        line = line.substring(line.indexOf(':') + 1);
                        int xdim = 16, ydim = 16;
                        String fname = null;
                        String setdir = null;
                        String[] toks = line.split(",");
                        for (String tok : toks) {
                            String[] v = tok.split("=");
                            if (v.length < 2) continue;
                            switch (v[0]) {
                                case "xcount":
                                    xdim = Integer.parseInt(v[1]);
                                    break;
                                case "ycount":
                                    ydim = Integer.parseInt(v[1]);
                                    break;
                                case "setdir":
                                    setdir = v[1];
                                    break;
                                case "filename":
                                    fname = v[1];
                                    break;
                            }
                        }
                        if ((fname != null) && (setdir != null)) {
                            /* Register tile file */
                            int fid = findOrAddDynamicTileFile(fname, null, xdim, ydim, TileFileFormat.TILESET, new String[0]);
                            tfile = addonfiles.get(fid);
                            if (tfile == null) {
                                Log.severe("Error registering tile set " + fname + " at " + rdr.getLineNumber() + " of " + txtname);
                                return;
                            }
                            /* Initialize tile name map and set directory path */
                            tfile.tilenames = new String[tfile.tile_to_dyntile.length];
                        } else {
                            Log.severe("Error defining tile set at " + rdr.getLineNumber() + " of " + txtname);
                            return;
                        }
                    } else if (Character.isDigit(line.charAt(0))) {    /* Starts with digit?  tile mapping */
                        int split = line.indexOf('-');  /* Find first dash */
                        if (split < 0) continue;
                        String id = line.substring(0, split).trim();
                        String name = line.substring(split + 1).trim();
                        String[] coords = id.split(",");
                        int idx = -1;
                        int zeroth = Integer.parseInt(coords[0]);
                        if (coords.length == 2) { /* If x,y */
                            idx = (Integer.parseInt(coords[1]) * tfile.tilecnt_x) + zeroth;
                        } else if (coords.length == 1) { /* Just index */
                            idx = zeroth;
                        }
                        if ((idx >= 0) && (idx < tfile.tilenames.length)) {
                            tfile.tilenames[idx] = name;
                        } else {
                            Log.severe("Bad tile index - line " + rdr.getLineNumber() + " of " + txtname);
                        }
                    }
                }
            } catch (IOException iox) {
                Log.severe("Error reading " + txtname + " - " + iox.toString());
            } catch (NumberFormatException nfx) {
                Log.severe("Format error - line " + rdr.getLineNumber() + " of " + txtname + ": " + nfx.getMessage());
            }
        } catch (IOException iox) {
            Log.severe("Error reading " + txtname + " - " + iox.toString());
        }
    }
    private static final int TXTID_INVALID = -2;
    private static final int TXTID_TERRAINPNG = -1;
    /**
     * Load texture pack mappings from texture.txt file
     */
    private static void loadTextureFile(InputStream txtfile, String txtname, ConfigurationNode config, DynmapCore core, String blockset) {
        final String version = core.getDynmapPluginPlatformVersion();
        try (LineNumberReader rnr = new LineNumberReader(new InputStreamReader(txtfile))) {
            final boolean[] terrain_ok = {true};
            final String[] texturePath = {null};
            final String[] textureMod = {null};
            final String[] modVersion = {null};
            // Default to minecraft base
            final String[] modName = {"minecraft"};
            final boolean[] modConfigLoaded = {false};
            final boolean[] modConfigNeeded = {false};
            Map<String, Integer> variableToValue = new HashMap<>();
            Map<String, Integer> fileToIndex = new HashMap<>();
            final int[] cnt = {0};
            rnr.lines().forEach(line -> {
                boolean skip = false;
                if ((line.length() > 0) && (line.charAt(0) == '[')) {    // If version constrained like
                    int end = line.indexOf(']');    // Find end
                    if (end < 0) {
                        Log.severe("Format error - line " + rnr.getLineNumber() + " of " + txtname + ": bad version limit");
                        return;
                    }
                    String vertst = line.substring(1, end);
                    String tver = version;
                    if (vertst.startsWith("mod:")) {    // If mod version ranged
                        tver = modVersion[0];
                        vertst = vertst.substring(4);
                    }
                    if (!HDBlockModels.checkVersionRange(tver, vertst)) {
                        skip = true;
                    }
                    line = line.substring(end+1);
                }
                // If we're skipping due to version restriction
                if (skip) {
                } else if(line.startsWith("block:")) {
                    int srctxtid = TXTID_TERRAINPNG;
                    if (!terrain_ok[0])
                        srctxtid = TXTID_INVALID;  // Mark as not usable
                    line = line.substring(6);
                    String[] args = line.split(",");
                    for(String arg : args) {
                        String[] keyValue = arg.split("=");
                        String key = keyValue[0];
                        String value = keyValue[1];
                        if(keyValue.length < 2) {
                        } else if(key.equals("txtid")) {
                            if(fileToIndex.containsKey(value)) {
                                srctxtid = fileToIndex.get(value);
                            } else {
                                Log.severe("Format error - line " + rnr.getLineNumber() + " of " + txtname + ": bad texture " + value);
                            }
                        }
                    }
                    // Build ID list : abort rest of processing if no valid values
                    List<String> blockIds = new ArrayList<>();
                    Arrays.stream(args)
                            .map(a -> a.split("="))
                            .filter(av -> av.length >= 2)
                            .forEachOrdered(av -> {
                                final String key = av[0];
                                final String value = av[1];
                                if (key.equals("id")) {
                                    String id = getBlockName(modName[0], value);
                                    if (id != null) {
                                        blockIds.add(id);
                                    }
                                }
                            });
                    if (blockIds.size() > 0) {
                        CustomColorMultiplier custColorMult = null;
                        // Legacy top/bottom rotation
                        boolean stdrot = false;
                        int blockColorIdx = -1;
                        int colorMult = 0;
                        BlockTransparency trans = BlockTransparency.OPAQUE;
                        int[] txtidx = {-1, -1, -1, -1, -1, -1};
                        int[] faces = {TILEINDEX_BLANK, TILEINDEX_BLANK, TILEINDEX_BLANK, TILEINDEX_BLANK, TILEINDEX_BLANK, TILEINDEX_BLANK};
                        BitSet stateids = null;
                        for(String arg : args) {
                            String[] equalKeyValue = arg.split("=");
                            if(equalKeyValue.length < 2) continue;
                            final String key = equalKeyValue[0];
                            final String value = equalKeyValue[1];
                            if(key.equals("data")) {
                                if(value.equals("*")) {
                                    stateids = null;
                                } else {
                                    if (stateids == null) { stateids = new BitSet(); }
                                    // See if range
                                    if (value.indexOf('-') >= 0) {
                                        String[] tok = value.split("-");
                                        int v1 = getIntValue(variableToValue, tok[0]);
                                        int v2 = getIntValue(variableToValue, tok[1]);
                                        for (int v = v1; v <= v2; v++) {
                                            stateids.set(v);
                                        }
                                    }
                                    else {
                                        stateids.set(getIntValue(variableToValue,value));
                                    }
                                }
                            } else if(key.equals("top") || key.equals("y-") || key.equals("face1")) {
                                faces[BlockStep.Y_MINUS.ordinal()] = parseTextureIndex(fileToIndex, srctxtid, value);
                            } else if(key.equals("bottom") || key.equals("y+") || key.equals("face0")) {
                                faces[BlockStep.Y_PLUS.ordinal()] = parseTextureIndex(fileToIndex, srctxtid, value);
                            } else if(key.equals("north") || key.equals("x+") || key.equals("face4")) {
                                faces[BlockStep.X_PLUS.ordinal()] = parseTextureIndex(fileToIndex, srctxtid, value);
                            } else if(key.equals("south") || key.equals("x-") || key.equals("face5")) {
                                faces[BlockStep.X_MINUS.ordinal()] = parseTextureIndex(fileToIndex, srctxtid, value);
                            } else if(key.equals("west") || key.equals("z-") || key.equals("face3")) {
                                faces[BlockStep.Z_MINUS.ordinal()] = parseTextureIndex(fileToIndex, srctxtid, value);
                            } else if(key.equals("east") || key.equals("z+") || key.equals("face2")) {
                                faces[BlockStep.Z_PLUS.ordinal()] = parseTextureIndex(fileToIndex, srctxtid, value);
                            } else if(key.startsWith("face")) {
                                int fid0, fid1;
                                String idrange = key.substring(4);
                                String[] ids = idrange.split("-");
                                if(ids.length > 1) {
                                    fid0 = Integer.parseInt(ids[0]);
                                    fid1 = Integer.parseInt(ids[1]);
                                } else {
                                    fid0 = fid1 = Integer.parseInt(ids[0]);
                                }
                                if((fid0 < 0) || (fid1 < fid0)) {
                                    Log.severe("Texture mapping has invalid face index - " + value + " - line " + rnr.getLineNumber() + " of " + txtname);
                                    return;
                                }
                                int[] faceToOrd = {
                                        BlockStep.Y_PLUS.ordinal(), BlockStep.Y_MINUS.ordinal(),
                                        BlockStep.Z_PLUS.ordinal(), BlockStep.Z_MINUS.ordinal(),
                                        BlockStep.X_PLUS.ordinal(), BlockStep.X_MINUS.ordinal()
                                };
                                int txtId = parseTextureIndex(fileToIndex, srctxtid, value);
                                for(int i = fid0; (i <= fid1) && (i < 6); i++) {
                                    faces[faceToOrd[i]] = txtId;
                                }
                            } else if(key.equals("allfaces")) {
                                int id = parseTextureIndex(fileToIndex, srctxtid, value);
                                for(int i = 0; i < 6; i++) {
                                    faces[i] = id;
                                }
                            } else if(key.equals("allsides")) {
                                int id = parseTextureIndex(fileToIndex, srctxtid, value);
                                faces[BlockStep.X_PLUS.ordinal()] = id;
                                faces[BlockStep.X_MINUS.ordinal()] = id;
                                faces[BlockStep.Z_PLUS.ordinal()] = id;
                                faces[BlockStep.Z_MINUS.ordinal()] = id;
                            } else if(key.equals("topbottom")) {
                                faces[BlockStep.Y_MINUS.ordinal()] =
                                        faces[BlockStep.Y_PLUS.ordinal()] = parseTextureIndex(fileToIndex, srctxtid, value);
                            } else if(key.equals("blockcolor")) {
                                if(fileToIndex.containsKey(value))
                                    blockColorIdx = fileToIndex.get(value);
                                else
                                    Log.severe("Format error - line " + rnr.getLineNumber() + " of " + txtname + ": bad texture " + value);
                            } else if(key.startsWith("patch")) {
                                int patchId0, patchId1;
                                String idRange = key.substring(5);
                                String[] ids = idRange.split("-");
                                patchId0 = Integer.parseInt(ids[0]);
                                patchId1 = ids.length > 1
                                        ? Integer.parseInt(ids[1])
                                        : patchId0;
                                if((patchId0 < 0) || (patchId1 < patchId0)) {
                                    Log.severe("Texture mapping has invalid patch index - " + value + " - line " + rnr.getLineNumber() + " of " + txtname);
                                    return;
                                }
                                if(faces.length <= patchId1) {
                                    int[] newFaces = new int[patchId1+1];
                                    Arrays.fill(newFaces, TILEINDEX_BLANK);
                                    System.arraycopy(faces, 0, newFaces, 0, faces.length);
                                    faces = newFaces;
                                    int[] newtxtidx = new int[patchId1+1];
                                    Arrays.fill(newtxtidx, -1);
                                    System.arraycopy(txtidx, 0, newtxtidx, 0, txtidx.length);
                                    txtidx = newtxtidx;
                                }
                                int txtid = parseTextureIndex(fileToIndex, srctxtid, value);
                                for(int i = patchId0; i <= patchId1; i++) {
                                    faces[i] = txtid;
                                }
                            } else if(key.equals("transparency")) {
                                trans = BlockTransparency.valueOf(value);
                                if(trans == null) {
                                    trans = BlockTransparency.OPAQUE;
                                    Log.severe("Texture mapping has invalid transparency setting - " + value + " - line " + rnr.getLineNumber() + " of " + txtname);
                                }
                                /* For leaves, base on leaf transparency setting */
                                if(trans == BlockTransparency.LEAVES) {
                                    if(core.getLeafTransparency())
                                        trans = BlockTransparency.TRANSPARENT;
                                    else
                                        trans = BlockTransparency.OPAQUE;
                                }
                            } else if(key.equals("colorMult")) {
                                colorMult = (int)Long.parseLong(value, 16);
                            } else if(key.equals("custColorMult")) {
                                try {
                                    Class<?> cls = Class.forName(value);
                                    custColorMult = (CustomColorMultiplier)cls.newInstance();
                                } catch (Exception x) {
                                    Log.severe("Error loading custom color multiplier - " + value + ": " + x.getMessage());
                                }
                            } else if(key.equals("stdrot")) {
                                stdrot = value.equals("true");
                            }
                        }
                        byte[] layers = null;
                        for(String arg : args) {
                            String[] keyValue = arg.split("=");
                            if(keyValue.length < 2) continue;
                            if(keyValue[0].startsWith("layer")) {
                                if(layers == null) {
                                    layers = new byte[faces.length];
                                    Arrays.fill(layers, (byte)-1);
                                }
                                String[] v = keyValue[0].substring(5).split("-");
                                int id1 = Integer.parseInt(v[0]);
                                int id2 = Integer.parseInt(v[0]);
                                if(v.length > 1) {
                                    id2 = Integer.parseInt(v[1]);
                                }
                                byte val = (byte)Integer.parseInt(keyValue[1]);
                                for(; id1 <= id2; id1++) {
                                    layers[id1] = val;
                                }
                            }
                        }
                        /* If we have everything, build block */
                        if(blockIds.size() > 0) {
                            Integer colorIndex = (blockColorIdx >= 0)?(blockColorIdx + IMG_CNT):null;
                            HDBlockStateTextureMap map = new HDBlockStateTextureMap(faces, layers, colorMult, custColorMult, blockset, stdrot, colorIndex, trans);
                            map.addToTable(blockIds, stateids);
                            cnt[0]++;
                        }
                        else {
                            Log.severe("Texture mapping missing required parameters = line " + rnr.getLineNumber() + " of " + txtname);
                        }
                    }
                }
                else if(line.startsWith("copyblock:")) {
                    line = line.substring(line.indexOf(':')+1);
                    String[] args = line.split(",");
                    String srcname = null;
                    int srcmeta = 0;
                    BlockTransparency trans = null;
                    BitSet stateids = null;
                    List<String> ids = new ArrayList<>();
                    for(String a : args) {
                        String[] keyValue = a.split("=");
                        if(keyValue.length < 2) continue;
                        final String key = keyValue[0];
                        final String value = keyValue[1];
                        switch (key) {
                            case "id":
                                String id = getBlockName(modName[0], value);
                                if (id != null) {
                                    ids.add(id);
                                }
                                break;
                            case "data":
                                if (value.equals("*")) {
                                    stateids = null;    // Set all
                                } else {
                                    if (stateids == null) {
                                        stateids = new BitSet();
                                    }
                                    // See if range
                                    if (value.indexOf('-') >= 0) {
                                        String[] tok = value.split("-");
                                        int v1 = getIntValue(variableToValue, tok[0]);
                                        int v2 = getIntValue(variableToValue, tok[1]);
                                        for (int v = v1; v <= v2; v++) {
                                            stateids.set(v);
                                        }
                                    } else {
                                        stateids.set(getIntValue(variableToValue, value));
                                    }
                                }
                                break;
                            case "srcid":
                                srcname = getBlockName(modName[0], value);
                                break;
                            case "srcmeta":
                                srcmeta = getIntValue(variableToValue, value);
                                break;
                            case "transparency":
                                trans = BlockTransparency.valueOf(value);
                                if (trans == null) {
                                    trans = BlockTransparency.OPAQUE;
                                    Log.severe("Texture mapping has invalid transparency setting - " + value + " - line " + rnr.getLineNumber() + " of " + txtname);
                                }
                                /* For leaves, base on leaf transparency setting */
                                if (trans == BlockTransparency.LEAVES) {
                                    if (core.getLeafTransparency())
                                        trans = BlockTransparency.TRANSPARENT;
                                    else
                                        trans = BlockTransparency.OPAQUE;
                                }
                                break;
                        }
                    }
                    /* If we have everything, build block */
                    if((ids.size() > 0) && (srcname != null)) {
                        DynmapBlockState srcblk = DynmapBlockState.getStateByNameAndIndex(srcname, srcmeta);
                        HDBlockStateTextureMap map = null;
                        if (srcblk != null) map = HDBlockStateTextureMap.getByBlockState(srcblk);
                        if (map == null) {
                            Log.severe("Copy of texture mapping failed = line " + rnr.getLineNumber() + " of " + txtname);
                        }
                        else {
                            for (String id : ids) {
                                DynmapBlockState dynBlockState = DynmapBlockState.getBaseStateByName(id);
                                if (stateids == null) {
                                    for (int sid = 0; sid < dynBlockState.getStateCount(); sid++) {
                                        DynmapBlockState dynBS = dynBlockState.getState(sid);
                                        HDBlockStateTextureMap.copyToStateIndex(dynBS, map, trans);
                                    }
                                } else {
                                    for (int stateid = stateids.nextSetBit(0); stateid >= 0; stateid = stateids.nextSetBit(stateid+1)) {
                                        DynmapBlockState dynBS = dynBlockState.getState(stateid);
                                        HDBlockStateTextureMap.copyToStateIndex(dynBS, map, trans);
                                    }
                                }
                            }
                            cnt[0]++;
                        }
                    }
                    else {
                        Log.severe("Texture mapping copy missing required parameters = line " + rnr.getLineNumber() + " of " + txtname);
                    }
                }
                else if(line.startsWith("addtotexturemap:")) {
                    line = line.substring(line.indexOf(':') + 1);
                    String[] args = line.split(",");
                    String mapid = null;
                    int srctxtid = -1;
                    for(String arg : args) {
                        String[] keyValue = arg.split("=");
                        if (keyValue.length >= 2) {
                            String key = keyValue[0];
                            String value = keyValue[1];
                            if (key.equals("txtid")) {
                                if (fileToIndex.containsKey(value)) {
                                    srctxtid = fileToIndex.get(value);
                                } else {
                                    Log.severe("Format error - line " + rnr.getLineNumber() + " of " + txtname);
                                }
                            } else if (key.equals("mapid")) {
                                mapid = value;
                            }
                        }
                    }
                    if(mapid != null) {
                        for(String arg : args) {
                            String[] keyValue = arg.split("=");
                            if(keyValue.length < 2) continue;
                            String propName = keyValue[0];
                            String value = keyValue[1];
                            if(propName.startsWith("key:")) {
                                Integer key = getIntValue(variableToValue, propName.substring(4));
                                if ((key != null) && (key > 0)) {
                                    addTextureByKey(mapid, key, parseTextureIndex(fileToIndex, srctxtid, value));
                                }
                            }
                        }
                    } else {
                        Log.severe("Missing mapid  - line " + rnr.getLineNumber() + " of " + txtname);
                    }
                }
                else if(line.startsWith("texturemap:")) {
                    line = line.substring(line.indexOf(':') + 1);
                    BlockTransparency trans = BlockTransparency.OPAQUE;
                    int colorMult = 0;
                    CustomColorMultiplier custColorMult = null;
                    String[] args = line.split(",");
                    String mapid = null;
                    BitSet stateids = null;
                    List<String> blknames = new ArrayList<>();
                    for(String arg : args) {
                        String[] keyValue = arg.split("=");
                        if(keyValue.length < 2) continue;
                        String key = keyValue[0];
                        String value = keyValue[1];
                        switch (key) {
                            case "id":
                                String id = getBlockName(modName[0], value);
                                if (id != null) {
                                    blknames.add(id);
                                }
                                break;
                            case "mapid":
                                mapid = value;
                                break;
                            case "data":
                                if (value.equals("*")) {
                                    stateids = null;
                                } else {
                                    if (stateids == null) {
                                        stateids = new BitSet();
                                    }
                                    // See if range
                                    if (value.indexOf('-') >= 0) {
                                        String[] tok = value.split("-");
                                        int v1 = getIntValue(variableToValue, tok[0]);
                                        int v2 = getIntValue(variableToValue, tok[1]);
                                        for (int v = v1; v <= v2; v++) {
                                            stateids.set(v);
                                        }
                                    } else {
                                        stateids.set(getIntValue(variableToValue, value));
                                    }
                                }
                                break;
                            case "transparency":
                                trans = BlockTransparency.valueOf(value);
                                if (trans == null) {
                                    trans = BlockTransparency.OPAQUE;
                                    Log.severe("Texture mapping has invalid transparency setting - " + value + " - line " + rnr.getLineNumber() + " of " + txtname);
                                }
                                /* For leaves, base on leaf transparency setting */
                                if (trans == BlockTransparency.LEAVES) {
                                    if (core.getLeafTransparency())
                                        trans = BlockTransparency.TRANSPARENT;
                                    else
                                        trans = BlockTransparency.OPAQUE;
                                }
                                break;
                            case "colorMult":
                                colorMult = Integer.valueOf(value, 16);
                                break;
                            case "custColorMult":
                                try {
                                    Class<?> cls = Class.forName(value);
                                    custColorMult = (CustomColorMultiplier) cls.newInstance();
                                } catch (Exception x) {
                                    Log.severe("Error loading custom color multiplier - " + value + ": " + x.getMessage());
                                }
                                break;
                        }
                    }
                    /* If we have everything, build texture map */
                    if((blknames.size() > 0) && (mapid != null)) {
                        addTextureIndex(mapid, blknames, stateids, trans, colorMult, custColorMult, blockset);
                    }
                    else {
                        Log.severe("Texture map missing required parameters = line " + rnr.getLineNumber() + " of " + txtname);
                    }
                }
                else if(line.startsWith("texturefile:") || line.startsWith("texture:")) {
                    boolean istxt = line.startsWith("texture:");
                    line = line.substring(line.indexOf(':')+1);
                    String[] args = line.split(",");
                    int xdim = 16, ydim = 16;
                    TileFileFormat fmt = TileFileFormat.GRID;
                    if(istxt) {
                        xdim = ydim = 1;
                        fmt = TileFileFormat.GRID;
                    }
                    MaterialType mt = null;
                    String id = null;
                    String fname = null;
                    for(String arg : args) {
                        String[] keyValue = arg.split("=");
                        if(keyValue.length < 2) continue;
                        final String key = keyValue[0];
                        final String value = keyValue[1];
                        switch (key) {
                            case "id":
                                id = value;
                                if (fname == null) {
                                    if (texturePath[0] != null) {
                                        fname = texturePath[0] + id + ".png";
                                    } else if (textureMod[0] != null) {
                                        fname = "mods/" + textureMod[0] + "/textures/blocks/" + id + ".png";
                                    }
                                }
                                break;
                            case "filename":
                                fname = value;
                                break;
                            case "xcount":
                                xdim = Integer.parseInt(value);
                                break;
                            case "ycount":
                                ydim = Integer.parseInt(value);
                                break;
                            case "format":
                                fmt = TileFileFormat.valueOf(value.toUpperCase());
                                if (fmt == null) {
                                    Log.severe("Invalid format type " + value + " - line " + rnr.getLineNumber() + " of " + txtname);
                                    return;
                                }
                                break;
                            case "material":
                                mt = MaterialType.valueOf(value);
                                if (mt == null) {
                                    Log.warning("Bad custom material type: " + value);
                                }
                                break;
                        }
                    }
                    if((fname != null) && (id != null)) {
                        /* Register the file */
                        int fid = findOrAddDynamicTileFile(fname, modName[0], xdim, ydim, fmt, args);
                        fileToIndex.put(id, fid); /* Save lookup */
                        if (mt != null) {
                            addonfiles.get(fid).material = mt;
                        }
                    }
                    else {
                        Log.severe("Format error - line " + rnr.getLineNumber() + " of " + txtname);
                        return;
                    }
                } else if(line.startsWith("#") || line.startsWith(";")) {
                } else if(line.startsWith("enabled:")) {  /* Test if texture file is enabled */
                    line = line.substring(8).trim();
                    if(line.startsWith("true")) {   /* We're enabled? */
                        /* Nothing to do - keep processing */
                    } else if(line.startsWith("false")) { /* Disabled */
                        return; /* Quit */
                    }
                    /* If setting is not defined or false, quit */
                    else if(!config.getBoolean(line, false)) {
                        return;
                    }
                    else {
                        Log.info(line + " textures enabled");
                    }
                } else if(line.startsWith("var:")) {  /* Test if variable declaration */
                    line = line.substring(4).trim();
                    String[] args = line.split(",");
                    for (String arg : args) {
                        String[] v = arg.split("=");
                        if (v.length < 2) {
                            Log.severe("Format error - line " + rnr.getLineNumber() + " of " + txtname);
                            return;
                        }
                        try {
                            int val = Integer.parseInt(v[1]);    /* Parse default value */
                            int parmval = config.getInteger(v[0], val); /* Read value, with applied default */
                            variableToValue.put(v[0], parmval); /* And save value */
                        } catch (NumberFormatException nfx) {
                            Log.severe("Format error - line " + rnr.getLineNumber() + " of " + txtname + ": " + nfx.getMessage());
                            return;
                        }
                    }
                } else if(line.startsWith("cfgfile:")) { /* If config file */
                    if (!modConfigLoaded[0]) {
                        modConfigNeeded[0] = true;
                    }
                    File cfgfile = new File(line.substring(8).trim());
                    ForgeConfigFile cfg = new ForgeConfigFile(cfgfile);
                    if(cfg.load()) {
                        cfg.addBlockIDs(variableToValue);
                        modConfigNeeded[0] = false;
                        modConfigLoaded[0] = true;
                    }
                } else if(line.startsWith("modname:")) {
                    String[] names = line.substring(8).split(",");
                    boolean found = false;
                    for(String n : names) {
                        String[] ntok = n.split("[\\[\\]]");
                        String rng = null;
                        if (ntok.length > 1) {
                            n = ntok[0].trim();
                            rng = ntok[1].trim();
                        }
                        n = n.trim();
                        // If already supplied by mod, quit processing this file
                        if (loadedmods.contains(n)) {
                            return;
                        }
                        String modver = core.getServer().getModVersion(n);
                        if((modver != null) && ((rng == null) || HDBlockModels.checkVersionRange(modver, rng))) {
                            found = true;
                            Log.info(n + "[" + modver + "] textures enabled");
                            modName[0] = n;
                            modVersion[0] = modver;
                            if(textureMod[0] == null) textureMod[0] = modName[0];
                            loadedmods.add(n);
                            // Prime values from block and item unique IDs
                            core.addModBlockItemIDs(modName[0], variableToValue);
                            break;
                        }
                    }
                    if(!found) return;
                } else if(line.startsWith("texturemod:")) {
                    textureMod[0] = line.substring(line.indexOf(':')+1).trim();
                } else if(line.startsWith("texturepath:")) {
                    texturePath[0] = line.substring(line.indexOf(':')+1).trim();
                    if (texturePath[0].charAt(texturePath[0].length()-1) != '/') {
                        texturePath[0] += "/";
                    }
                } else if(line.startsWith("biome:")) {
                    line = line.substring(6).trim();
                    String[] args = line.split(",");
                    int id = 0;
                    int grasscolormult = -1;
                    int foliagecolormult = -1;
                    int watercolormult = -1;
                    double rain = -1.0;
                    double tmp = -1.0;
                    for (String arg : args) {
                        String[] equalSplit = arg.split("=");
                        if (equalSplit.length < 2) {
                            Log.severe("Format error - line " + rnr.getLineNumber() + " of " + txtname);
                            return;
                        }

                        final String key = equalSplit[0];
                        final String value = equalSplit[1];
                        switch (key) {
                            case "id":
                                id = getIntValue(variableToValue, value);
                                break;
                            case "grassColorMult":
                                grasscolormult = Integer.valueOf(value, 16);
                                break;
                            case "foliageColorMult":
                                foliagecolormult = Integer.valueOf(value, 16);
                                break;
                            case "waterColorMult":
                                watercolormult = Integer.valueOf(value, 16);
                                break;
                            case "temp":
                                tmp = Double.parseDouble(value);
                                break;
                            case "rain":
                                rain = Double.parseDouble(value);
                                break;
                        }
                    }
                    if(id > 0) {
                        BiomeMap b = BiomeMap.byBiomeID(id); /* Find biome */
                        if(b == null) {
                            Log.severe("Format error - line " + rnr.getLineNumber() + " of " + txtname + ": " + id);
                        } else {
                            if(foliagecolormult != -1)
                                b.setFoliageColorMultiplier(foliagecolormult);
                            if(grasscolormult != -1)
                                b.setGrassColorMultiplier(grasscolormult);
                            if(watercolormult != -1)
                                b.setWaterColorMultiplier(watercolormult);
                            if(tmp != -1.0)
                                b.setTemperature(tmp);
                            if(rain != -1.0)
                                b.setRainfall(rain);
                        }
                    }
                } else if(line.startsWith("version:")) {
                    line = line.substring(line.indexOf(':')+1);
                    if (!HDBlockModels.checkVersionRange(version, line)) {
                        return;
                    }
                } else if(line.startsWith("noterrainpng:")) {
                    line = line.substring(line.indexOf(':')+1);
                    terrain_ok[0] = !line.startsWith("true");
                }
            });
            if(modConfigNeeded[0]) {
                Log.severe("Error loading configuration file for " + modName[0]);
            }

            Log.verboseinfo("Loaded " + cnt[0] + " texture mappings from " + txtname);
        } catch (IOException iox) {
            Log.severe("Error reading " + txtname + " - " + iox.toString());
        } catch (NumberFormatException nfx) {
            // TODO: line
            Log.severe("Format error - line 0 of " + txtname + ": " + nfx.getMessage());
        }

    }

    /* Process any block aliases */
    public static void handleBlockAlias() {
        MapManager.mapman
                .getAliasedBlocks()
                .forEach(an -> {
                    String newId = MapManager.mapman.getBlockAlias(an);
                    if (!newId.equals(an)) {
                        HDBlockStateTextureMap.remapTexture(an, newId);
                    }
                });
    }

    /**
     * Read color for given subblock coordinate, with given block id and data and face
     * @param ps - perspective state
     * @param mapiter - map iterator
     * @param rslt - color result (returned with value)
     * @param blk - block state
     * @param lastblocktype - last block ID
     * @param ss - shader state
     */
    public final void readColor(final HDPerspectiveState ps, final MapIterator mapiter, final Color rslt, final DynmapBlockState blk, final DynmapBlockState lastblocktype,
            final TexturePackHDShader.ShaderState ss) {
        HDBlockStateTextureMap map = HDBlockStateTextureMap.getByBlockState(blk);
        BlockStep lastStep = ps.getLastBlockStep();
        int patchId = ps.getTextureIndex();   /* See if patch index */
        int faceIndex = patchId >= 0 ? patchId : lastStep.ordinal();
        int textid = map.faces[faceIndex];
        if (ctm != null) {
            int mod = 0;
            if(textid >= COLORMOD_MULT_INTERNAL) {
                mod = (textid / COLORMOD_MULT_INTERNAL) * COLORMOD_MULT_INTERNAL;
                textid -= mod;
            }
            textid = mod + ctm.mapTexture(mapiter, blk, lastStep, textid, ss);
        }
        readColor(ps, mapiter, rslt, blk, lastblocktype, ss, map, lastStep, patchId, textid, map.stdrotate);
        if(map.layers != null) {    /* If layered */
            /* While transparent and more layers */
            while(rslt.isTransparent() && (map.layers[faceIndex] >= 0)) {
                faceIndex = map.layers[faceIndex];
                textid = map.faces[faceIndex];
                readColor(ps, mapiter, rslt, blk, lastblocktype, ss, map, lastStep, patchId, textid, map.stdrotate);
            }
        }
    }
    
    /**
     * Read color for given subblock coordinate, with given block id and data and face
     */
    private void readColor(final HDPerspectiveState ps, final MapIterator mapIterator, /* Destination */final Color result, final DynmapBlockState blk, final DynmapBlockState lastblocktype,
                           final TexturePackHDShader.ShaderState ss, HDBlockStateTextureMap map, BlockStep laststep, int patchid, int textid, boolean stdrot) {
        if(textid < 0) {
            result.setTransparent();
            return;
        }
        boolean hasCustomColoring = ss.do_biome_shading && this.blockColoring.hasBlkStateValue(blk);
        // Test if we have no texture modifications
        boolean simpleMap = (textid < COLORMOD_MULT_INTERNAL) && (!hasCustomColoring);
        int[] xyz = null;
        
        if (simpleMap) {    /* If simple mapping */
            int[] texture = getTileARGB(textid);
            /* Get texture coordinates (U=horizontal(left=0),V=vertical(top=0)) */
            int u = 0, v = 0;
            /* If not patch, compute U and V */
            if(patchid < 0) {
                xyz = ps.getSubblockCoord();

                switch(laststep) {
                    case X_MINUS: /* South face: U = East (Z-), V = Down (Y-) */
                        u = nativeScale -xyz[2]-1;
                        v = nativeScale -xyz[1]-1;
                        break;
                    case X_PLUS:    /* North face: U = West (Z+), V = Down (Y-) */
                        u = xyz[2];
                        v = nativeScale -xyz[1]-1;
                        break;
                    case Z_MINUS:   /* West face: U = South (X+), V = Down (Y-) */
                        u = xyz[0];
                        v = nativeScale -xyz[1]-1;
                        break;
                    case Z_PLUS:    /* East face: U = North (X-), V = Down (Y-) */
                        u = nativeScale -xyz[0]-1;
                        v = nativeScale -xyz[1]-1;
                        break;
                    case Y_MINUS:   /* U = East(Z-), V = South(X+) */
                        if(stdrot) {
                            u = xyz[0];
                            v = xyz[2];
                        } else {
                            u = nativeScale -xyz[2]-1;
                            v = xyz[0];
                        }
                        break;
                    case Y_PLUS:
                        if(stdrot) {
                            u = nativeScale -xyz[0]-1;
                            v = xyz[2];
                        } else {
                            u = xyz[2];
                            v = xyz[0];
                        }
                        break;
                }
            }
            else {
                u = fastFloor(ps.getPatchU() * nativeScale);
                v = nativeScale - fastFloor(ps.getPatchV() * nativeScale) - 1;
            }
            /* Read color from texture */
            try {
                result.setARGB(texture[v* nativeScale + u]);
            } catch(ArrayIndexOutOfBoundsException aoobx) {
                u = ((u < 0) ? 0 : ((u >= nativeScale) ? (nativeScale -1) : u));
                v = ((v < 0) ? 0 : ((v >= nativeScale) ? (nativeScale -1) : v));
                try {
                    result.setARGB(texture[v* nativeScale + u]);
                } catch(ArrayIndexOutOfBoundsException oob2) { }
            }
            
            return;            
        }
        
        /* See if not basic block texture */
        int textop = textid / COLORMOD_MULT_INTERNAL;
        textid = textid % COLORMOD_MULT_INTERNAL;
        
        /* If clear-inside op, get out early */
        if((textop == COLORMOD_CLEARINSIDE) || (textop == COLORMOD_MULTTONED_CLEARINSIDE)) {
        	DynmapBlockState lasthit = ss.getLastBlockHit(); // Last surface hit, vs last visited
            /* Check if previous block is same block type as we are: surface is transparent if it is */
            if (blk.matchingBaseState(lasthit) || ((blk.isWaterFilled() && lasthit.isWaterFilled()) && ps.isOnFace())) {
                result.setTransparent();
                return;
            }
            /* If water block, to watercolor tone op */
            if (blk.isWater()) {
                textop = COLORMOD_WATERTONED;
            }
            else if(textop == COLORMOD_MULTTONED_CLEARINSIDE) {
                textop = COLORMOD_MULTTONED;
            }
        }

        int[] texture = getTileARGB(textid);
        /* Get texture coordinates (U=horizontal(left=0),V=vertical(top=0)) */
        int u = 0, v = 0;

        if(patchid < 0) {
            if (xyz == null) xyz = ps.getSubblockCoord();
            switch(laststep) {
                case X_MINUS: /* South face: U = East (Z-), V = Down (Y-) */
                    u = nativeScale -xyz[2]-1;
                    v = nativeScale -xyz[1]-1;
                    break;
                case X_PLUS:    /* North face: U = West (Z+), V = Down (Y-) */
                    u = xyz[2];
                    v = nativeScale -xyz[1]-1;
                    break;
                case Z_MINUS:   /* West face: U = South (X+), V = Down (Y-) */
                    u = xyz[0];
                    v = nativeScale -xyz[1]-1;
                    break;
                case Z_PLUS:    /* East face: U = North (X-), V = Down (Y-) */
                    u = nativeScale -xyz[0]-1;
                    v = nativeScale -xyz[1]-1;
                    break;
                case Y_MINUS:   /* U = East(Z-), V = South(X+) */
                    if(stdrot) {
                        u = xyz[0];
                        v = xyz[2];
                    } else {
                        u = nativeScale -xyz[2]-1;
                        v = xyz[0];
                    }
                    break;
                case Y_PLUS:
                    if(stdrot) {
                        u = nativeScale -xyz[0]-1;
                        v = xyz[2];
                    } else {
                        u = xyz[2];
                        v = xyz[0];
                    }
                    break;
            }
        }
        else {
            u = fastFloor(ps.getPatchU() * nativeScale);
            v = nativeScale - fastFloor(ps.getPatchV() * nativeScale) - 1;
        }
        /* Handle U-V transorms before fetching color */
        switch(textop) {
            case COLORMOD_ROT90: {
                int tmp = u;
                u = nativeScale - v - 1;
                v = tmp;
                break;
            }
            case COLORMOD_ROT180:
                u = nativeScale - u - 1; v = nativeScale - v - 1;
                break;
            case COLORMOD_ROT270:
            case COLORMOD_GRASSTONED270:
            case COLORMOD_FOLIAGETONED270:
            case COLORMOD_WATERTONED270: {
                int tmp = u;
                u = v;
                v = nativeScale - tmp - 1;
                break;
            }
            case COLORMOD_FLIPHORIZ:
                u = nativeScale - u - 1;
                break;
            case COLORMOD_SHIFTDOWNHALF:
                if(v < nativeScale /2) {
                    result.setTransparent();
                    return;
                }
                v -= nativeScale /2;
                break;
            case COLORMOD_SHIFTDOWNHALFANDFLIPHORIZ:
                if(v < nativeScale /2) {
                    result.setTransparent();
                    return;
                }
                v -= nativeScale /2;
                u = nativeScale - u - 1;
                break;
            case COLORMOD_INCLINEDTORCH:
                if(v >= (3* nativeScale /4)) {
                    result.setTransparent();
                    return;
                }
                v += nativeScale /4;
                if(u < nativeScale /2) u = nativeScale /2-1;
                if(u > nativeScale /2) u = nativeScale /2;
                break;
            case COLORMOD_GRASSSIDE:
                boolean doGrassSide = false;
                boolean doSnowSide = false;
                if(ss.do_better_grass) {
                    mapIterator.unstepPosition(laststep);
                    if (mapIterator.getBlockType().isSnow())
                        doSnowSide = true;
                    if (mapIterator.getBlockTypeAt(BlockStep.Y_MINUS).isGrass())
                        doGrassSide = true;
                    mapIterator.stepPosition(laststep);
                }
                
                /* Check if snow above block */
                if(mapIterator.getBlockTypeAt(BlockStep.Y_PLUS).isSnow()) {
                    if(doSnowSide) {
                        texture = getTileARGB(TILEINDEX_SNOW); /* Snow full side block */
                    } else {
                        texture = getTileARGB(TILEINDEX_SNOWSIDE); /* Snow block */
                    }
                    textop = 0;
                } else {  /* Else, check the grass color overlay */
                    if(doGrassSide) {
                        texture = getTileARGB(TILEINDEX_GRASS); /* Grass block */
                        textop = COLORMOD_GRASSTONED;   /* Force grass toning */
                    } else {
                        int alphaValue = getTileARGB(TILEINDEX_GRASSMASK)[v* nativeScale +u];
                        if((alphaValue & 0xFF000000) != 0) { /* Hit? */
                            texture = getTileARGB(TILEINDEX_GRASSMASK); /* Use it */
                            textop = COLORMOD_GRASSTONED;   /* Force grass toning */
                        }
                    }
                }
                break;
            case COLORMOD_LILYTONED:
                /* Rotate texture based on lily orientation function (from renderBlockLilyPad in RenderBlocks.java in MCP) */
                long l1 = (mapIterator.getX() * 0x2fc20f) ^ (long)mapIterator.getZ() * 0x6ebfff5L ^ mapIterator.getY();
                l1 = l1 * l1 * 0x285b825L + l1 * 11L;
                int orientation = (int)(l1 >> 16 & 3L);
                switch(orientation) {
                    case 0: {
                        int tmp = u;
                        u = nativeScale - v - 1;
                        v = tmp;
                        break;
                    }
                    case 1:
                        u = nativeScale - u - 1; v = nativeScale - v - 1;
                        break;
                    case 2: {
                        int tmp = u;
                        u = v;
                        v = nativeScale - tmp - 1;
                        break;
                    }
                    case 3:
                        break;
                }
                break;
        }
        /* Read color from texture */
        try {
            result.setARGB(texture[v* nativeScale + u]);
        } catch (ArrayIndexOutOfBoundsException aioobx) {
            result.setARGB(0);
        }

        int customColorMult = -1;
        // If block has custom coloring
        if (hasCustomColoring) {
            Integer idx = this.blockColoring.getBlkStateValue(blk);
            LoadedImage img = imgs[idx];
            if (img.argb != null) {
                customColorMult = mapIterator.getSmoothWaterColorMultiplier(img.argb);
            }
            else {
                hasCustomColoring = false;
            }
        }
        int colorAlpha = 0xFF000000;
        int colorMult = -1;
        if (!hasCustomColoring) {
            // Switch based on texture modifier
            switch(textop) {
                case COLORMOD_GRASSTONED:
                case COLORMOD_GRASSTONED270:
                    if(ss.do_biome_shading) {
                        if(imgs[IMG_SWAMPGRASSCOLOR] != null)
                            colorMult = mapIterator.getSmoothColorMultiplier(imgs[IMG_GRASSCOLOR].argb, imgs[IMG_SWAMPGRASSCOLOR].argb);
                        else
                            colorMult = mapIterator.getSmoothGrassColorMultiplier(imgs[IMG_GRASSCOLOR].argb);
                    }
                    else {
                        colorMult = imgs[IMG_GRASSCOLOR].trivial_color;
                    }
                    break;
                case COLORMOD_FOLIAGETONED:
                case COLORMOD_FOLIAGETONED270:
                    if(ss.do_biome_shading) {
                        if(imgs[IMG_SWAMPFOLIAGECOLOR] != null)
                            colorMult = mapIterator.getSmoothColorMultiplier(imgs[IMG_FOLIAGECOLOR].argb, imgs[IMG_SWAMPFOLIAGECOLOR].argb);
                        else
                            colorMult = mapIterator.getSmoothFoliageColorMultiplier(imgs[IMG_FOLIAGECOLOR].argb);
                    }
                    else {
                        colorMult = imgs[IMG_FOLIAGECOLOR].trivial_color;
                    }
                    break;
                case COLORMOD_FOLIAGEMULTTONED:
                    if(ss.do_biome_shading) {
                        if(imgs[IMG_SWAMPFOLIAGECOLOR] != null)
                            colorMult = mapIterator.getSmoothColorMultiplier(imgs[IMG_FOLIAGECOLOR].argb, imgs[IMG_SWAMPFOLIAGECOLOR].argb);
                        else
                            colorMult = mapIterator.getSmoothFoliageColorMultiplier(imgs[IMG_FOLIAGECOLOR].argb);
                    }
                    else {
                        colorMult = imgs[IMG_FOLIAGECOLOR].trivial_color;
                    }
                    if(map.custColorMult != null) {
                        colorMult = ((colorMult & 0xFEFEFE) + map.custColorMult.getColorMultiplier(mapIterator)) / 2;
                    }
                    else {
                        colorMult = ((colorMult & 0xFEFEFE) + map.colorMult) / 2;
                    }
                    break;

                case COLORMOD_WATERTONED:
                case COLORMOD_WATERTONED270:
                    if(imgs[IMG_WATERCOLORX] != null) {
                        if(ss.do_biome_shading) {
                            colorMult = mapIterator.getSmoothWaterColorMultiplier(imgs[IMG_WATERCOLORX].argb);
                        }
                        else {
                            colorMult = imgs[IMG_WATERCOLORX].trivial_color;
                        }
                    }
                    else {
                        if(ss.do_biome_shading) {
                            colorMult = mapIterator.getSmoothWaterColorMultiplier();
                        }
                    }
                    break;
                case COLORMOD_BIRCHTONED:
                    if(ss.do_biome_shading) {
                        if(imgs[IMG_BIRCHCOLOR] != null)
                            colorMult = mapIterator.getSmoothFoliageColorMultiplier(imgs[IMG_BIRCHCOLOR].argb);
                        else
                            colorMult = colorMultBirch;
                    }
                    else {
                        colorMult = colorMultBirch;
                    }
                    break;
                case COLORMOD_PINETONED:
                    if(ss.do_biome_shading) {
                        if(imgs[IMG_PINECOLOR] != null)
                            colorMult = mapIterator.getSmoothFoliageColorMultiplier(imgs[IMG_PINECOLOR].argb);
                        else
                            colorMult = colorMultPine;
                    }
                    else {
                        colorMult = colorMultPine;
                    }
                    break;
                case COLORMOD_LILYTONED:
                    colorMult = colorMultLily;
                    break;
                case COLORMOD_MULTTONED:    /* Use color multiplier */
                    if(map.custColorMult != null) {
                        colorMult = map.custColorMult.getColorMultiplier(mapIterator);
                    }
                    else {
                        colorMult = map.colorMult;
                    }
                    if((colorMult & 0xFF000000) != 0) {
                        colorAlpha = colorMult & 0xFF000000;
                    }
                    break;
            }
        }
        
        if((colorMult != -1) && (colorMult != 0)) {
            result.blendColor(colorMult | colorAlpha);
        }
        if (hasCustomColoring && (customColorMult != -1)) {
            result.blendColor(customColorMult | colorAlpha);
        }
    }
    
    private static void makeAlphaPure(int[] argb) {
        IntStream.range(0, argb.length)
                .filter(i -> (argb[i] & 0xFF000000) != 0)
                .forEachOrdered(i -> argb[i] |= 0xFF000000);
    }

    private static int fastFloor(double f) {
        return ((int)(f + 1000000000.0)) - 1000000000;
    }

    /**
     * Get tile index, based on tile file name and relative index within tile file
     * @param fname - filename
     * @param idx - tile index (= (y * xdim) + x)
     * @return global tile index, or -1 if not found
     */
    public static int findDynamicTile(String fname, int idx) {
        /* Find existing, if already there */
        DynamicTileFile f = addonFilesByName.get(fname);
        if (f != null) {
            if ((idx >= 0) && (idx < f.tile_to_dyntile.length) && (f.tile_to_dyntile[idx] >= 0)) {
                f.used = true;
                return f.tile_to_dyntile[idx];
            }
        }
        return -1;
    }
    /**
     * Add new dynmaic file definition, or return existing
     * 
     * @param fileName - filename
     * @param modName - mod name
     * @param xdim - x dimension
     * @param ydim - y dimension
     * @param fmt - tile file format
     * @param args - args for file format
     * @return dynamic file index
     */
    public static int findOrAddDynamicTileFile(String fileName, String modName, int xdim, int ydim, TileFileFormat fmt, String[] args) {
        /* Find existing, if already there */
        DynamicTileFile f = addonFilesByName.get(fileName);
        if (f != null) {
            return f.idx;
        }
        /* Add new tile file entry */
        DynamicTileFile dynamicTileFile = new DynamicTileFile();
        dynamicTileFile.filename = fileName;
        dynamicTileFile.modname = modName;
        dynamicTileFile.tilecnt_x = xdim;
        dynamicTileFile.tilecnt_y = ydim;
        dynamicTileFile.format = fmt;
        // Assume all biome files are used (not referred to by index)
        dynamicTileFile.used = fmt == TileFileFormat.BIOME;
        switch(fmt) {
            case GRID:
                dynamicTileFile.tile_to_dyntile = new int[xdim*ydim];
                break;
            case CHEST:
                dynamicTileFile.tile_to_dyntile = new int[TILEINDEX_CHEST_COUNT]; /* 6 images for chest tile */
                break;
            case BIGCHEST:
                dynamicTileFile.tile_to_dyntile = new int[TILEINDEX_BIGCHEST_COUNT]; /* 10 images for chest tile */
                break;
            case SIGN:
                dynamicTileFile.tile_to_dyntile = new int[TILEINDEX_SIGN_COUNT]; /* 10 images for sign tile */
                break;
            case SHULKER:
                dynamicTileFile.tile_to_dyntile = new int[TILEINDEX_SHULKER_COUNT]; /* 6 images for sign tile */
                break;
            case BED:
                dynamicTileFile.tile_to_dyntile = new int[TILEINDEX_BED_COUNT]; /* 18 images for tile */
                break;
            case CUSTOM:
                {
                    List<CustomTileRec> recs = new ArrayList<>();
                    for(String a : args) {
                        String[] v = a.split("=");
                        if(v.length != 2) continue;
                        if(v[0].startsWith("tile")) {
                            int id;
                            try {
                                id = Integer.parseInt(v[0].substring(4));
                            } catch (NumberFormatException nfx) {
                                Log.warning("Bad tile ID: " + v[0]);
                                continue;
                            }
                            while(recs.size() <= id) {
                                recs.add(null);
                            }
                            CustomTileRec rec = new CustomTileRec();
                            try {
                                String[] coords = v[1].split("/");
                                String[] topleft = coords[0].split(":");
                                rec.srcx = Integer.parseInt(topleft[0]);
                                rec.srcy = Integer.parseInt(topleft[1]);
                                String[] size = coords[1].split(":");
                                rec.width = Integer.parseInt(size[0]);
                                rec.height = Integer.parseInt(size[1]);
                                if(coords.length >= 3) {
                                    String[] dest = coords[2].split(":");
                                    rec.targetx = Integer.parseInt(dest[0]);
                                    rec.targety = Integer.parseInt(dest[1]);
                                }
                                recs.set(id,  rec);
                            } catch (Exception x) {
                                Log.warning("Bad custom tile coordinate: " + v[1]);
                            }
                        }
                    }
                    dynamicTileFile.tile_to_dyntile = new int[recs.size()];
                    dynamicTileFile.cust = recs;
                }
                break;
            case SKIN:
                dynamicTileFile.tile_to_dyntile = new int[TILEINDEX_SKIN_COUNT]; /* 6 images for skin tile */
                break;
            case TILESET:
                dynamicTileFile.tile_to_dyntile = new int[xdim*ydim];
                break;
            case BIOME:
                dynamicTileFile.tile_to_dyntile = new int[1];
                break;
            default:
                dynamicTileFile.tile_to_dyntile = new int[xdim*ydim];
                break;
        }
        Arrays.fill(dynamicTileFile.tile_to_dyntile,  -1);
        dynamicTileFile.idx = addonfiles.size();
        addonfiles.add(dynamicTileFile);
        addonFilesByName.put(dynamicTileFile.filename, dynamicTileFile);
        return dynamicTileFile.idx;
    }
    /**
     * Add or find dynamic tile index of given dynamic tile
     * @param dynfile_idx - index of file
     * @param tile_id - ID of tile within file
     * @return global tile ID
     */
    public static int findOrAddDynamicTile(int dynfile_idx, int tile_id) {
        DynamicTileFile f = addonfiles.get(dynfile_idx);
        if(f == null) {
            throw new NumberFormatException("Invalid add-on file index: " + dynfile_idx);
        }
        if (tile_id >= f.tile_to_dyntile.length) {
            throw new NumberFormatException("Invalid index " + tile_id + " for texture file " + f.filename + " on mod " + f.modname);
        }
        if(f.tile_to_dyntile[tile_id] < 0) {   /* Not assigned yet? */
            f.tile_to_dyntile[tile_id] = next_dynamic_tile;
            next_dynamic_tile++;    /* Allocate next ID */
        }
        f.used = true;  // Mark file as being used
        return f.tile_to_dyntile[tile_id];
    }

    private static final int[] smoothWaterMult = new int[10];
    
    public static int getTextureIDAt(MapIterator mapiter, DynmapBlockState blk, BlockStep face) {
        HDBlockStateTextureMap map = HDBlockStateTextureMap.getByBlockState(blk);
        int idx = -1;
        if (map != null) {
            int sideOrd = face.ordinal();
            if (map.faces != null) {
                idx = sideOrd < map.faces.length ? map.faces[sideOrd] : map.faces[0];
            }
        }
        if(idx > 0)
            idx = idx % COLORMOD_MULT_INTERNAL;
        return idx;
    }
    
    private static final String PALETTE_BLOCK_KEY = "palette.block.";

    private void processCustomColorMap(String fname, String ids) {
        // Register file name
        int idx = findOrAddDynamicTileFile(fname, null, 1, 1, TileFileFormat.BIOME, new String[0]);
        if(idx < 0) {
            Log.info("Error registering custom color file: " + fname);
            return;
        }
        Integer index = idx + IMG_CNT;
        // Now, parse block ID list
        for (String id : ids.split("\\s+")) {
            String[] tok = id.split(":");
            int meta = -1;
            DynmapBlockState state = null;
            String name;
            if (tok.length == 1) {  /* Only ID */
                name = tok[0];
                state = DynmapBlockState.getBaseStateByName(name);
                if (state.isAir()) {
                    Log.info("Bad custom color block ID: " + tok[0]);
                }
            } else if (tok.length == 2) { /* ID : meta */
                name = tok[0];
                try {
                    meta = Integer.parseInt(tok[1]);
                } catch (NumberFormatException nfx) {
                    Log.info("Bad custom color meta ID: " + tok[1]);
                }
                state = DynmapBlockState.getStateByNameAndIndex(name, meta);
                if (state.isAir()) {
                    Log.info("Bad custom color block ID: " + tok[0] + ":" + meta);
                }
            }

            /* Add mappings for values */
            HDBlockStateTextureMap texMap = HDBlockStateTextureMap.getByBlockState(state);
            if (texMap != null) {
                if (meta >= 0) {
                    if (state.isNotAir()) {
                        this.blockColoring.setBlkStateValue(state, index);
                    }
                } else if (meta == -1) {  /* All meta IDs */
                    for (int v = 0; v < state.getStateCount(); v++) {
                        DynmapBlockState b = state.getState(v);
                        if (b.isNotAir()) {
                            this.blockColoring.setBlkStateValue(b, index);
                        }
                    }
                }
            }
        }
    }
    private void processCustomColors(Properties p) {
        // Loop through keys
        for(String pname : p.stringPropertyNames()) {
            if(!pname.startsWith(PALETTE_BLOCK_KEY))
                continue;
            String v = p.getProperty(pname);
            String fname = pname.substring(PALETTE_BLOCK_KEY.length()).trim(); // Get filename of color map
            if(fname.charAt(0) == '/') fname = fname.substring(1); // Strip leading /
            if(fname.charAt(0) == '~') fname = "assets/minecraft/mcpatcher" + fname.substring(1);
            processCustomColorMap(fname, v);
        }
    }
    
    static {
        /*
         * Generate smoothed swamp multipliers (indexed by swamp biome count)
         */
        Color c = new Color();
        for(int i = 0; i < 10; i++) {
            /* Use water color multiplier base for 1.1 (E0FFAE) */
            int r = (((9-i) * 0xFF) + (i * 0xE0)) / 9;
            int g = 0xFF;
            int b = (((9-i) * 0xFF) + (i * 0xAE)) / 9;
            c.setRGBA(r & 0xFE, g & 0xFE, b & 0xFE, 0xFF);
            smoothWaterMult[i] = c.getARGB();
        }
    }
    
    public int getTrivialFoliageMultiplier() {
        return imgs[IMG_FOLIAGECOLOR].argb[BiomeMap.FOREST.biomeLookup()];
    }
    public int getTrivialGrassMultiplier() {
        return imgs[IMG_GRASSCOLOR].argb[BiomeMap.FOREST.biomeLookup()];
    }
    public int getTrivialWaterMultiplier() {
        return imgs[IMG_WATERCOLORX] != null 
                ? imgs[IMG_WATERCOLORX].argb[BiomeMap.FOREST.biomeLookup()] 
                : 0xFFFFFF;
    }
    public int getCustomBlockMultiplier(DynmapBlockState blk) {
        Integer idx = this.blockColoring.getBlkStateValue(blk);
        if (idx != null) {
            LoadedImage img = imgs[idx];
            if (img.argb != null) {
                return img.argb[BiomeMap.FOREST.biomeLookup()];
            }
        }
        return 0xFFFFFF;
    }

    private static class TextureSpec {
        public String fileName;
        public Color diffuseColor;
        public String fileNameWithAlpha;
        public MaterialType material;
    }

    private static class ExportPackSpec {
        final Map<String, TextureSpec> txtids = new HashMap<>();
        DynmapBufferedImage img;
        OBJExport exp;
        String name;
    }
    
    // Encode image as PNG and add to ZIP
    private void addImageToZip(String idstr, int idx, int colorMult, ExportPackSpec etp) throws IOException {
        if (etp.txtids.containsKey(idstr)) {   // Already in set?
            return;
        }
        colorMult = colorMult & 0xFFFFFF;   // Mask multiplier
        int[] argb = getTileARGB(idx);  // Look up tile data
        if (colorMult != 0xFFFFFF) {   // Non-trivial color multiplier
            colorMult |= 0xFF000000;
            for (int i = 0; i < etp.img.argb_buf.length; i++) {
                etp.img.argb_buf[i] = Color.blendColor(argb[i], colorMult);
            }
        } else {  // Else, just copy into destination
            System.arraycopy(argb, 0, etp.img.argb_buf, 0, etp.img.argb_buf.length);
        }
        boolean hasAlpha = false;
        // Compute simple color
        double r = 0.0, g = 0.0, b = 0.0, w = 0.0;
        for (int i = 0; i < etp.img.argb_buf.length; i++) {
            int v = etp.img.argb_buf[i];
            int ww = (v >> 24) & 0xFF;
            int rr = (v >> 16) & 0xFF;
            r += ww * rr;
            int gg = (v >> 8) & 0xFF;
            g += ww * gg;
            int bb = v & 0xFF;
            b += ww * bb;
            w += ww;
            if (ww != 0xFF) {   // Non-trivial alpha?
                hasAlpha = true;
            }
        }
        BufferOutputStream baos = new BufferOutputStream();
        
        ImageIO.setUseCache(false); /* Don't use file cache - too small to be worth it */

        String fileName = etp.name + "/" + idstr + ".png";
        etp.exp.startExportedFile(fileName);
        
        ImageIO.write(etp.img.buf_img, "png", baos);

        etp.exp.addBytesToExportedFile(baos.buf, 0, baos.len);
        etp.exp.finishExportedFile();
        String nameWithAlpha = null;
        // If has alpha, convert to gray scale for alpha image
        if (hasAlpha) {
            for (int i = 0; i < etp.img.argb_buf.length; i++) {
                int v = etp.img.argb_buf[i];
                int alpha = (v >> 24) & 0xFF;
                etp.img.argb_buf[i] = (alpha << 24) | (alpha << 16) | (alpha << 8) | alpha;
            }
            nameWithAlpha = etp.name + "/" + idstr + "_a.png";
            etp.exp.startExportedFile(nameWithAlpha);
            
            baos.reset();
            ImageIO.write(etp.img.buf_img, "png", baos);

            etp.exp.addBytesToExportedFile(baos.buf, 0, baos.len);
            etp.exp.finishExportedFile();
        }
        
        TextureSpec et = new TextureSpec();
        et.fileName = fileName;
        et.fileNameWithAlpha = nameWithAlpha;
        if (w > 0)
            et.diffuseColor = new Color((int)(r / w), (int)(g / w), (int)(b / w));
        else
            et.diffuseColor = new Color();
        et.material = getMaterialTypeByTile(idx);
        etp.txtids.put(idstr, et);  // Add to set
    }
    // Export texture pack as OBJ format material library
    public void exportAsOBJMaterialLibrary(OBJExport exp, String name) throws IOException {
        ExportPackSpec etp = new ExportPackSpec();
        etp.img = DynmapBufferedImage.allocateBufferedImage(this.nativeScale, this.nativeScale);
        etp.name = name;
        etp.exp = exp;
        // Get set of texture references from export
        Set<String> txtMaterialIds = exp.getMaterialIDs();
        // Loop through them, adding the textures needed
        for (String matId : txtMaterialIds) {
            int off = matId.lastIndexOf("__");
            int mult = -1;
            String finalMaterialId = matId;
            if ((off > 0) && ((off + 8) == matId.length())) {
                String end = matId.substring(off+2);
                try {
                    mult = Integer.parseInt(end, 16);
                } catch (NumberFormatException x) {
                    Log.warning("Invalid multiplier " + end);
                }
                finalMaterialId = matId.substring(0, off);
            }
            Integer textId = tileIDByMatID.get(finalMaterialId);
            int numericId = -1;
            if (textId == null) {
                if (!finalMaterialId.startsWith("txt")) {
                    continue;
                }
                try {
                    numericId = Integer.parseInt(finalMaterialId.substring(3));
                } catch (NumberFormatException x) {
                    Log.warning("Invalid texture ID " + finalMaterialId);
                }
            } else {
                numericId = textId;
            }
            if (numericId >= 0) {
                addImageToZip(matId, numericId, mult, etp);
            }
        }
        // Build MTL file
        exp.startExportedFile(etp.name + ".mtl");
        TreeSet<String> ids = new TreeSet<>(etp.txtids.keySet());
        for (String id : ids) {
            TextureSpec et = etp.txtids.get(id);
            String lines = "newmtl " + id + "\n";
            lines += String.format(Locale.US, "Ka %.3f %.3f %.3f\n", (double)et.diffuseColor.getRed() / 256.0, (double)et.diffuseColor.getGreen() / 256.0, (double) et.diffuseColor.getBlue() / 256.0);
            lines += String.format(Locale.US, "Kd %.3f %.3f %.3f\n", (double)et.diffuseColor.getRed() / 256.0, (double)et.diffuseColor.getGreen() / 256.0, (double) et.diffuseColor.getBlue() / 256.0);
            lines += "map_Kd " + et.fileName + "\n";
            lines += "map_Ka " + et.fileName + "\n";
            if (et.fileNameWithAlpha != null) {
                lines += "map_d " + et.fileNameWithAlpha + "\n";
            }
            if (et.material != null) {
                lines += String.format(Locale.US, "Ni %.3f\n", et.material.Ni);
                lines += String.format(Locale.US, "Ns %.3f\n", et.material.Ns);
                lines += "Ks 0.500 0.500 0.500\n";
                lines += String.format("illum %d\n", et.material.illum);
            } else {
                lines += "Ks 0.000 0.000 0.000\n";
            }
            lines += "\n";
            exp.addStringToExportedFile(lines);
        }
        exp.finishExportedFile();
    }
    private static final int[] deftxtidx = { 0, 1, 2, 3, 4, 5 };
    
    public String[] getCurrentBlockMaterials(DynmapBlockState state, MapIterator mapiter, int[] textIndex, BlockStep[] steps) {
        HDBlockStateTextureMap map = HDBlockStateTextureMap.getByBlockState(state);
        if (textIndex == null) textIndex = deftxtidx;
        String[] result = new String[textIndex.length];   // One for each face
        boolean handleStandardRotate = (steps != null) && (!map.stdrotate);
        Integer blockcoloring = this.blockColoring.getBlkStateValue(state);
        int custclrmult = -1;
        // If block has custom coloring
        if (blockcoloring != null) {
            LoadedImage img = imgs[blockcoloring];
            if (img.argb != null) {
                custclrmult = mapiter.getSmoothWaterColorMultiplier(img.argb);
            } else {
                blockcoloring = null;
            }
        }
        for (int patchidx = 0; patchidx < textIndex.length; patchidx++) {
            int faceIndex = textIndex[patchidx];
            int textId = map.faces[faceIndex];
            int mod = textId / COLORMOD_MULT_INTERNAL;
            textId = textId % COLORMOD_MULT_INTERNAL;
            BlockStep step = steps[patchidx];
            /* If clear-inside op, get out early */
            if ((mod == COLORMOD_CLEARINSIDE) || (mod == COLORMOD_MULTTONED_CLEARINSIDE)) {
                BlockStep dir = step.opposite();
                /* Check if previous block is same block type as we are:
                surface is transparent if it is */
                if (state.matchingBaseState(mapiter.getBlockTypeAt(dir))) {
                    continue;   // Skip: no texture
                }
                /* If water block, to watercolor tone op */
                if (state.isWater()) {
                    mod = COLORMOD_WATERTONED;
                } else if (mod == COLORMOD_MULTTONED_CLEARINSIDE) {
                    mod = COLORMOD_MULTTONED;
                }
            }
            
            if (ctm != null) {
                textId = ctm.mapTexture(mapiter, state, step, textId, null);
            }
            if (textId >= 0) {
                result[patchidx] = getMatIDForTileID(textId);   // Default texture
                int multiplier;
                if (blockcoloring == null) {
                    BiomeMap bio;
                    switch (mod) {
                        case COLORMOD_GRASSTONED:
                        case COLORMOD_GRASSTONED270:
                            bio = mapiter.getBiome();
                            if ((bio == BiomeMap.SWAMPLAND) && (imgs[IMG_SWAMPGRASSCOLOR] != null)) {
                                multiplier = getBiomeTonedColor(imgs[IMG_SWAMPGRASSCOLOR], -1, bio, state);
                            }
                            else {
                                multiplier = getBiomeTonedColor(imgs[IMG_GRASSCOLOR], -1, bio, state);
                            }
                            break;
                        case COLORMOD_FOLIAGETONED:
                        case COLORMOD_FOLIAGETONED270:
                        case COLORMOD_FOLIAGEMULTTONED:
                            multiplier = getBiomeTonedColor(imgs[IMG_FOLIAGECOLOR], -1, mapiter.getBiome(), state);
                            break;
                        case COLORMOD_WATERTONED:
                        case COLORMOD_WATERTONED270:
                            multiplier = getBiomeTonedColor(imgs[IMG_WATERCOLORX], -1, mapiter.getBiome(), state);
                            break;
                        case COLORMOD_PINETONED:
                            multiplier = getBiomeTonedColor(imgs[IMG_PINECOLOR], colorMultPine, mapiter.getBiome(), state);
                            break;
                        case COLORMOD_BIRCHTONED:
                            multiplier = getBiomeTonedColor(imgs[IMG_BIRCHCOLOR], colorMultBirch, mapiter.getBiome(), state);
                            break;
                        case COLORMOD_LILYTONED:
                            multiplier = getBiomeTonedColor(null, colorMultLily, mapiter.getBiome(), state);
                            break;
                        case COLORMOD_MULTTONED:
                        case COLORMOD_MULTTONED_CLEARINSIDE:
                            if(map.custColorMult == null) {
                                multiplier = getBiomeTonedColor(null, map.colorMult, mapiter.getBiome(), state);
                            }
                            else {
                                multiplier = map.custColorMult.getColorMultiplier(mapiter);
                            }
                            break;
                        default:
                            multiplier = getBiomeTonedColor(null, -1, mapiter.getBiome(), state);
                            break;
                    }
                } else {
                    multiplier = custclrmult;
                }
                int maskedValue = multiplier & 0xFFFFFF;
                // Is masked?
                if (maskedValue != 0xFFFFFF) {
                    result[patchidx] += String.format("__%06X", maskedValue);
                }
                if (handleStandardRotate && (!map.stdrotate) && ((step == BlockStep.Y_MINUS) || (step == BlockStep.Y_PLUS))) {
                    // Handle rotations
                    switch (mod) {
                        case COLORMOD_ROT90:
                            mod = COLORMOD_ROT180;
                            break;
                        case COLORMOD_ROT180:
                            mod = COLORMOD_ROT270;
                            break;
                        case COLORMOD_ROT270:
                        case COLORMOD_GRASSTONED270:
                        case COLORMOD_FOLIAGETONED270:
                        case COLORMOD_WATERTONED270:
                            mod = 0;
                            break;
                        default:
                            mod = COLORMOD_ROT90;
                            break;
                    }
                }
                // Handle rotations
                switch (mod) {
                    case COLORMOD_ROT90:
                        result[patchidx] += "@" + OBJExport.ROT90;
                        break;
                    case COLORMOD_ROT180:
                        result[patchidx] += "@" + OBJExport.ROT180;
                        break;
                    case COLORMOD_ROT270:
                    case COLORMOD_GRASSTONED270:
                    case COLORMOD_FOLIAGETONED270:
                    case COLORMOD_WATERTONED270:
                        result[patchidx] += "@" + OBJExport.ROT270;
                        break;
                    case COLORMOD_FLIPHORIZ:
                        result[patchidx] += "@" + OBJExport.HFLIP;
                        break;
                }
            }
        }
        return result;
    }
    
    // Get biome-specific color multpliers
    private int getBiomeTonedColor(LoadedImage toneMap, int fallbackColorMult, BiomeMap biome, DynmapBlockState state) {
        int mult;
        if (toneMap == null) { // No map? just use trivial
            mult = fallbackColorMult;
        } else if (toneMap.argb == null) {    // No details, use trivial
            mult = toneMap.trivial_color;
        } else {
            mult = toneMap.argb[biome.biomeLookup()];
        }
        Integer idx = this.blockColoring.getBlkStateValue(state);
        if (idx != null) {
            LoadedImage indexedImage = imgs[idx];
            if (indexedImage.argb != null) {
                mult = Color.blendColor(mult, indexedImage.argb[biome.biomeLookup()]);
            }
        }
        return mult;
    }
    
    public MaterialType getMaterialTypeByTile(int tileidx) {
        return materialbytileid.get(tileidx);
    }

    private void setMatIDForTileID(String matid, int tileid) {
        String id = matIDByTileID.get(tileid);
        if (id != null) return;
        String s = matid;
        String[] tok = s.split("/");
        if (tok.length < 5) {
            s = tok[tok.length-1];
        } else {
            s = tok[4];
            for (int i = 5; i < tok.length; i++) {
                s = s + "_" + tok[i];
            }
        }
        s = s.replace(' ', '_');
        int off = s.lastIndexOf('.');
        if (off > 0) {
            s = s.substring(0, off);
        }
        String baseid = s;
        int cnt = 2;
        while (true) {
            Integer v = tileIDByMatID.get(s);   
            if (v == null) {    // Not defined, use ID
                tileIDByMatID.put(s, tileid);
                matIDByTileID.put(tileid, s);
                return;
            } else if (v == tileid) {
                return;
            }
            s = baseid + "_" + cnt;
            cnt++;
        }
    }
    
    private String getMatIDForTileID(int txtid) {
        String id = matIDByTileID.get(txtid);
        if (id == null) {
            id = "txt" + txtid;
            matIDByTileID.put(txtid, id);
            tileIDByMatID.put(id, txtid);
        }

        return id;
    }
}

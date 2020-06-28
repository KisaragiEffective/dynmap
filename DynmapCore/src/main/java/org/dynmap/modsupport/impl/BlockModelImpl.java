package org.dynmap.modsupport.impl;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.dynmap.modsupport.BlockModel;

public abstract class BlockModelImpl implements BlockModel {
    private int[] ids = new int[0];
    private String[] names = new String[0];
    private int metaMask = -1;
    protected final ModModelDefinitionImpl mdf;

    public BlockModelImpl(int blkid, ModModelDefinitionImpl mdf) {
        addBlockID(blkid);
        this.mdf = mdf;
    }
    public BlockModelImpl(String blkname, ModModelDefinitionImpl mdf) {
        addBlockName(blkname);
        this.mdf = mdf;
    }
    
    /**
     * Add block ID to mapping (in case multiple block IDs use same texture mapping)
     * @param blockID - block ID
     */
    @Override
    public void addBlockID(int blockID) {
        if (blockID > 0) {
            boolean found = Arrays.stream(ids).anyMatch(id -> id == blockID);
            if (found) return;
            ids = Arrays.copyOf(ids, ids.length+1);
            ids[ids.length-1] = blockID;
        }
    }

    /**
     * Add block name to mapping (in case multiple block names use same texture mapping)
     * @param blockname - block name
     */
    @Override
    public void addBlockName(String blockname) {
        boolean found = Arrays.asList(names).contains(blockname);
        if (found) return;
        names = Arrays.copyOf(names, names.length+1);
        names[names.length-1] = blockname;
    }

    /**
     * Get block IDs
     * @return configured IDs
     */
    @Override
    public int[] getBlockIDs() {
        return ids;
    }

    /**
     * Get block names
     * @return configured names
     */
    @Override
    public String[] getBlockNames() {
        return names;
    }

    /**
     * Set metadata value : default is for all values (data=*).  Setting other values will match only the values that are set
     * @param data - value to match (-1 = all, 0-15 is meta value to match)
     */
    @Override
    public void setMetaValue(int data) {
        if (data < 0) { // Setting to all
            metaMask = METAMASK_ALL;
        }
        else if (data < 16) {
            if (metaMask == METAMASK_ALL) {
                metaMask = 0;
            }
            metaMask |= (1 << data);
        }
    }

    /**
     * Get matching metadata value mask
     * @return matching metadata mask: bit N is set if given metadata value matches
     */
    @Override
    public int getMetaValueMask() {
        return metaMask;
    }

    public abstract String getLine();
    
    protected String getIDsAndMeta() {
        if ((ids.length == 0) && (names.length == 0)) {
            return null;
        }
        String s = "";
        // Add ids
        s += Arrays.stream(ids)
                .mapToObj(i -> "id=" + ids[i])
                .collect(Collectors.joining(","));
        // Add names
        s += Arrays.stream(names)
                .map(name -> "id=%" + name)
                .collect(Collectors.joining(","));
        // Add meta
        if (this.metaMask == METAMASK_ALL) {
            s += ",data=*";
        }
        else {
            for (int i = 0; i < 16; i++) {
                if ((metaMask & (1 << i)) != 0) {
                    s += ",data=" + i;
                }
            }
        }
        
        return s;
    }
}

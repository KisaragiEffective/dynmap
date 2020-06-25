package org.dynmap.forge_1_15_2.permissions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.player.PlayerEntity;

import org.dynmap.Log;
import org.dynmap.forge_1_15_2.DynmapPlugin;

public class OpPermissions implements PermissionProvider {
    public final HashSet<String> usrCommands = new HashSet<>();

    public OpPermissions(String[] usrCommands) {
        Collections.addAll(this.usrCommands, usrCommands);
        Log.info("Using ops.txt for access control");
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        HashSet<String> rslt = new HashSet<>();
        if(DynmapPlugin.plugin.isOp(player)) {
            rslt.addAll(perms);
        }
        return rslt;
    }
    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        return DynmapPlugin.plugin.isOp(player);
    }

    @Override
    public boolean has(PlayerEntity psender, String permission) {
        if(psender != null) {
            if(usrCommands.contains(permission)) {
                return true;
            }
            return DynmapPlugin.plugin.isOp(psender.getEntity().getName().getString());
        }
        return true;
    }
    @Override
    public boolean hasPermissionNode(PlayerEntity psender, String permission) {
        if(psender != null) {
            return DynmapPlugin.plugin.isOp(psender.getEntity().getName().getString());
        }
        return true;
    } 
}

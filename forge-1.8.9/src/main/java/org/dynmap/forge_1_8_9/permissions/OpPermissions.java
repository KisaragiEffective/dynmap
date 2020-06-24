package org.dynmap.forge_1_8_9.permissions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;

import org.dynmap.Log;
import org.dynmap.forge_1_8_9.DynmapPlugin;

public class OpPermissions implements PermissionProvider {
    public HashSet<String> usrCommands = new HashSet<>();

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
    public boolean has(ICommandSender sender, String permission) {
        if(sender instanceof EntityPlayer) {
            if(usrCommands.contains(permission)) {
                return true;
            }
            return DynmapPlugin.plugin.isOp(sender.getCommandSenderEntity().getName());
        }
        return true;
    }
    @Override
    public boolean hasPermissionNode(ICommandSender sender, String permission) {
        if(sender instanceof EntityPlayer) {
            return DynmapPlugin.plugin.isOp(sender.getCommandSenderEntity().getName());
        }
        return true;
    } 
}

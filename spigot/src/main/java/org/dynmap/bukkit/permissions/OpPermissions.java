package org.dynmap.bukkit.permissions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.dynmap.Log;

public class OpPermissions implements PermissionProvider {
    public final HashSet<String> opCommands = new HashSet<>();

    public OpPermissions(String[] opCommands) {
        Collections.addAll(this.opCommands, opCommands);
        Log.info("Using ops.txt for access control");
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return (!(sender instanceof Player)) || (!opCommands.contains(permission) || sender.isOp());
    }
    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        return null;
    }
    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        return false;
    }
}

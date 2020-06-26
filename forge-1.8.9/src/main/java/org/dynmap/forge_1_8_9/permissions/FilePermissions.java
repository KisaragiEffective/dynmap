package org.dynmap.forge_1_8_9.permissions;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;

import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.forge_1_8_9.DynmapPlugin;

public class FilePermissions implements PermissionProvider {
    private final HashMap<String, Set<String>> perms;
    private Set<String> defperms;
    
    public static FilePermissions create() {
        File f = new File("dynmap/permissions.yml");
        if(!f.exists())
            return null;
        ConfigurationNode cfg = new ConfigurationNode(f);
        cfg.load();
        
        Log.info("Using permissions.yml for access control");
        
        return new FilePermissions(cfg);
    }
    
    private FilePermissions(ConfigurationNode cfg) {
        perms = new HashMap<>();
        cfg.keySet().forEach(k2 -> {
            List<String> strings = cfg.getStrings(k2, null);
            if (strings != null) {
                final String k = k2.toLowerCase();
                Set<String> lowercase = strings.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toCollection(HashSet::new));
                perms.put(k, lowercase);
                if (k.equals("defaultuser")) {
                    defperms = lowercase;
                }
            }
        });
    }

    private boolean hasPerm(String player, String perm) {
        Set<String> ps = perms.get(player);
        if((ps != null) && (ps.contains(perm))) {
            return true;
        }
        return defperms.contains(perm);
    }
    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        player = player.toLowerCase();
        HashSet<String> rslt = new HashSet<>();
        if(DynmapPlugin.plugin.isOp(player)) {
            rslt.addAll(perms);
        }
        else {
            for(String p : perms) {
                if(hasPerm(player, p)) {
                    rslt.add(p);
                }
            }
        }
        return rslt;
    }
    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        player = player.toLowerCase();
        if(DynmapPlugin.plugin.isOp(player)) {
            return true;
        }
        else {
            return hasPerm(player, perm);
        }
    }

    @Override
    public boolean has(ICommandSender sender, String permission) {
        if(sender instanceof EntityPlayer) {
            return hasPerm(((EntityPlayer) sender).getName().toLowerCase(), permission);
        }
        return true;
    }
    @Override
    public boolean hasPermissionNode(ICommandSender sender, String permission) {
        if(sender instanceof EntityPlayer) {
            String player = ((EntityPlayer) sender).getName().toLowerCase();
            return DynmapPlugin.plugin.isOp(player);
        }
        return false;
    } 

}

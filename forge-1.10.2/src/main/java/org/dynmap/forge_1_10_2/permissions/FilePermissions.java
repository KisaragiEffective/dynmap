package org.dynmap.forge_1_10_2.permissions;

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
import org.dynmap.forge_1_10_2.DynmapPlugin;

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
        cfg.keySet().forEach(configKey -> {
            List<String> strings = cfg.getStrings(configKey, null);
            if (strings != null) {
                final String lowerKey = configKey.toLowerCase();
                Set<String> noUppercase = strings.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toCollection(HashSet::new));
                perms.put(lowerKey, noUppercase);
                if (lowerKey.equals("defaultuser")) {
                    defperms = noUppercase;
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
        HashSet<String> rslt;
        if(DynmapPlugin.plugin.isOp(player)) {
            rslt = new HashSet<>(perms);
        } else {
            final String fp = player;
            rslt = perms.stream()
                    .filter(p -> hasPerm(fp, p))
                    .collect(Collectors.toCollection(HashSet::new));
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

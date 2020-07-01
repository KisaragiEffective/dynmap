package org.dynmap.forge_1_15_2.permissions;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.entity.player.PlayerEntity;

import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.forge_1_15_2.DynmapPlugin;

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
                HashSet<String> lowercase = strings.stream()
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
        HashSet<String> rslt;
        if(DynmapPlugin.plugin.isOp(player)) {
            rslt = new HashSet<>(perms);
        }
        else {
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
    public boolean has(PlayerEntity psender, String permission) {
        if(psender != null) {
            String n = psender.getName().getString().toLowerCase();
            return hasPerm(n, permission);
        }
        return true;
    }
    @Override
    public boolean hasPermissionNode(PlayerEntity psender, String permission) {
        if(psender != null) {
            String player = psender.getName().getString().toLowerCase();
            return DynmapPlugin.plugin.isOp(player);
        }
        return false;
    } 

}

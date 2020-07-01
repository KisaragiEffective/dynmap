package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.User;
import me.lucko.luckperms.api.caching.PermissionData;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.dynmap.Log;

public class LuckPermsPermissions implements PermissionProvider {
    final String name;
    final LuckPermsApi luckPerms;

    public static LuckPermsPermissions create(Server server, String name) {
        try {
            Class.forName("me.lucko.luckperms.api.LuckPermsApi");    /* See if class exists */
        } catch (ClassNotFoundException cnfx) {
            return null;
        }
        if (!server.getPluginManager().isPluginEnabled("LuckPerms"))
            return null;
        LuckPermsApi luckPerms = server.getServicesManager().load(LuckPermsApi.class);
        if (luckPerms == null)
            return null;
        Log.info("Using LuckPerms " + luckPerms.getPlatformInfo().getVersion() + " for access control");
        return new LuckPermsPermissions(name, luckPerms);
    }

    public LuckPermsPermissions(String name, LuckPermsApi luckPerms) {
        this.name = name;
        this.luckPerms = luckPerms;
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(name + "." + permission);
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        Set<String> result = new HashSet<>();
        PermissionData user = getUser(player);
        if (user != null) {
            result = perms.stream().filter(p -> user.getPermissionValue(name + "." + p).asBoolean()).collect(Collectors.toSet());
        }
        return result;
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        PermissionData user = getUser(player);
        if (user == null)
            return false;
        return user.getPermissionValue(name + "." + perm).asBoolean();
    }

    private PermissionData getUser(String username) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        UUID uuid;

        if (offlinePlayer != null && offlinePlayer.getUniqueId() != null)
            uuid = offlinePlayer.getUniqueId();
        else
            uuid = joinFuture(luckPerms.getStorage().getUUID(username));

        if (uuid == null)
            return null;

        User user = luckPerms.getUser(uuid);
        if (user == null) {
            joinFuture(luckPerms.getStorage().loadUser(uuid));
            user = luckPerms.getUser(uuid);
        }

        if (user == null)
            return null;

        return user.getCachedData().getPermissionData(luckPerms.getContextManager().getStaticContexts());
    }

    private static <T> T joinFuture(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
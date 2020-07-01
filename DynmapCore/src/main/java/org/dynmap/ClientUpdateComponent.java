package org.dynmap;

import java.util.List;
import org.dynmap.common.DynmapPlayer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ClientUpdateComponent extends Component {
    private final int hideifshadow;
    private final int hideifunder;
    private final boolean hideifsneaking;
    private final boolean hideifinvisiblepotion;
    private final boolean is_protected;
    public static boolean usePlayerColors;
    public static boolean hideNames;
    
    public ClientUpdateComponent(final DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        
        hideNames = configuration.getBoolean("hidenames", false);
        hideifshadow = configuration.getInteger("hideifshadow", 15);
        hideifunder = configuration.getInteger("hideifundercover", 15);
        hideifsneaking = configuration.getBoolean("hideifsneaking", false);
        hideifinvisiblepotion = configuration.getBoolean("hide-if-invisiblity-potion", true);
        is_protected = configuration.getBoolean("protected-player-info", false);
        usePlayerColors = configuration.getBoolean("use-name-colors", false);
        if(is_protected)
            core.player_info_protected = true;
        
        core.events.addListener("buildclientupdate", (Event.Listener<ClientUpdateEvent>) e -> buildClientUpdate(e));
    }
    
    protected void buildClientUpdate(ClientUpdateEvent e) {
        DynmapWorld world = e.world;
        JSONObject u = e.update;
        long since = e.timestamp;
        String worldName = world.getName();
        boolean see_all = true;
        
        if(is_protected && (!e.include_all_users)) {
            if(e.user != null)
                see_all = core.getServer().checkPlayerPermission(e.user, "playermarkers.seeall");
            else
                see_all = false;
        }
        if((e.include_all_users) && is_protected) { /* If JSON request AND protected, leave mark for script */
            JSONUtils.setValue(u, "protected", true);
        }
        
        JSONUtils.setValue(u, "confighash", core.getConfigHashcode());

        JSONUtils.setValue(u, "servertime", world.getTime() % 24000);
        JSONUtils.setValue(u, "hasStorm", world.hasStorm());
        JSONUtils.setValue(u, "isThundering", world.isThundering());

        JSONUtils.setValue(u, "players", new JSONArray());
        List<DynmapPlayer> players = core.playerList.getVisiblePlayers();
        for(DynmapPlayer p : players) {
            boolean hide = false;
            DynmapLocation pl = p.getLocation();
            DynmapWorld pw = core.getWorld(pl.world);
            if(pw == null) {
                hide = true;
            }
            JSONObject jp = new JSONObject();
            
            JSONUtils.setValue(jp, "type", "player");
            if (hideNames)
                JSONUtils.setValue(jp, "name", "");
            else if (usePlayerColors)
                JSONUtils.setValue(jp, "name", Client.encodeColorInHTML(p.getDisplayName()));
            else
                JSONUtils.setValue(jp, "name", Client.stripColor(p.getDisplayName()));
            JSONUtils.setValue(jp, "account", p.getName());
            if((!hide) && (hideifshadow < 15)) {
                if(pw.getLightLevel((int)pl.x, (int)pl.y, (int)pl.z) <= hideifshadow) {
                    hide = true;
                }
            }
            if((!hide) && (hideifunder < 15)) {
                if(pw.canGetSkyLightLevel()) { /* If we can get real sky level */
                    if(pw.getSkyLightLevel((int)pl.x, (int)pl.y, (int)pl.z) <= hideifunder) {
                        hide = true;
                    }
                }
                else if(!pw.isNether()) {   /* Not nether */
                    if(pw.getHighestBlockYAt((int)pl.x, (int)pl.z) > pl.y) {
                        hide = true;
                    }
                }
            }
            if((!hide) && hideifsneaking && p.isSneaking()) {
                hide = true;
            }
            if((!hide) && is_protected && (!see_all)) {
                if(e.user != null) {
                    hide = !core.testIfPlayerVisibleToPlayer(e.user, p.getName());
                }
                else {
                    hide = true;
                }
            }
            if((!hide) && hideifinvisiblepotion && p.isInvisible()) {
                hide = true;
            }
                
            /* Don't leak player location for world not visible on maps, or if sendposition disbaled */
            DynmapWorld pworld = MapManager.mapman.worldsLookup.get(pl.world);
            /* Fix typo on 'sendpositon' to 'sendposition', keep bad one in case someone used it */
            if(configuration.getBoolean("sendposition", true) && configuration.getBoolean("sendpositon", true) &&
                    (pworld != null) && pworld.sendposition && (!hide)) {
                JSONUtils.setValue(jp, "world", pl.world);
                JSONUtils.setValue(jp, "x", pl.x);
                JSONUtils.setValue(jp, "y", pl.y);
                JSONUtils.setValue(jp, "z", pl.z);
            }
            else {
                JSONUtils.setValue(jp, "world", "-some-other-bogus-world-");
                JSONUtils.setValue(jp, "x", 0.0);
                JSONUtils.setValue(jp, "y", 64.0);
                JSONUtils.setValue(jp, "z", 0.0);
            }
            /* Only send health if enabled AND we're on visible world */
            if (configuration.getBoolean("sendhealth", false) && (pworld != null) && pworld.sendhealth && (!hide)) {
                JSONUtils.setValue(jp, "health", p.getHealth());
                JSONUtils.setValue(jp, "armor", p.getArmorPoints());
            }
            else {
                JSONUtils.setValue(jp, "health", 0);
                JSONUtils.setValue(jp, "armor", 0);
            }
            JSONUtils.setValue(jp, "sort", p.getSortWeight());
            JSONUtils.array(u, "players", jp);
        }
        List<DynmapPlayer> hidden = core.playerList.getHiddenPlayers();
        if(configuration.getBoolean("includehiddenplayers", false)) {
            for(DynmapPlayer p : hidden) {
                JSONObject jp = new JSONObject();
                JSONUtils.setValue(jp, "type", "player");
                if (hideNames) 
                    JSONUtils.setValue(jp, "name", "");
                else if (usePlayerColors)
                    JSONUtils.setValue(jp, "name", Client.encodeColorInHTML(p.getDisplayName()));
                else
                    JSONUtils.setValue(jp, "name", Client.stripColor(p.getDisplayName()));
                JSONUtils.setValue(jp, "account", p.getName());
                JSONUtils.setValue(jp, "world", "-hidden-player-");
                JSONUtils.setValue(jp, "x", 0.0);
                JSONUtils.setValue(jp, "y", 64.0);
                JSONUtils.setValue(jp, "z", 0.0);
                JSONUtils.setValue(jp, "health", 0);
                JSONUtils.setValue(jp, "armor", 0);
                JSONUtils.setValue(jp, "sort", p.getSortWeight());
                JSONUtils.array(u, "players", jp);
            }
            JSONUtils.setValue(u, "currentcount", core.getCurrentPlayers());
        }
        else {
            JSONUtils.setValue(u, "currentcount", core.getCurrentPlayers() - hidden.size());
        }

        JSONUtils.setValue(u, "updates", new JSONArray());
        for(Object update : core.mapManager.getWorldUpdates(worldName, since)) {
            JSONUtils.array(u, "updates", update);
        }
    }

}

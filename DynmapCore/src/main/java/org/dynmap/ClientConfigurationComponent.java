package org.dynmap;

import org.json.simple.JSONObject;

public class ClientConfigurationComponent extends Component {
    public ClientConfigurationComponent(final DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        core.events.<JSONObject>addListener("buildclientconfiguration", t -> {
            ConfigurationNode c = core.configuration;
            JSONUtils.setValue(t, "confighash", core.getConfigHashcode());
            JSONUtils.setValue(t, "updaterate", c.getFloat("updaterate", 1.0f));
            JSONUtils.setValue(t, "showplayerfacesinmenu", c.getBoolean("showplayerfacesinmenu", true));
            JSONUtils.setValue(t, "joinmessage", c.getString("joinmessage", "%playername% joined"));
            JSONUtils.setValue(t, "quitmessage", c.getString("quitmessage", "%playername% quit"));
            JSONUtils.setValue(t, "spammessage", c.getString("spammessage", "You may only chat once every %interval% seconds."));
            JSONUtils.setValue(t, "webprefix", unescapeString(c.getString("webprefix", "[WEB] ")));
            JSONUtils.setValue(t, "defaultzoom", c.getInteger("defaultzoom", 0));
            JSONUtils.setValue(t, "sidebaropened", c.getString("sidebaropened", "false"));
            JSONUtils.setValue(t, "dynmapversion", core.getDynmapPluginVersion());
            JSONUtils.setValue(t, "coreversion", core.getDynmapCoreVersion());
            JSONUtils.setValue(t, "cyrillic", c.getBoolean("cyrillic-support", false));
            JSONUtils.setValue(t, "showlayercontrol", c.getString("showlayercontrol", "true"));
            JSONUtils.setValue(t, "grayplayerswhenhidden", c.getBoolean("grayplayerswhenhidden", true));
            JSONUtils.setValue(t, "login-enabled", core.isLoginSupportEnabled());
            String sn = core.getServer().getServerName();
            if(sn.equals("Unknown Server"))
                sn = "Minecraft Dynamic Map";
            JSONUtils.setValue(t, "title", c.getString("webpage-title", sn));
            JSONUtils.setValue(t, "msg-maptypes", c.getString("msg/maptypes", "Map Types"));
            JSONUtils.setValue(t, "msg-players", c.getString("msg/players", "Players"));
            JSONUtils.setValue(t, "msg-chatrequireslogin", c.getString("msg/chatrequireslogin", "Chat Requires Login"));
            JSONUtils.setValue(t, "msg-chatnotallowed", c.getString("msg/chatnotallowed", "You are not permitted to send chat messages"));
            JSONUtils.setValue(t, "msg-hiddennamejoin", c.getString("msg/hiddennamejoin", "Player joined"));
            JSONUtils.setValue(t, "msg-hiddennamequit", c.getString("msg/hiddennamequit", "Player quit"));
            JSONUtils.setValue(t, "maxcount", core.getMaxPlayers());

            DynmapWorld defaultWorld = null;
            String defmap = null;
            JSONUtils.array(t, "worlds", null);
            for(DynmapWorld world : core.mapManager.getWorlds()) {
                if (world.maps.size() == 0) continue;
                if (defaultWorld == null) defaultWorld = world;
                JSONObject wo = new JSONObject();
                JSONUtils.setValue(wo, "name", world.getName());
                JSONUtils.setValue(wo, "title", world.getTitle());
                JSONUtils.setValue(wo, "protected", world.isProtected());
                DynmapLocation center = world.getCenterLocation();
                JSONUtils.setValue(wo, "center/x", center.x);
                JSONUtils.setValue(wo, "center/y", center.y);
                JSONUtils.setValue(wo, "center/z", center.z);
                JSONUtils.setValue(wo, "extrazoomout", world.getExtraZoomOutLevels());
                JSONUtils.setValue(wo, "sealevel", world.sealevel);
                JSONUtils.setValue(wo, "worldheight", world.worldheight);
                JSONUtils.array(t, "worlds", wo);

                for(MapType mt : world.maps) {
                    mt.buildClientConfiguration(wo, world);
                    if(defmap == null) defmap = mt.getName();
                }
            }
            JSONUtils.setValue(t, "defaultworld", c.getString("defaultworld", defaultWorld == null ? "world" : defaultWorld.getName()));
            JSONUtils.setValue(t, "defaultmap", c.getString("defaultmap", defmap == null ? "surface" : defmap));
            if(c.getString("followmap", null) != null)
                JSONUtils.setValue(t, "followmap", c.getString("followmap"));
            if(c.getInteger("followzoom",-1) >= 0)
                JSONUtils.setValue(t, "followzoom", c.getInteger("followzoom", 0));
        });
    }
    
}

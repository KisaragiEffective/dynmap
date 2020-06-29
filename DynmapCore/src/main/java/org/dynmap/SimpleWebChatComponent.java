package org.dynmap;

import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapListenerManager.ChatEventListener;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.json.simple.JSONObject;

public class SimpleWebChatComponent extends Component {

    public SimpleWebChatComponent(final DynmapCore plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.events.addListener("webchat", (Event.Listener<ChatEvent>) t -> {
            if(plugin.getServer().sendWebChatEvent(t.source, t.name, t.message)) {
                String msg;
                String msgfmt = plugin.configuration.getString("webmsgformat", null);
                if(msgfmt != null) {
                    msgfmt = unescapeString(msgfmt);
                    msg = msgfmt.replace("%playername%", t.name).replace("%message%", t.message);
                }
                else {
                    msg = unescapeString(plugin.configuration.getString("webprefix", "\u00A72[WEB] ")) + t.name + ": " + unescapeString(plugin.configuration.getString("websuffix", "\u00A7f")) + t.message;
                }
                plugin.getServer().broadcastMessage(msg);
                if (core.mapManager != null) {
                    core.mapManager.pushUpdate(new Client.ChatMessage("web", null, t.name, t.message, null));
                }
            }
        });
        
        plugin.events.addListener("buildclientconfiguration", (Event.Listener<JSONObject>) t -> JSONUtils.setValue(t, "allowchat", configuration.getBoolean("allowchat", false)));
        
        if (configuration.getBoolean("allowchat", false)) {
            plugin.listenerManager.addListener(EventType.PLAYER_CHAT, (ChatEventListener) (p, msg) -> {
                if(core.disable_chat_to_web) return;
                if(core.mapManager != null)
                    core.mapManager.pushUpdate(new Client.ChatMessage("player", "", p.getDisplayName(), msg, p.getName()));
            });
            plugin.listenerManager.addListener(EventType.PLAYER_JOIN, (DynmapListenerManager.PlayerEventListener) p -> {
                if(core.disable_chat_to_web) return;
                if((core.mapManager != null) && (core.playerList != null) && (core.playerList.isVisiblePlayer(p.getName()))) {
                    core.mapManager.pushUpdate(new Client.PlayerJoinMessage(p.getDisplayName(), p.getName()));
                }
            });
            plugin.listenerManager.addListener(EventType.PLAYER_QUIT, (DynmapListenerManager.PlayerEventListener) p -> {
                if(core.disable_chat_to_web) return;
                if((core.mapManager != null) && (core.playerList != null) && (core.playerList.isVisiblePlayer(p.getName()))) {
                    core.mapManager.pushUpdate(new Client.PlayerQuitMessage(p.getDisplayName(), p.getName()));
                }
            });
        }
    }
}

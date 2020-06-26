package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
public class ClientComponent extends Component {
    private boolean disabled;
    
    public ClientComponent(final DynmapCore plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.events.addListener("buildclientconfiguration", (Event.Listener<JSONObject>) root -> {
            if(!disabled)
                buildClientConfiguration(root);
        });
    }
    
    protected void disableComponent() {
        disabled = true;
    }
    
    protected void buildClientConfiguration(JSONObject root) {
        JSONObject o = createClientConfiguration();
        JSONUtils.array(root, "components", o);
    }
    
    protected JSONObject createClientConfiguration() {
        JSONObject o = convertMap(configuration);
        o.remove("class");
        return o;
    }
    
    protected static JSONObject convertMap(Map<String, ?> m) {
        JSONObject o = new JSONObject();
        for(Map.Entry<String, ?> entry : m.entrySet()) {
            JSONUtils.setValue(o, entry.getKey(), convert(entry.getValue()));
        }
        return o;
    }
    
    @SuppressWarnings("unchecked")
    protected static JSONArray convertList(List<?> list) {
        return list.stream()
                .map(ClientComponent::convert)
                .collect(Collectors.toCollection(JSONArray::new));
    }
    
    @SuppressWarnings("unchecked")
    protected static Object convert(Object o) {
        if (o instanceof Map<?, ?>) {
            return convertMap((Map<String, ?>)o);
        } else if (o instanceof List<?>) {
            return convertList((List<?>)o);
        }
        return o;
    }

}

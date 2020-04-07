package sakura.kooi.MixedBiliveServer.clients;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;
import sakura.kooi.MixedBiliveServer.Constants;
import sakura.kooi.MixedBiliveServer.utils.PerMessageDeflateExtension;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OfficialClient extends WebSocketClient implements IBroadcastSource {
    private ClientContainer container;
    public OfficialClient(URI uri, String protocol, ClientContainer container) {
        super(uri, new Draft_6455(Collections.singletonList(new PerMessageDeflateExtension()), Collections.singletonList(new Protocol(protocol))), toHeader(protocol));
        this.container = container;
        container.setHostString("ws://" + uri.getAuthority());
    }

    private static Map<String, String> toHeader(String protocol) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Sec-WebSocket-Protocol", protocol);
        headers.put("User-Agent", "Bilive_Client 2.2.6.2260V");
        return headers;
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        container.onConnected();
    }

    @Override
    public void onMessage(String s) {
        container.onPacketReceived(s);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        container.onDisconnected(code+" "+reason);
    }

    @Override
    public void onError(Exception e) {
        container.onErrorOccurred("数据处理出错", e);
    }

    @Override
    public void disconnect(String reason) {
        this.close();
    }

    @Override
    public void processPacket(String packet) {
        JsonElement json = JsonParser.parseString(packet);
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            if (jsonObject.has("cmd")) {
                String cmd = jsonObject.get("cmd").getAsString();
                if (Constants.WHITELISTED_COMMANDS.contains(cmd.toLowerCase())) {
                    if (cmd.equals("sysmsg")) {
                        container.getLogger().success("来自 {} 的系统消息: {}", container.getHostString(), jsonObject.get("msg").getAsString());
                    } else {
                        long room = jsonObject.get("roomID").getAsLong();
                        long id = jsonObject.get("id").getAsLong();
                        String title = jsonObject.get("title").getAsString().replace("抽奖抽奖", "抽奖");
                        String type = jsonObject.get("type").getAsString();
                        int time = jsonObject.has("time") ? jsonObject.get("time").getAsInt() : -1;
                        int max_time = jsonObject.has("max_time") ? jsonObject.get("max_time").getAsInt() : -1;
                        int time_wait = jsonObject.has("time_wait") ? jsonObject.get("time_wait").getAsInt() : -1;
                        container.onLotteryReceived(cmd, id, room, type, title, time, max_time, time_wait);
                    }
                } else {
                    container.getLogger().trace("Drop non-whitelisted command from {} -> {}", container.getHostString(), cmd);
                }
                return;
            }
        }
        container.getLogger().warn("收到未知的数据包 {} -> {}", container.getHostString(), packet);
    }
}

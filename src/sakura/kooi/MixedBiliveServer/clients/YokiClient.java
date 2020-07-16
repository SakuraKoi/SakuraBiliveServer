package sakura.kooi.MixedBiliveServer.clients;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import sakura.kooi.MixedBiliveServer.Constants;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class YokiClient extends WebSocketClient implements IBroadcastSource {
    private ClientContainer container;

    public YokiClient(URI uri, ClientContainer container) throws IOException {
        super(uri, getHeader(container));
        this.container = container;
        container.setHostString("ws://" + uri.getAuthority());
    }

    private static Map<String, String> getHeader(ClientContainer container) throws IOException {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("user-agent", "Yoki/233333/40");
        headers.put("token", getToken(container));
        return headers;
    }

    private static String getToken(ClientContainer container) throws IOException {
        container.getLogger().info("尝试请求Token...");
        HttpResponse<String> response = Unirest.get(Constants.Protocol.YOKI_TOKEN_SERVER)
                .header("Referer", Constants.Protocol.YOKI_TOKEN_SERVER)
                .header("User-Agent", "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1)")
                .asString();
        if (response.getStatus() != 200) throw new IOException("Failed get token: HTTP code "+response.getStatus());
        String json = response.getBody();
        JsonElement jsonElement = JsonParser.parseString(json);
        if (!jsonElement.isJsonObject()) throw new IOException("Failed get token: response "+json);
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        if (!jsonObject.has("token")) throw new IOException("Failed get token: response "+json);
        String token = jsonObject.get("token").getAsString();
        container.getLogger().success("成功获得Yoki监听服务器Token: {}", token);
        return token;
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
        container.onDisconnected(code + " " + reason);
    }

    @Override
    public void onError(Exception ex) {
        if (!(ex instanceof ConnectException))
            container.onErrorOccurred("数据处理出错", ex);
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

package sakura.kooi.MixedBiliveServer.clients;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.ConnectException;
import java.net.URI;
import java.nio.ByteBuffer;

public class RaffleJsClient extends WebSocketClient implements IBroadcastSource {
    private ClientContainer container;
    public RaffleJsClient(URI uri, ClientContainer container) {
        super(uri);
        this.container = container;
        container.setHostString("ws://" + uri.getAuthority());
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        container.onConnected();
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        onMessage(new String(bytes.array()));
    }

    @Override
    public void onMessage(String message) {
        container.onPacketReceived(message);
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
        JsonElement element = JsonParser.parseString(packet);
        if (element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            if (json.has("category")) {
                String category = json.get("category").getAsString();
                if (category.equals("guard")) {
                    long room = json.get("roomid").getAsLong();
                    long id = json.get("id").getAsLong();
                    String raffleType = json.get("type").getAsString();
                    String title = json.get("name").getAsString();
                    String cmd = "lottery";
                    container.onLotteryReceived(cmd, id, room, raffleType, title, 1200, -1, -1);
                    return;
                } else {
                    container.getLogger().trace("Drop lottery type {}, packet {}", category, packet);
                    return;
                }
            }
        }
        container.getLogger().warn("收到未知的数据包 {} -> {}", container.getHostString(), packet);
    }
}

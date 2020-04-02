package sakura.kooi.MixedBiliveServer.clients;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Getter;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import sakura.kooi.MixedBiliveServer.utils.ClientCounter;
import sakura.kooi.MixedBiliveServer.Constants;
import sakura.kooi.MixedBiliveServer.SakuraBilive;
import sakura.kooi.logger.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class YokiClient extends WebSocketClient {
    public static final Logger logger = Logger.of("YokiClient");
    @Getter
    private static ClientCounter counter = new ClientCounter();

    private boolean running = true;
    private int retry;
    private String hostString;
    public YokiClient(URI yokiServer, int retry) throws IOException {
        super(yokiServer, toHeader());
        this.retry = retry;
        hostString = yokiServer.getHost()+":"+yokiServer.getPort();
    }

    private static Map<String, String> toHeader() throws IOException {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("user-agent", "Yoki/233333/40");
        headers.put("token", getToken());
        return headers;
    }

    private static String getToken() throws IOException {
        logger.info("尝试请求Token...");
        HttpResponse<String> response = Unirest.get(Constants.YOKI_TOKEN_SERVER)
                .header("Referer", Constants.YOKI_TOKEN_SERVER)
                .header("User-Agent", "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1)")
                .asString();
        if (response.getStatus() != 200) throw new IOException("Failed get token: HTTP code "+response.getStatus());
        String json = response.getBody();
        JsonElement jsonElement = JsonParser.parseString(json);
        if (!jsonElement.isJsonObject()) throw new IOException("Failed get token: response "+json);
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        if (!jsonObject.has("token")) throw new IOException("Failed get token: response "+json);
        String token = jsonObject.get("token").getAsString();
        logger.success("成功获得Yoki监听服务器Token: {}", token);
        return token;
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        logger.info("成功连接至 ws://{}", hostString);
        retry = 0;
    }

    @Override
    public void onMessage(String s) {
        logger.log(Constants.LOGLEVEL_PACKET, "Received packet {}", s);
        JsonElement json = JsonParser.parseString(s);
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            if (jsonObject.has("cmd")) {
                String cmd = jsonObject.get("cmd").getAsString();
                if (Constants.WHITELISTED_COMMANDS.contains(cmd.toLowerCase())) {
                    if (cmd.equals("sysmsg")) {
                        logger.success("来自 ws://{} 的系统消息: {}", hostString, jsonObject.get("msg").getAsString());
                    } else {
                        long room = jsonObject.get("roomID").getAsLong();
                        long id = jsonObject.get("id").getAsLong();
                        String title = jsonObject.get("title").getAsString().replace("抽奖抽奖", "抽奖");
                        String type = jsonObject.get("type").getAsString();
                        int time = jsonObject.has("time") ? jsonObject.get("time").getAsInt() : -1;
                        int max_time = jsonObject.has("max_time") ? jsonObject.get("max_time").getAsInt() : -1;
                        int time_wait = jsonObject.has("time_wait") ? jsonObject.get("time_wait").getAsInt() : -1;
                        logger.info("源 ws://{} -> {} {} #{}", hostString, room, title, id);
                        counter.increment(cmd);
                        SakuraBilive.rebroadcast(cmd, id, room, type, title+" [YOKI]", time, max_time, time_wait);
                    }
                } else {
                    logger.trace("Drop non-whitelisted command from ws://{} -> {}", hostString, cmd);
                }
                return;
            }
        }
        logger.warn("收到未知的数据包 ws://{} -> {}", hostString, s);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("到节点 ws://{} 的连接已断开 : {} {}", hostString, code, reason);
        if (running) {
            retry++;
            SakuraBilive.reconnectYokiClient(retry);
        }
    }

    @Override
    public void onError(Exception e) {
        logger.errorEx("节点服务器 ws://{} 连接处理出错", e, hostString);
    }

    public void shutdown() {
        running = false;
        this.close();
    }
}

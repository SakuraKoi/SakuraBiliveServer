package sakura.kooi.MixedBiliveServer.clients;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.commons.codec.binary.StringUtils;
import sakura.kooi.MixedBiliveServer.Constants;
import sakura.kooi.MixedBiliveServer.SakuraBilive;
import sakura.kooi.MixedBiliveServer.utils.FishingDetection;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class DawnClient implements IBroadcastSource {
    private ClientContainer container;
    private Socket socket;
    private DataOutputStream daos;
    public DawnClient(ClientContainer container) {
        this.container = container;
        socket = new Socket();
    }

    @Override
    public void connect() throws IOException {
        socket.connect(new InetSocketAddress(InetAddress.getByName(Constants.Protocol.BILI_HELPER_SERVER_HOST), Constants.Protocol.BILI_HELPER_SERVER_PORT));
        container.setHostString("tcp://"+ Constants.Protocol.BILI_HELPER_SERVER_HOST+":"+ Constants.Protocol.BILI_HELPER_SERVER_PORT);
        SakuraBilive.getThreadPool().execute(this::handleConnection);
    }

    @Override
    public void disconnect(String reason) throws IOException {
        if (!socket.isClosed()) {
            socket.close();
            container.onDisconnected(reason);
        }
    }

    @Override
    public void processPacket(String packet) {
        JsonElement element = JsonParser.parseString(packet);
        if (element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            String cmd = json.get("cmd").getAsString();
            if ("Guard".equalsIgnoreCase(cmd)) {
                JsonElement dataElem = json.get("data");
                if (dataElem.isJsonObject()) {
                    JsonObject data = dataElem.getAsJsonObject();
                    long room = data.get("RoomId").getAsLong();
                    long id = data.get("Id").getAsLong();

                    SakuraBilive.getThreadPool().submit(() -> {
                        if (FishingDetection.isFishingRoom(room)) {
                            container.getLogger().warn("丢弃钓鱼抽奖 {} {} #{}", room, "舰队抽奖", id);
                            return;
                        }
                        container.onLotteryReceived(cmd, id, room, "lottery", "舰队抽奖", 1200, -1, -1);
                    });
                } else {
                    container.getLogger().warn("收到错误格式的数据包 {} -> {}", container.getHostString(), packet);
                }
            } else {
                container.getLogger().trace("Drop non-whitelisted command from {} -> {}", container.getHostString(), packet);
            }
        } else {
            container.getLogger().warn("收到未知的数据包 {} -> {}", container.getHostString(), packet);
        }
    }

    private void handleConnection() {
        try {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(in);
            BufferedOutputStream bos = new BufferedOutputStream(out);
            DataInputStream dis = new DataInputStream(bis);
            daos = new DataOutputStream(bos);
            container.getLogger().info("成功连接至 {}, 正在登录节点...", container.getHostString());
            writePacket(daos, createLoginPacket(Constants.Protocol.DAWN_SERVER_KEY));
            writePacket(daos, createHeartbeatPacket());
            SakuraBilive.getThreadPool().execute(this::keepAlive);
            container.onConnected();
            int length;
            try {
                while ((length = dis.readInt()) != -1) {
                    if (length == 0) continue;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream packetOut = new DataOutputStream(baos);
                    length -= 4;
                    for (int i = 0; i < length; i++)
                        packetOut.write(dis.read());
                    String packet = StringUtils.newStringUtf8(baos.toByteArray())
                            .replace("True", "true").replace("False", "false").replace("None", "null");
                    container.onPacketReceived(packet);
                }
            } catch (EOFException e) {
                disconnect("连接关闭");
            } catch (IOException e) {
                if (container.getRunning().get())
                    container.getLogger().error("读取数据包时发生了错误", e);
                disconnect("IO错误");
            }
        } catch (IOException e) {
            container.getLogger().error("登录监听服务器时发生了错误", e);
            try {
                socket.close();
            } catch (IOException e1) {
                container.getLogger().error("关闭连接时发生了错误", e);
            }
            onClosed();
        }
    }

    private void keepAlive() {
        while(container.getRunning().get() && !socket.isClosed()) {
            try {
                writePacket(daos, createHeartbeatPacket());
            } catch (IOException e) {
                container.getLogger().error("发送心跳出错", e);
            }
            try {
                Thread.sleep(25000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String createHeartbeatPacket() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("cmd", new JsonPrimitive("HeartBeat"));
        jsonObject.add("code", new JsonPrimitive(0));
        JsonObject keyObj = new JsonObject();
        jsonObject.add("data", keyObj);
        return Constants.GSON.toJson(jsonObject);
    }

    private String createLoginPacket(String key) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("cmd", new JsonPrimitive("Auth"));
        jsonObject.add("code", new JsonPrimitive(0));
        JsonObject keyObj = new JsonObject();
        keyObj.add("key", new JsonPrimitive(key));
        jsonObject.add("data", keyObj);
        return Constants.GSON.toJson(jsonObject);
    }

    private void writePacket(DataOutputStream daos, String data) throws IOException {
        byte[] content = StringUtils.getBytesUtf8(data);
        int lenbody = content.length;
        int lenheader = 4;
        daos.writeInt(lenbody+lenheader);
        daos.write(content);
        daos.flush();
    }

    private void onClosed() {
        container.onDisconnected("连接断开");
    }
}

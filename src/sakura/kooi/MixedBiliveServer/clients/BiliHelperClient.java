package sakura.kooi.MixedBiliveServer.clients;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import sakura.kooi.MixedBiliveServer.utils.ClientCounter;
import sakura.kooi.MixedBiliveServer.Constants;
import sakura.kooi.MixedBiliveServer.SakuraBilive;
import sakura.kooi.MixedBiliveServer.utils.FishingDetection;
import sakura.kooi.logger.Logger;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Pattern;

public class BiliHelperClient implements IBroadcastSource {
    private static final Pattern THANKS_PATTERN = Pattern.compile("感谢.*?赠送的");
    private ClientContainer container;
    private boolean loggedIn = false;

    private Socket socket;
    private DataOutputStream daos;
    public BiliHelperClient(ClientContainer container) {
        this.container = container;
        socket = new Socket();
    }

    public void connect() throws IOException {
        socket.connect(new InetSocketAddress(InetAddress.getByName(Constants.BILI_HELPER_SERVER_HOST), Constants.BILI_HELPER_SERVER_PORT));
        container.setHostString(""+Constants.BILI_HELPER_SERVER_HOST+":"+Constants.BILI_HELPER_SERVER_PORT);
        SakuraBilive.getThreadPool().execute(this::handleConnection);
    }

    @Override
    public void disconnect() throws IOException {
        socket.close();
        container.onDisconnected("断开连接");
    }

    @Override
    public void processPacket(String packet) {
        JsonElement element = JsonParser.parseString(packet);
        if (element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            if (json.get("code").getAsInt()==0) {
                String type = json.get("type").getAsString();
                if (type.equals("entered")) {
                    if (!loggedIn) {
                        loggedIn = true;
                        SakuraBilive.getThreadPool().execute(this::keepAlive);
                        container.onConnected();
                    }
                } else if (type.equals("raffle")) {
                    JsonElement dataElem = json.get("data");
                    if (dataElem.isJsonObject()) {
                        JsonObject data = dataElem.getAsJsonObject();
                        String cmd;
                        long room = data.get("room_id").getAsLong();
                        long id = data.get("raffle_id").getAsLong();
                        String raffleType = data.get("raffle_type").getAsString();
                        String titleS = data.get("raffle_title").getAsString();
                        if ("guard".equals(raffleType)) {
                            cmd = "lottery";
                        } else {
                            container.getLogger().trace("Unknown lottery type {}, packet {}", raffleType, packet);
                            return;
                        }
                        final String title = titleS;
                        SakuraBilive.getThreadPool().submit(() -> {
                            if (FishingDetection.isFishingRoom(room)) {
                                container.getLogger().warn("丢弃钓鱼抽奖 {} {} #{}", room, title, id);
                                return;
                            }
                            container.onLotteryReceived(cmd, id, room, type, title + " [BIHP]", 1200, -1, -1);
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
            writePacked(daos, createLoginPacket(Constants.BILI_HELPER_SERVER_KEY));
            writePacked(daos, "");
            int length;
            try {
                while ((length = dis.readInt()) != -1) {
                    if (length == 0) continue;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream packetOut = new DataOutputStream(baos);
                    for (int i = 0; i < length; i++)
                        packetOut.write(dis.read());
                    String packet = new String(baos.toByteArray());
                    container.onPacketReceived(packet);
                }
            } catch (EOFException e) {
                disconnect();
            } catch (IOException e) {
                if (container.getRunning().get())
                    container.getLogger().error("读取数据包时发生了错误", e);
                disconnect();
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
                writePacked(daos, "");
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

    private void writePacked(DataOutputStream daos, String data) throws IOException {
        char[] array = data.toCharArray();
        daos.writeInt(array.length);
        for (char c : array) {
            daos.write(c);
        }
        daos.flush();
    }

    private String createLoginPacket(String key) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("code", new JsonPrimitive(0));
        jsonObject.add("type", new JsonPrimitive("ask"));
        JsonObject keyObj = new JsonObject();
        keyObj.add("key", new JsonPrimitive(key));
        jsonObject.add("data", keyObj);
        return Constants.GSON.toJson(jsonObject);
    }

    private void onClosed() {
        container.onDisconnected("连接断开");
    }
}

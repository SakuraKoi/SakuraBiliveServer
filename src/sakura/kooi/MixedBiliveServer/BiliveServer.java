package sakura.kooi.MixedBiliveServer;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import sakura.kooi.logger.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class BiliveServer extends WebSocketServer {
    public static final Logger logger = Logger.of("BiliveServer");
    public static AtomicInteger currentOnline = new AtomicInteger();
    public static BiMap<WebSocket, String> addressHashMap = Maps.synchronizedBiMap(HashBiMap.create());
    public static BiMap<WebSocket, String> tokenHashMap = Maps.synchronizedBiMap(HashBiMap.create());
    public BiliveServer(InetSocketAddress address) {
        super(address);
        this.setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        String host = webSocket.getRemoteSocketAddress().getHostString() + ":" + webSocket.getRemoteSocketAddress().getPort();
        try {
            addressHashMap.put(webSocket, host);
        } catch (IllegalArgumentException e) {
            webSocket.send("{\"cmd\":\"sysmsg\",\"msg\":\"拒绝连接 - 一个相同IP的客户端已在线\"}");
            webSocket.close();
            return;
        }

        logger.success("客户端 {} 已连接至服务器", host);
        webSocket.send("{\"cmd\":\"sysmsg\",\"msg\":\"欢迎连接到SakuraKooi的聚合监听服务器\"}");
        currentOnline.incrementAndGet();
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        currentOnline.decrementAndGet();
        logger.warn("客户端 {} 已断开连接", addressHashMap.get(webSocket));
        addressHashMap.remove(webSocket);
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {

    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        logger.error("Unexpected error occurred.", e);
    }

    @Override
    public void onStart() {
        logger.success("SakuraBilive Websocket 已运行在 {}:{} 接口上", this.getAddress().getHostString(), this.getAddress().getPort());
    }
}

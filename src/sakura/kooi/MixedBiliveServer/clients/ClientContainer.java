package sakura.kooi.MixedBiliveServer.clients;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import sakura.kooi.MixedBiliveServer.Constants;
import sakura.kooi.MixedBiliveServer.SakuraBilive;
import sakura.kooi.MixedBiliveServer.utils.ClientConstructor;
import sakura.kooi.MixedBiliveServer.utils.ClientCounter;
import sakura.kooi.MixedBiliveServer.utils.ClientStatus;
import sakura.kooi.logger.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ClientContainer {
    @Getter
    private AtomicBoolean running = new AtomicBoolean(false);
    @Getter
    private AtomicReference<ClientStatus> clientStatus = new AtomicReference(ClientStatus.DISCONNECTED);
    @Getter @Setter(value = AccessLevel.PROTECTED)
    private int retried = 0;
    @Getter @Setter(value = AccessLevel.PROTECTED)
    private long nextConnectTime = -1L;
    @Getter @Setter(value = AccessLevel.PROTECTED)
    private long lastConnectedTime = -1L;

    @Getter @Setter(value = AccessLevel.PROTECTED)
    private IBroadcastSource client;
    @Getter @Setter(value = AccessLevel.PROTECTED)
    private String hostString;

    @Getter
    private ClientCounter counter = new ClientCounter();
    @Getter
    private AtomicLong hits = new AtomicLong(0L);

    @Getter
    private String name;
    @Getter
    private String tag;
    @Getter
    private Logger logger;
    private ClientConstructor<IBroadcastSource> constructor;

    public ClientContainer(String name, String tag, ClientConstructor<IBroadcastSource> constructor) {
        this.name = name;
        this.tag = " ["+tag+"]";
        this.constructor = constructor;
        logger = Logger.of(name+"-Client");
    }

    private void connect() throws IOException {
        running.set(true);
        try {
            client = constructor.create(this);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        clientStatus.set(ClientStatus.CONNECTING);
        client.connect();
    }

    public void disconnect() throws IOException {
        running.set(false);
        if (client != null)
            client.disconnect("断开连接");
    }

    protected void onConnected() {
        logger.info("成功连接至节点 {}", hostString);
        retried = 0;
        lastConnectedTime = System.currentTimeMillis();
        clientStatus.set(ClientStatus.CONNECTED);
    }

    protected void onDisconnected(String reason) {
        logger.warn("到节点 {} 的连接已断开 : {}", hostString, reason);
        if (running.get()) {
            retried++;
            this.doReconnect();
        } else {
            clientStatus.set(ClientStatus.DISCONNECTED);
        }

    }

    protected void onErrorOccurred(String reason, Exception e) {
        logger.errorEx("处理节点 {} 的连接时出错 : {}", e, hostString, reason);
    }

    protected void onLotteryReceived(String cmd, long id, long room, String type, String title, int time, int max_time, int time_wait) {
        title = title + tag;
        logger.info("源 {} -> {} {} #{}", hostString, room, title, id);
        counter.increment(cmd);
        if (SakuraBilive.rebroadcast(cmd, id, room, type, title, time, max_time, time_wait)) {
            hits.incrementAndGet();
        }
    }

    public void doReconnect() {
        clientStatus.set(ClientStatus.WAITING_RECONNECT);
        long waitTime = getWaitTime(retried);
        nextConnectTime = System.currentTimeMillis() + waitTime;
        SakuraBilive.getThreadPool().submit(() -> {
            try {
                Thread.sleep(waitTime);
                try {
                    logger.info("正在尝试连接至监听节点...");
                    connect();
                } catch (Exception e) {
                    if (e instanceof ConnectException) {
                        logger.error("连接监听节点失败, 稍后重试... {}", e.getMessage());
                    } else {
                        logger.error("连接监听节点失败, 稍后重试...", e);
                    }
                    retried++;
                    doReconnect();
                }
            } catch (InterruptedException e) {
                clientStatus.set(ClientStatus.DISCONNECTED);
                Thread.currentThread().interrupt();
            }
        });
    }

    private long getWaitTime(int retried) {
        if (retried == 0) return 0L;
        if (retried < 5) {
            return TimeUnit.SECONDS.toMillis(5);
        } else if (retried < 10) {
            return TimeUnit.MINUTES.toMillis(15);
        } else if (retried < 20) {
            return TimeUnit.MINUTES.toMillis(30);
        } else if (retried < 50) {
            return TimeUnit.HOURS.toMillis(1);
        } else if (retried < 70) {
            return TimeUnit.HOURS.toMillis(2);
        } else if (retried < 100) {
            return TimeUnit.HOURS.toMillis(4);
        } else if (retried < 200) {
            return TimeUnit.HOURS.toMillis(6);
        }
        return TimeUnit.HOURS.toMillis(12);
    }

    public void onPacketReceived(String packet) {
        logger.log(Constants.LOGLEVEL_PACKET, "Received packet {}", packet);
        try {
            client.processPacket(packet);
        } catch (Exception e) {
            logger.errorEx("处理数据包 {} 时发生了错误", e, packet);
        }
    }

    public void start() {
        if (true && !running.get()) {
            running.set(true);
            doReconnect();
        }
    }
}

package sakura.kooi.MixedBiliveServer.clients;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import sakura.kooi.MixedBiliveServer.Constants;
import sakura.kooi.MixedBiliveServer.SakuraBilive;
import sakura.kooi.MixedBiliveServer.utils.ClientConstructor;
import sakura.kooi.MixedBiliveServer.utils.ClientCounter;
import sakura.kooi.logger.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ClientContainer {
    @Getter @Setter(value = AccessLevel.PROTECTED)
    private AtomicBoolean connected = new AtomicBoolean(false);
    @Getter
    private AtomicBoolean running = new AtomicBoolean(false);
    @Getter
    private AtomicBoolean reconnectWaiting = new AtomicBoolean(false);
    @Getter @Setter(value = AccessLevel.PROTECTED)
    private int retried = 0;
    @Getter @Setter(value = AccessLevel.PROTECTED)
    private long nextConnect = -1L;
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
        connected.set(true);
    }

    protected void onDisconnected(String reason) {
        logger.warn("到节点 {} 的连接已断开 : {}", hostString, reason);
        connected.set(false);
        if (running.get() && !reconnectWaiting.get()) {
            retried++;
            this.doReconnect();
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
        reconnectWaiting.set(true);
        long waitTime = getWaitTime(retried);
        nextConnect = System.currentTimeMillis() + waitTime;
        SakuraBilive.getThreadPool().submit(() -> {
            try {
                Thread.sleep(waitTime);
                try {
                    reconnectWaiting.set(false);
                    logger.info("正在尝试连接至监听节点...");
                    connect();
                } catch (Exception e) {
                    logger.error("连接监听节点失败, 稍候重试...", e);
                    retried++;
                    doReconnect();
                }
            } catch (InterruptedException e) {
                reconnectWaiting.set(false);
                Thread.currentThread().interrupt();
            }
        });
    }

    private long getWaitTime(int retried) {
        if (retried == 0) return 0L;
        if (retried < 5) {
            return(TimeUnit.SECONDS.toMillis(5));
        } else if (retried < 10) {
            return(TimeUnit.MINUTES.toMillis(15));
        } else if (retried < 20) {
            return(TimeUnit.MINUTES.toMillis(30));
        }
        return(TimeUnit.HOURS.toMillis(1));
    }

    public void onPacketReceived(String packet) {
        logger.log(Constants.LOGLEVEL_PACKET, "Received packet {}", packet);
        try {
            client.processPacket(packet);
        } catch (Exception e) {
            logger.errorEx("处理数据包 {} 时发生了错误", e, packet);
        }
    }
}

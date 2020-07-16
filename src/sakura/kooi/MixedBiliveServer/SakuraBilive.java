package sakura.kooi.MixedBiliveServer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.fusesource.jansi.AnsiConsole;
import sakura.kooi.MixedBiliveServer.clients.*;
import sakura.kooi.MixedBiliveServer.commands.CommandHandler;
import sakura.kooi.MixedBiliveServer.utils.ClientCounter;
import sakura.kooi.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.text.DecimalFormat;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SakuraBilive {
    private static final Logger logger = Logger.of("Launcher");
    @Getter
    private static DecimalFormat numberFormat = (DecimalFormat) DecimalFormat.getInstance();
    @Getter
    private static Configuration configuration;
    @Getter
    private static BiliveServer biliveServer;

    @Getter
    private static ExecutorService threadPool = Executors.newCachedThreadPool();
    @Getter
    private static ExecutorService sentPool = Executors.newSingleThreadExecutor();

    @Getter
    private static LinkedHashSet<ClientContainer> clients = new LinkedHashSet<>();

    @Getter
    private static long startTime;
    @Getter
    private static AtomicLong lotteryMin = new AtomicLong(-1L);
    @Getter
    private static AtomicLong lotteryCurrent = new AtomicLong(-1L);
    @Getter
    private static AtomicLong caughtLottery = new AtomicLong(0L);
    @Getter
    private static ClientCounter counter = new ClientCounter();

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("running_in_idea"))
            AnsiConsole.systemInstall();
        //Logger.setLogLevelEnabled(LogLevel.TRACE, true);
        logger.info("Sakura bilive_server | 聚合抽奖监听服务器 | Powered by SakuraKooi");
        startTime = System.currentTimeMillis();
        numberFormat.setMaximumFractionDigits(4);
        logger.info("正在加载配置文件...");
        loadConfiguration();
        logger.info("正在启动Websocket服务器...");
        biliveServer = new BiliveServer(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 23388));
        biliveServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(SakuraBilive::shutdown));
        logger.info("正在连接监听服务器节点...");
        initializeClients();
        clients.forEach(ClientContainer::start);
        CommandHandler.listenCommand();
    }

    private static void loadConfiguration() {
        File configFile = new File("sakura_bilive.json");
        if (configFile.exists()) {

        } else {
            configuration = new Configuration();
        }
    }

    private static void saveConfiguation() {

    }

    private static void initializeClients() {
        clients.add(new ClientContainer("Vector000", "VECT", container -> new OfficialClient(new URI(Constants.Protocol.VECTOR_SERVER_ADDRESS), Constants.Protocol.VECTOR_SERVER_PROTOCOL, container)));
        clients.add(new ClientContainer("Yoki", "YOKI", container -> new YokiClient(new URI(Constants.Protocol.YOKI_SERVER_ADDRESS), container)));
        clients.add(new ClientContainer("BiliHelper", "BIHP", container -> new BiliHelperClient(container)));
        clients.add(new ClientContainer("Lzghzr", "LZGH", container -> new OfficialClient(new URI(Constants.Protocol.LZGHZR_SERVER_ADDRESS), Constants.Protocol.LZGHZR_SERVER_PROTOCOL, container)));
        clients.add(new ClientContainer("RaffleJS", "RFJS", container -> new RaffleJsClient(new URI(Constants.Protocol.RAFFLEJS_SERVER_ADDRESS), container)));
        clients.add(new ClientContainer("LiveTool", "LVTL", container -> new LiveToolClient(container)));
        clients.add(new ClientContainer("Lunmx", "LUMX", container -> new OfficialClient(new URI(Constants.Protocol.LUNMX_SERVER_ADDRESS), Constants.Protocol.LUNMX_SERVER_PROTOCOL, container)));
        clients.add(new ClientContainer("DawnBili", "DAWN", container -> new DawnClient(container)));
    }

    private static void shutdown() {
        clients.forEach(container -> {
            if (!container.getRunning().get()) return;
            container.getLogger().info("正在关闭到 {} 的监听连接", container.getHostString());
            try {
                container.disconnect();
            } catch (IOException e) {
                container.getLogger().errorEx("关闭到 {} 的监听连接出错", e, container.getHostString());
            }
        });
        logger.info("正在关闭线程池...");
        threadPool.shutdownNow();
        sentPool.shutdownNow();
        logger.info("正在停止服务器...");
        if (biliveServer != null)
            try {
                biliveServer.stop();
            } catch (IOException | InterruptedException e) {
                logger.error("关闭服务器时发生错误", e);
            }
        logger.info("服务器关闭");
    }

    private static final Cache<String, Boolean> lotteryCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

    public static boolean rebroadcast(String cmd, long id, long roomId, String type, String title, int time, int max_time, int time_wait) {
        String key = (cmd + id).toLowerCase();
        synchronized (lotteryCache) {
            if (lotteryCache.getIfPresent(key) == null) {
                lotteryCache.put(key, true);
                sentPool.submit(() -> biliveServer.broadcast(buildPacket(cmd, id, roomId, type, title, time, max_time, time_wait)));
                counter.increment(cmd);
                BiliveServer.logger.log(Constants.LOGLEVEL_SENT, "转发抽奖 -> 房间 {} 开启了 {} | #{} {} {}", roomId, title, id, cmd, type);
                if (cmd.equals("lottery")) {
                    caughtLottery.incrementAndGet();
                    if (lotteryMin.get() == -1) {
                        lotteryMin.set(id);
                    }
                    lotteryCurrent.set(id);
                }
                return true;
            }
            return false;
        }
    }

    private static String buildPacket(String cmd, long id, long roomId, String type, String title, int time, int max_time, int time_wait) {
        JsonObject json = new JsonObject();
        json.add("cmd", new JsonPrimitive(cmd));
        json.add("roomID", new JsonPrimitive(roomId));
        json.add("id", new JsonPrimitive(id));
        json.add("type", new JsonPrimitive(type));
        json.add("title", new JsonPrimitive(title));
        if (time != -1)
            json.add("time", new JsonPrimitive(time));
        if (max_time != -1)
            json.add("max_time", new JsonPrimitive(max_time));
        if (time_wait != -1)
            json.add("time_wait", new JsonPrimitive(time_wait));
        return Constants.GSON.toJson(json);
    }
}

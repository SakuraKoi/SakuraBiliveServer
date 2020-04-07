package sakura.kooi.MixedBiliveServer;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.fusesource.jansi.AnsiConsole;
import org.slf4j.helpers.MessageFormatter;
import sakura.kooi.MixedBiliveServer.clients.*;
import sakura.kooi.MixedBiliveServer.utils.ClientCounter;
import sakura.kooi.MixedBiliveServer.utils.FishingDetection;
import sakura.kooi.MixedBiliveServer.utils.TimeUtils;
import sakura.kooi.logger.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SakuraBilive {
    private static final Logger logger = Logger.of("Launcher");
    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance();
    @Getter
    private static BiliveServer biliveServer;
    @Getter
    private static ExecutorService threadPool = Executors.newCachedThreadPool();
    @Getter
    private static ExecutorService sentPool = Executors.newSingleThreadExecutor();
    private static LinkedHashSet<ClientContainer> clients = new LinkedHashSet<>();

    private static long startTime;
    private static AtomicLong lotteryMin = new AtomicLong(-1L);
    private static AtomicLong lotteryCurrent = new AtomicLong(-1L);
    private static AtomicLong caughtLottery = new AtomicLong(0L);
    private static ClientCounter counter = new ClientCounter();

    public static void main(String[] args) throws Exception {
       // logger.setLogLevelEnabled(LogLevel.TRACE, true);
        if (!Boolean.getBoolean("running_in_idea"))
            AnsiConsole.systemInstall();

        logger.info("Sakura bilive_server | 聚合抽奖监听服务器 | Powered by SakuraKooi");
        startTime = System.currentTimeMillis();
        nf.setMaximumFractionDigits(4);
        logger.info("正在启动Websocket服务器...");
        biliveServer = new BiliveServer(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 23388));
        biliveServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(SakuraBilive::shutdown));
        logger.info("正在连接监听服务器节点...");
        initializeClients();
        clients.forEach(ClientContainer::doReconnect);
        try {
            Scanner scn = new Scanner(System.in);
            while (scn.hasNextLine()) {
                String ln = scn.nextLine();
                if ("stop".equalsIgnoreCase(ln)) break;
                if ("status".equalsIgnoreCase(ln)) {
                    try {
                        printStatus();
                    } catch (Exception e) {
                        logger.error("打印报告时出现错误", e);
                    }
                } else if ("reset".equalsIgnoreCase(ln)) {
                    counter.reset();
                    lotteryMin.set(-1L);
                    lotteryCurrent.set(-1L);
                    caughtLottery.set(0L);
                    clients.forEach(client -> {
                        client.getHits().set(0);
                        client.getCounter().reset();
                    });
                    logger.info("成功重置抽奖计数");
                }
            }
            scn.close();
            Runtime.getRuntime().exit(0);
        } catch (Exception e) {
            logger.error("监听键盘输入时出现错误, 日志报告功能关闭", e);
        }
    }

    private static void initializeClients() {
        clients.add(new ClientContainer("RaffleJS", "RFJS", container -> new RaffleJsClient(new URI(Constants.RAFLEJS_SERVER_ADDRESS), container)));
        clients.add(new ClientContainer("Lzghzr", "LZGH",container -> new OfficialClient(new URI(Constants.LZGHZR_SERVER_ADDRESS), Constants.LZGHZR_SERVER_PROTOCOL, container)));
        clients.add(new ClientContainer("Vector000", "VECT", container -> new OfficialClient(new URI(Constants.VECTOR_SERVER_ADDRESS), Constants.VECTOR_SERVER_PROTOCOL, container)));
        clients.add(new ClientContainer("Yoki", "YOKI",container -> new YokiClient(new URI(Constants.YOKI_SERVER_ADDRESS), container)));
        clients.add(new ClientContainer("BiliHelper", "BIHP", container -> new BiliHelperClient(container)));
    }

    private static final String reportFormat =
                    "---------------------------------------------------------------------------------------------------------\n" +
                    " 监听服务器于 {} 启动\n" +
                    " 当前在线客户端 {} 个 识别到 {}/{} 个钓鱼房间\n" +
                    " 舰队抽奖统计: 漏抽 {} / 推送 {} / 理论 {} (漏抽率 {}%)\n" +
                    " 共监听到 {}\n" +
                    "---------------------------------------------------------------------------------------------------------\n" +
                    "{}" +
                    "---------------------------------------------------------------------------------------------------------\n" +
                    "{}" +
                    "---------------------------------------------------------------------------------------------------------\n";
    private static void printStatus() {
        long totalLottery = lotteryMin.get() == -1 ? 0 : lotteryCurrent.get() - lotteryMin.get() + 1;
        long missLottery = totalLottery - caughtLottery.get();
        logger.info(MessageFormatter.arrayFormat(reportFormat, new Object[]{
                df.format(startTime),
                BiliveServer.currentOnline.get(),
                FishingDetection.countFishing(),
                FishingDetection.countTotal(),
                missLottery,
                caughtLottery,
                totalLottery,
                totalLottery == 0 ? 0 : nf.format((missLottery / (float)totalLottery)*100),
                counter.report(),
                createLotteryReport(),
                createStatusReport(),
        }).getMessage());
    }

    private static String createStatusReport() {
        StringBuilder sb = new StringBuilder();

        boolean sw = true;
        for (ClientContainer container : clients) {
            StringBuilder ln = new StringBuilder();
            ln.append(' ').append(Strings.padEnd(container.getName(), 12, ' '));
            if (container.getConnected().get()) {
                ln.append("已连接");
            } else if (container.getNextConnect() == -1L) {
                ln.append("未连接");
            } else {
                ln.append("断开");
            }
            ln.append(' ');
            if (container.getConnected().get()) {
                ln.append(TimeUtils.millisToShortDHMS(System.currentTimeMillis() - container.getNextConnect()));
            } else {
                ln.append("已重试 ").append(container.getRetried()).append(" 次 ")
                        .append("下次连接于 ").append(TimeUtils.millisToShortDHMS(container.getNextConnect() - System.currentTimeMillis())).append(" 后");
            }
            if (sw) {
                sb.append(Strings.padEnd(ln.toString(), 51, ' '));
                sb.append(" | ");
            } else {
                ln.append('\n');
                sb.append(ln);
            }
            sw = !sw;
        }
        if (!sw) {
            sb.delete(sb.length()-3, sb.length());
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String createLotteryReport() {
        StringBuilder sb = new StringBuilder();
        for (ClientContainer container : clients) {
            sb.append(' ').append(Strings.padEnd(container.getName(), 12, ' '))
                    .append(Strings.padEnd("命中 "+container.getHits().get()+" 次", 11, ' '))
                    .append(" | 监听到 ").append(container.getCounter().report())
                    .append('\n');
        }
        return sb.toString();
    }

    private static void shutdown() {
        clients.forEach(container -> {
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
    private static final Cache<String, Boolean> lotteryCache =  CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();
    public static boolean rebroadcast(String cmd, long id, long roomId, String type, String title, int time, int max_time, int time_wait) {
        String key = (cmd+id).toLowerCase();
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

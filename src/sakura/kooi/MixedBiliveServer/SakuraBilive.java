package sakura.kooi.MixedBiliveServer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.fusesource.jansi.AnsiConsole;
import org.slf4j.helpers.MessageFormatter;
import sakura.kooi.MixedBiliveServer.clients.BiliHelperClient;
import sakura.kooi.MixedBiliveServer.clients.OfficialClient;
import sakura.kooi.MixedBiliveServer.clients.YokiClient;
import sakura.kooi.MixedBiliveServer.utils.ClientCounter;
import sakura.kooi.logger.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    private static OfficialClient officialClient;
    private static YokiClient yokiClient;
    private static BiliHelperClient biliHelperClient;

    private static long startTime;
    private static long lotteryMin = -1L;
    private static long lotteryCurrent = -1L;
    private static long caughtLottery = 0L;
    private static ClientCounter counter = new ClientCounter();

    public static void main(String[] args) throws Exception {
        //logger.setLogLevelEnabled(Constants.LOGLEVEL_PACKET, true);
        AnsiConsole.systemInstall();
        logger.info("Sakura bilive_server | 聚合抽奖监听服务器 | Powered by SakuraKooi");
        startTime = System.currentTimeMillis();
        nf.setMaximumFractionDigits(4);
        logger.info("正在启动Websocket服务器...");
        biliveServer = new BiliveServer(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 23388));
        biliveServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(SakuraBilive::shutdown));
        logger.info("正在连接监听服务器节点...");
        reconnectOfficialClient(0);
        reconnectYokiClient(0);
        reconnectBiliHelperClient(0);
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
                }
            }
            scn.close();
            Runtime.getRuntime().exit(0);
        } catch (Exception e) {
            logger.error("监听键盘输入时出现错误, 日志报告功能关闭", e);
        }
    }

    private static final String reportFormat =
                    "----------------------------------------------------------------\n" +
                    " 监听服务器于 {} 启动\n" +
                    " 当前在线客户端 {} 个\n" +
                    " 舰队抽奖统计: 漏抽 {} / 推送 {} / 理论 {} (漏抽率 {}%)\n" +
                    " 共监听到 {}\n" +
                    "----------------------------------------------------------------\n" +
                    " Vector000  监听到 {}\n" +
                    " Yoki       监听到 {}\n" +
                    " BiliHelper 监听到 {}\n" +
                    "----------------------------------------------------------------\n";
    private static void printStatus() {
        long totalLottery = lotteryMin == -1 ? 0 : lotteryCurrent == lotteryMin ? 1 : lotteryCurrent - lotteryMin;
        long missLottery = totalLottery - caughtLottery;
        logger.info(MessageFormatter.arrayFormat(reportFormat, new Object[]{
                df.format(startTime),
                BiliveServer.currentOnline.get(),
                missLottery,
                caughtLottery,
                totalLottery,
                totalLottery == 0 ? 0 : nf.format((missLottery / (float)totalLottery)*100),
                counter.report(),
                OfficialClient.getCounter().report(),
                YokiClient.getCounter().report(),
                BiliHelperClient.getCounter().report()
        }).getMessage());
    }

    public static void reconnectBiliHelperClient(int retry) {
        threadPool.submit(() -> {
            try {
                retrySleep(retry);
                try {
                    BiliHelperClient.logger.info("正在尝试连接至BiliHelper监听服务器...");
                    biliHelperClient = new BiliHelperClient(retry);
                    biliHelperClient.connect();
                } catch (Exception e) {
                    BiliHelperClient.logger.error("连接监听服务器失败, 稍候重试...", e);
                    reconnectBiliHelperClient(retry+1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void reconnectYokiClient(int retry) {
        threadPool.submit(() -> {
            try {
                retrySleep(retry);
                try {
                    YokiClient.logger.info("正在尝试连接至Yoki监听服务器...");
                    yokiClient = new YokiClient(new URI(Constants.YOKI_SERVER_ADDRESS), retry);
                    yokiClient.connect();
                } catch (Exception e) {
                    YokiClient.logger.error("连接监听服务器失败, 稍候重试...", e);
                    reconnectYokiClient(retry+1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void reconnectOfficialClient(int retry) {
        threadPool.submit(() -> {
            try {
                retrySleep(retry);
                try {
                    OfficialClient.logger.info("正在尝试连接至Vector000监听服务器...");
                    officialClient = new OfficialClient(new URI(Constants.BILIVE_OFFICIAL_SERVER_ADDRESS), Constants.BILIVE_OFFICIAL_SERVER_PROTOCOL, retry);
                    officialClient.connect();
                } catch (Exception e) {
                    OfficialClient.logger.error("连接监听服务器失败, 稍候重试...", e);
                    reconnectYokiClient(retry+1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static void retrySleep(int retry) throws InterruptedException {
        if (retry == 0) return;
        if (retry < 5) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        } else if (retry < 10) {
            Thread.sleep(TimeUnit.MINUTES.toMillis(15));
        } else {
            Thread.sleep(TimeUnit.HOURS.toMillis(60));
        }
    }

    private static void shutdown() {
        if (officialClient != null) {
            logger.info("正在断开Vector000监听客户端...");
            officialClient.shutdown();
        }
        if (yokiClient != null) {
            logger.info("正在断开Yoki监听客户端...");
            yokiClient.shutdown();
        }
        if (biliHelperClient != null) {
            logger.info("正在断开BiliHelper监听客户端...");
            try {
                biliHelperClient.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
    private static final Cache<String, Boolean> lotteryCache =  CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build();
    public static void rebroadcast(String cmd, long id, long roomId, String type, String title, int time, int max_time, int time_wait) {
        String key = (cmd+id).toLowerCase();
        synchronized (lotteryCache) {
            if (lotteryCache.getIfPresent(key) == null) {
                lotteryCache.put(key, true);
                sentPool.submit(() -> biliveServer.broadcast(buildPacket(cmd, id, roomId, type, title, time, max_time, time_wait)));
                counter.increment(cmd);
                BiliveServer.logger.log(Constants.LOGLEVEL_SENT, "转发抽奖 -> 房间 {} 开启了 {} | #{} {} {}", roomId, title, id, cmd, type);
                if (cmd.equals("lottery")) {
                    caughtLottery++;
                    if (lotteryMin == -1) {
                        lotteryMin = id;
                        lotteryCurrent = id;
                        return;
                    }
                    lotteryCurrent = id;
                }
            }
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

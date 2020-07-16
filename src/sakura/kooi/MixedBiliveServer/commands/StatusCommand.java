package sakura.kooi.MixedBiliveServer.commands;

import com.google.common.base.Strings;
import org.slf4j.helpers.MessageFormatter;
import sakura.kooi.MixedBiliveServer.BiliveServer;
import sakura.kooi.MixedBiliveServer.SakuraBilive;
import sakura.kooi.MixedBiliveServer.clients.ClientContainer;
import sakura.kooi.MixedBiliveServer.utils.ClientStatus;
import sakura.kooi.MixedBiliveServer.utils.FishingDetection;
import sakura.kooi.MixedBiliveServer.utils.TimeUtils;

public class StatusCommand {
    private static final String reportFormat =
                    "-----------------------------------------------------------------------------------------------\n" +
                    " 监听服务器于 {} 启动\n" +
                    " 当前在线客户端 {} 个 识别到 {}/{} 个钓鱼房间\n" +
                    " 舰队抽奖统计: 漏抽 {} / 推送 {} / 理论 {} (漏抽率 {}%)\n" +
                    " 共监听到 {}\n" +
                    "-----------------------------------------------------------------------------------------------\n" +
                    "{}" +
                    "-----------------------------------------------------------------------------------------------\n" +
                    "{}" +
                    "-----------------------------------------------------------------------------------------------\n";

    static void execute() {
                        long totalLottery = SakuraBilive.getLotteryMin().get() == -1 ? 0 : SakuraBilive.getLotteryCurrent().get() - SakuraBilive.getLotteryMin().get() + 1;
                        long missLottery = totalLottery - SakuraBilive.getCaughtLottery().get();
                        CommandHandler.logger.info(MessageFormatter.arrayFormat(reportFormat, new Object[]{
                                CommandHandler.df.format(SakuraBilive.getStartTime()),
                                BiliveServer.currentOnline.get(),
                                FishingDetection.countFishing(),
                                FishingDetection.countTotal(),
                                missLottery,
                                SakuraBilive.getCaughtLottery(),
                                totalLottery,
                                totalLottery == 0 ? 0 : SakuraBilive.getNumberFormat().format((missLottery / (float)totalLottery)*100),
                                SakuraBilive.getCounter().report(),
                                createLotteryReport(),
                                createStatusReport(),
                        }).getMessage());
                    }

    private static String createStatusReport() {
        StringBuilder sb = new StringBuilder();
        for (ClientContainer container : SakuraBilive.getClients()) {
            if (!container.getRunning().get()) continue;
            sb.append(' ').append(Strings.padEnd(container.getName(), 12, ' '));
            if (container.getClientStatus().get() == ClientStatus.CONNECTED) {
                sb.append("连接 ").append(TimeUtils.millisToShortDHMS(System.currentTimeMillis() - container.getNextConnectTime()));
            } else {
                sb.append("断开 ").append("x").append(container.getRetried());
                if (container.getClientStatus().get() == ClientStatus.CONNECTING) {
                    sb.append(" 正在连接中");
                } else {
                    sb.append(" 将于 ")
                            .append(TimeUtils.millisToShortDHMS(container.getNextConnectTime() - System.currentTimeMillis()))
                            .append(" 后重试");
                }
                sb.append(", 上次连接于 ").append(CommandHandler.df.format(container.getLastConnectedTime()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String createLotteryReport() {
        StringBuilder sb = new StringBuilder();
        for (ClientContainer container : SakuraBilive.getClients()) {
            sb.append(' ').append(Strings.padEnd(container.getName(), 12, ' '))
                    .append(Strings.padEnd("命中 "+container.getHits().get()+" 次", 11, ' '))
                    .append(" | 监听到 ").append(container.getCounter().report())
                    .append('\n');
        }
        return sb.toString();
    }
}

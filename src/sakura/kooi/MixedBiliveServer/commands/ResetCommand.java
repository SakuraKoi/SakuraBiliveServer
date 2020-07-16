package sakura.kooi.MixedBiliveServer.commands;

import sakura.kooi.MixedBiliveServer.SakuraBilive;

public class ResetCommand {
    static void execute() {
        SakuraBilive.getCounter().reset();
        SakuraBilive.getLotteryMin().set(-1L);
        SakuraBilive.getLotteryCurrent().set(-1L);
        SakuraBilive.getCaughtLottery().set(0L);
        SakuraBilive.getClients().forEach(client -> {
            client.getHits().set(0);
            client.getCounter().reset();
        });
        CommandHandler.logger.info("成功重置抽奖计数");
    }
}

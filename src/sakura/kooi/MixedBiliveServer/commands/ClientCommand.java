package sakura.kooi.MixedBiliveServer.commands;

import org.slf4j.helpers.MessageFormatter;
import sakura.kooi.MixedBiliveServer.BiliveServer;

public class ClientCommand {
    private static final String clientFormat =
                    "-------------- 当前在线客户端 {} 个 --------------\n" +
                    "{}" +
                    "--------------------------------------------------\n";

    static void execute() {
                        CommandHandler.logger.info(MessageFormatter.arrayFormat(clientFormat, new Object[]{
                                BiliveServer.currentOnline.get(),
                                createClientList()
                        }).getMessage());
                    }

    private static String createClientList() {
        StringBuilder sb = new StringBuilder();
        for (String client : BiliveServer.addressHashMap.values()) {
            sb.append(' ').append(client).append('\n');
        }
        return sb.toString();
    }
}

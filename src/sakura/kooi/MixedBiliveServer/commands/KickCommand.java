package sakura.kooi.MixedBiliveServer.commands;

import org.java_websocket.WebSocket;
import sakura.kooi.MixedBiliveServer.BiliveServer;

public class KickCommand {
    static void execute(String[] commands) {
        if (commands.length>1) {
            String client = commands[1];
            String reason = "未提供原因";
            if(commands.length>2) {
                reason = commands[2];
            }
            WebSocket webSocket = BiliveServer.addressHashMap.inverse().get(client);
            if (webSocket != null) {
                webSocket.send("{\"cmd\":\"sysmsg\",\"msg\":\"您被管理员踢出 - "+reason+"\"}");
                webSocket.close();
                CommandHandler.logger.success("客户端 {} 已被踢出: {}", client, reason);
            } else {
                CommandHandler.logger.error("客户端 {} 不在线", client);
            }
        } else {
            CommandHandler.logger.info("Usage: kick <client> [reason]");
        }
    }
}

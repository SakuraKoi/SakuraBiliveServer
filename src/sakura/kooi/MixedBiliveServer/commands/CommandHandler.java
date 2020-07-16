package sakura.kooi.MixedBiliveServer.commands;

import sakura.kooi.logger.Logger;

import java.text.SimpleDateFormat;
import java.util.Scanner;

public class CommandHandler {
    protected static Logger logger = Logger.of("Command");
    protected static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void listenCommand() {
        try {
            Scanner scn = new Scanner(System.in);
            while (scn.hasNextLine()) {
                String ln = scn.nextLine();
                try {
                    executeCommand(ln.split(" "));
                } catch (Exception e) {
                    logger.errorEx("处理命令 {} 时出现错误", e, ln);
                }
            }
            scn.close();
        } catch (Exception e) {
            logger.error("监听键盘输入时出现错误, 命令处理关闭", e);
        }
    }

    private static void executeCommand(String[] commands) {
        switch(commands[0].toLowerCase()) {
            case "stop": {
                Runtime.getRuntime().exit(0);
                break;
            }
            case "status": {
                StatusCommand.execute();
                break;
            }
            case "reset": {
                ResetCommand.execute();
                break;
            }
            case "clients": {
                ClientCommand.execute();
                break;
            }
            case "kick": {
                KickCommand.execute(commands);
                break;
            }
        }
    }
}

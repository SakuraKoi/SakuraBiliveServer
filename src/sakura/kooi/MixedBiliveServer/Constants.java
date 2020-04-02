package sakura.kooi.MixedBiliveServer;

import com.google.gson.Gson;
import sakura.kooi.logger.LogLevel;

import java.util.Arrays;
import java.util.List;

public class Constants {
    public static final String BILIVE_OFFICIAL_SERVER_ADDRESS = "ws://47.101.153.223:20080/";
    public static final String BILIVE_OFFICIAL_SERVER_PROTOCOL = "ff5f0db2548baecbcd21c7a50ece57a3";
    public static final List<String> WHITELISTED_COMMANDS = Arrays.asList("sysmsg", "lottery", "beatstorm", "raffle", "pklottery");

    public static final String YOKI_SERVER_ADDRESS = "ws://north.ip.iruiyu.cn:9017/";
    public static final String YOKI_TOKEN_SERVER = "http://101.201.64.44/wstokenget";

    public static final String BILI_HELPER_SERVER_HOST = "47.102.120.84";
    public static final int BILI_HELPER_SERVER_PORT = 10010;
    public static final String BILI_HELPER_SERVER_KEY = ",*(?PVl]nIbo35sB";

    public static final LogLevel LOGLEVEL_SENT = new LogLevel("SENT", "§6", true);
    public static final LogLevel LOGLEVEL_MISS = new LogLevel("MISS", "§5", true);
    public static final Gson GSON = new Gson();
    public static final LogLevel LOGLEVEL_PACKET = new LogLevel("PACKET", "§d", false);
}

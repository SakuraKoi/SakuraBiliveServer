package sakura.kooi.MixedBiliveServer;

import com.google.gson.Gson;
import sakura.kooi.logger.LogLevel;

import java.util.Arrays;
import java.util.List;

public final class Constants {
    public static final List<String> WHITELISTED_COMMANDS = Arrays.asList("sysmsg", "lottery", "beatstorm", "raffle", "pklottery");
    public static final LogLevel LOGLEVEL_SENT = new LogLevel("SENT", "§6", true);
    public static final LogLevel LOGLEVEL_PACKET = new LogLevel("PACKET", "§d", false);
    public static final Gson GSON = new Gson();

    public static final class Client {
        public static final boolean YOKI_CLIENT_ENABLED = true;
        public static final boolean LZGHZR_CLIENT_ENABLED = true;
        public static final boolean RAFFLEJS_CLIENT_ENABLED = true;
        public static final boolean LIVE_TOOL_CLIENT_ENABLED = true; // bad
        public static final boolean BILI_HELPER_CLIENT_ENABLED = true; // no longer works
        public static final boolean VECTOR_CLIENT_ENABLED = true; // no longer works
        public static final boolean LUNMX_CLIENT_ENABLED = true; // no longer works
        public static final boolean DAWN_CLIENT_ENABLED = true;
    }

    public static final class Protocol {
        public static final String VECTOR_SERVER_ADDRESS = "ws://47.101.153.223:20080/";
        public static final String VECTOR_SERVER_PROTOCOL = "ff5f0db2548baecbcd21c7a50ece57a3";

        public static final String YOKI_SERVER_ADDRESS = "ws://north.ip.iruiyu.cn:9017/";
        public static final String YOKI_TOKEN_SERVER = "http://101.201.64.44/wstokenget";

        public static final String BILI_HELPER_SERVER_HOST = "47.102.120.84";
        public static final int BILI_HELPER_SERVER_PORT = 10010;
        public static final String BILI_HELPER_SERVER_KEY = ",*(?PVl]nIbo35sB";

        public static final String LZGHZR_SERVER_ADDRESS = "wss://bilive.halaal.win/server/";
        public static final String LZGHZR_SERVER_PROTOCOL = "164c48292a6c773c85716093589cd60e";

        public static final String RAFFLEJS_SERVER_ADDRESS = "ws://bili.minamiktr.com/ws";

        public static final String LIVE_TOOL_URL = "http://118.25.108.153:8080/guard";

        public static final String LUNMX_SERVER_ADDRESS = "ws://xuyaoyao.love:20080/";
        public static final String LUNMX_SERVER_PROTOCOL = "7b276a261c754d49f8f601fb32ddbb72";

        public static final String DAWN_SERVER_HOST = "139.9.83.34";
        public static final int DAWN_SERVER_PORT = 12344;
        public static final String DAWN_SERVER_KEY = "root";
    }

}

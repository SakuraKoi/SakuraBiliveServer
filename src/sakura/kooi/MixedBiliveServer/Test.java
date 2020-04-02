package sakura.kooi.MixedBiliveServer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.commons.codec.binary.Hex;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

public class Test {
    private static Gson gson = new Gson();
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(InetAddress.getByName(Constants.BILI_HELPER_SERVER_HOST), Constants.BILI_HELPER_SERVER_PORT));
        // https://github.com/lkeme/BiliHelper-personal/blob/master/src/plugin/AloneTcpClient.php
        socket.getOutputStream().write(pack(loginPacket(Constants.BILI_HELPER_SERVER_KEY)));
        socket.getOutputStream().write(pack(""));
        int length;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    socket.getOutputStream().write(pack(""));
                    System.out.println("sent heart beat");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 10000, 10000);
        InputStream is = socket.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        DataInputStream dis = new DataInputStream(bis);
        while((length = dis.readInt()) != -1) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream daos = new DataOutputStream(baos);
            for (int i = 0; i < length; i++)
                daos.write(dis.read());
            System.out.println(new String(baos.toByteArray()));
        }
    }

    private static byte[] pack(String data) {
        char[] array = data.toCharArray();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream daos = new DataOutputStream(baos);
        try {
            daos.writeInt(array.length);
            for (char c : array) {
                daos.write(c);
            }
        } catch (IOException e) {
            throw new AssertionError("Should not happen");
        }
        return baos.toByteArray();
    }

    private static String loginPacket(String key) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("code", new JsonPrimitive(0));
        jsonObject.add("type", new JsonPrimitive("ask"));
        JsonObject keyObj = new JsonObject();
        keyObj.add("key", new JsonPrimitive(key));
        jsonObject.add("data", keyObj);
        return gson.toJson(jsonObject);
    }
}

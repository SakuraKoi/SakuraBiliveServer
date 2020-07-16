package sakura.kooi.MixedBiliveServer.clients;

import com.google.gson.*;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import sakura.kooi.MixedBiliveServer.Constants;
import sakura.kooi.MixedBiliveServer.SakuraBilive;
import sakura.kooi.MixedBiliveServer.utils.ClientStatus;
import sakura.kooi.MixedBiliveServer.utils.FishingDetection;

import java.util.*;

public class LiveToolClient implements IBroadcastSource {
    private ClientContainer container;
    private Thread listenThread;
    private final long QUERY_SLEEP = 60000;
    private final long SUMBIT_DELAY = 1000;
    private LinkedList<Long> gotIds = new LinkedList<>();
    public LiveToolClient(ClientContainer container) {
        this.container = container;
        listenThread = new Thread(this::getGuardList);
        container.setHostString(Constants.Protocol.LIVE_TOOL_URL);
    }

    @Override
    public void connect() {
        listenThread.start();
        container.onConnected();
        container.getClientStatus().set(ClientStatus.WAITING_RECONNECT);
    }

    @Override
    public void disconnect(String reason) {
        listenThread.interrupt();
        container.onDisconnected(reason);
    }

    private void getGuardList() {
        while(container.getRunning().get()) {
            try {
                container.setNextConnectTime(System.currentTimeMillis() + QUERY_SLEEP);
                Thread.sleep(QUERY_SLEEP);
                if (SakuraBilive.getLotteryMin().get() != -1L) {
                    HttpResponse<String> response = Unirest.get(Constants.Protocol.LIVE_TOOL_URL).header("User-Agent", "bilibili-live-tools/23333").asString();
                    if (response.getStatus() == 200) {
                        try {
                            String body = response.getBody();
                            if (body == null) continue;
                            JsonElement element = JsonParser.parseString(body);
                            if (element.isJsonObject()) {
                                container.getLogger().warn("请求舰队列表出错 服务器返回 {}", response.getBody());
                            } else if (element.isJsonArray()) {
                                JsonArray array = element.getAsJsonArray();
                                processRaffleList(array);
                            }
                        } catch (JsonSyntaxException e) {
                            container.getLogger().warn("请求舰队列表出错 服务器返回 {}", response.getBody());
                        }
                    } else {
                        container.getLogger().warn("请求舰队列表出错 服务器返回 HTTP {}", response.getStatus());
                    }
                    container.setRetried(container.getRetried()+1);
                    container.setLastConnectedTime(System.currentTimeMillis());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (UnirestException e) {
                container.getLogger().error("请求舰队列表出错", e);
            }
        }
    }

    private void processRaffleList(JsonArray array) {
        ArrayList<JsonObject> raffleList = new ArrayList<>();
        for (JsonElement entry : array) {
            if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                long id = object.get("Id").getAsLong();
                if (SakuraBilive.getLotteryMin().get() > id || gotIds.contains(id)) continue;
                raffleList.add(object);
                gotIds.add(id);
            }
        }
        raffleList.sort(Comparator.comparingLong(self -> self.get("Id").getAsLong()));
        container.getLogger().info("本次轮询获得 {} 个舰队抽奖, 正在投入队列...", raffleList.size());
        if (!raffleList.isEmpty()) {
            SakuraBilive.getThreadPool().submit(() -> {
                for (JsonObject entry : raffleList) {
                    container.onPacketReceived(Constants.GSON.toJson(entry));
                    try {
                        Thread.sleep(SUMBIT_DELAY);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                while (gotIds.size() > 10000) gotIds.removeFirst();
            });
        }
    }

    @Override
    public void processPacket(String packet) {
        // {"EndTime":1586259298,"Id":2311380,"MasterId":405281725,"MasterName":"呆萌加加","RoomId":21305876,"Sender":"猛男温温","SenderId":85733135,"Time":1586258098,"Type":3}
        JsonObject json = JsonParser.parseString(packet).getAsJsonObject();
        long room = json.get("RoomId").getAsLong();
        long id = json.get("Id").getAsLong();
        String title = "舰队类型"+json.get("Type").getAsInt();
        SakuraBilive.getThreadPool().submit(() -> {
            if (FishingDetection.isFishingRoom(room)) {
                container.getLogger().warn("丢弃钓鱼抽奖 {} {} #{}", room, title, id);
                return;
            }
            container.onLotteryReceived("lottery", id, room, "guard", title, 1200, -1, -1);
        });
    }
}

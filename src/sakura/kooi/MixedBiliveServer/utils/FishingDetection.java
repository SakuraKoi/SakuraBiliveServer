package sakura.kooi.MixedBiliveServer.utils;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import sakura.kooi.MixedBiliveServer.entity.RoomInfoEntity;
import sakura.kooi.logger.Logger;

import java.util.HashMap;

public class FishingDetection {
    private static Logger logger = Logger.of("FishingDetection");
    private static HashMap<Long, Boolean> fishingRoom = new HashMap<>();
    public static boolean isFishingRoom(long room) {
        if (fishingRoom.containsKey(room)) return fishingRoom.get(room);
        try {
            HttpResponse<RoomInfoEntity> response = Unirest.get("https://api.live.bilibili.com/room/v1/Room/room_init?id=" + room).asObject(RoomInfoEntity.class);
            if (response.getStatus() != 200) {
                logger.warn("检测钓鱼房间时Bilibili API返回 HTTP {}", response.getStatus());
                return true;
            }
            RoomInfoEntity entity = response.getBody();
            if (entity.code != 0) {
                logger.warn("检测钓鱼房间时Bilibili API返回 {} {}", entity.code, entity.message);
                return true;
            }
            if (entity.data.isHidden || entity.data.isLocked || entity.data.encrypted) {
                fishingRoom.put(room, true);
                return true;
            }
            fishingRoom.put(room, false);
            return false;
        } catch (Exception e) {
            logger.error("检测钓鱼房间时出错", e);
            return true;
        }
    }
    public static long countFishing() {
        return fishingRoom.values().stream().filter(aBoolean -> !aBoolean).count();
    }

    public static long countTotal() {
        return fishingRoom.size();
    }
}

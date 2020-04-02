package sakura.kooi.MixedBiliveServer.entity;

import com.google.gson.annotations.SerializedName;

public class RoomInfoEntity {

    /**
     * code : 0
     * msg : ok
     * message : ok
     * data : {"room_id":5440,"short_id":1,"uid":9617619,"need_p2p":0,"is_hidden":false,"is_locked":false,"is_portrait":false,"live_status":2,"hidden_till":0,"lock_till":0,"encrypted":false,"pwd_verified":false,"live_time":-62170012800,"room_shield":1,"is_sp":0,"special_type":0}
     */

    @SerializedName("code")
    public int code;
    @SerializedName("msg")
    public String msg;
    @SerializedName("message")
    public String message;
    @SerializedName("data")
    public DataBean data;

    public static class DataBean {
        /**
         * room_id : 5440
         * short_id : 1
         * uid : 9617619
         * need_p2p : 0
         * is_hidden : false
         * is_locked : false
         * is_portrait : false
         * live_status : 2
         * hidden_till : 0
         * lock_till : 0
         * encrypted : false
         * pwd_verified : false
         * live_time : -62170012800
         * room_shield : 1
         * is_sp : 0
         * special_type : 0
         */

        @SerializedName("room_id")
        public int roomId;
        @SerializedName("short_id")
        public int shortId;
        @SerializedName("uid")
        public int uid;
        @SerializedName("need_p2p")
        public int needP2p;
        @SerializedName("is_hidden")
        public boolean isHidden;
        @SerializedName("is_locked")
        public boolean isLocked;
        @SerializedName("is_portrait")
        public boolean isPortrait;
        @SerializedName("live_status")
        public int liveStatus;
        @SerializedName("hidden_till")
        public int hiddenTill;
        @SerializedName("lock_till")
        public int lockTill;
        @SerializedName("encrypted")
        public boolean encrypted;
        @SerializedName("pwd_verified")
        public boolean pwdVerified;
        @SerializedName("live_time")
        public long liveTime;
        @SerializedName("room_shield")
        public int roomShield;
        @SerializedName("is_sp")
        public int isSp;
        @SerializedName("special_type")
        public int specialType;
    }
}

package com.muedsa.bilibililiveapiclient;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.muedsa.bilibililiveapiclient.model.BilibiliResponse;
import com.muedsa.bilibililiveapiclient.model.DanmuInfo;
import com.muedsa.bilibililiveapiclient.model.chat.ChatBroadcast;
import com.muedsa.bilibililiveapiclient.uitl.ChatBroadcastPacketUtil;
import com.muedsa.httpjsonclient.HttpJsonClient;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

public class ChatBroadcastWsClient {

    private long roomId;

    private String token;

    private WebSocketClient webSocketClient;

    private Timer heartTimer;

    private CallBack callBack;

    public ChatBroadcastWsClient(long roomId, String token){
        this.roomId = roomId;
        this.token = token;
        webSocketClient = new WebSocketClient(URI.create(ApiUrlContainer.WS_CHAT), new Draft_6455()) {
            @Override
            public void onOpen(ServerHandshake handshakeData) {
                if(callBack != null) callBack.onStart();
            }

            @Override
            public void onMessage(ByteBuffer byteBuffer) {
                if(callBack != null){
                    List<String> msgList = ChatBroadcastPacketUtil.decode(byteBuffer, null);
                    if(!msgList.isEmpty()){
                        for (String msg : msgList) {
                            try{
                                JSONObject jsonObject = JSON.parseObject(msg);
                                String cmd = jsonObject.getString("cmd");
                                if(ChatBroadcast.CMD_DANMU_MSG.equals(cmd)){
                                    JSONArray infoJsonArray = jsonObject.getJSONArray("info");
                                    JSONArray propertyJsonArray = infoJsonArray.getJSONArray(0);
                                    float textSize = propertyJsonArray.getFloatValue(2);
                                    int textColor = (int) (0x00000000ff000000 | propertyJsonArray.getLongValue(3));
                                    boolean textShadowTransparent = "true".equalsIgnoreCase(propertyJsonArray.getString(11));
                                    String text = infoJsonArray.getString(1);
                                    callBack.onReceiveDanmu(text, textSize, textColor, textShadowTransparent);
                                }
                            }
                            catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                }

            }

            @Override
            public void onMessage(String message) {
                //System.out.println("Rec: "+ message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                if(callBack != null){
                    callBack.onClose(code, reason, remote);
                }
            }

            @Override
            public void onError(Exception ex) {
                //ex.printStackTrace();
            }
        };
    }

    public void start() throws InterruptedException  {
        if(!webSocketClient.isOpen()){
            webSocketClient.connectBlocking();
            String joinRoomJson = String.format(Locale.CHINA, ChatBroadcastPacketUtil.ROOM_AUTH_JSON, roomId, token);
            webSocketClient.send(ChatBroadcastPacketUtil.encode(joinRoomJson, 1, ChatBroadcastPacketUtil.OPERATION_AUTH));
        }
        if(heartTimer == null){
            heartTimer = new Timer();
            heartTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if(webSocketClient.isOpen()){
                        webSocketClient.send(ChatBroadcastPacketUtil.HEART_PACKET);
                    }
                }
            }, 1000, 30000);
        }
    }

    public void close(){
        if(heartTimer != null){
            heartTimer.cancel();
            heartTimer = null;
        }
        if(webSocketClient != null){
            webSocketClient.close();
            webSocketClient = null;
        }
    }

    public boolean isClosed(){
       return webSocketClient.isClosed();
    }

    public boolean isOpen(){
        return webSocketClient.isOpen();
    }

    public void setCallBack(CallBack callBack) {
        this.callBack = callBack;
    }

    public interface CallBack{
        void onStart();

        void onReceiveDanmu(String text, float textSize, int textColor, boolean textShadowTransparent);

        void onClose(int code, String reason, boolean remote);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        long roomId = 545068;
        BilibiliLiveApiClient httpClient = new BilibiliLiveApiClient();
        BilibiliResponse<DanmuInfo> response = httpClient.getDanmuInfo(roomId);
        ChatBroadcastWsClient client = new ChatBroadcastWsClient(roomId, response.getData().getToken());
        client.start();
        client.setCallBack(new CallBack() {

            @Override
            public void onStart() {
            }

            @Override
            public void onReceiveDanmu(String text, float textSize, int textColor, boolean textShadowTransparent) {
                String message = String.format(Locale.CHINA, "text:%s, textSize:%f, textColor:%d, textShadowTransparent:%b", text, textSize, textColor, textShadowTransparent);
                System.out.println(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {

            }
        });
    }
}

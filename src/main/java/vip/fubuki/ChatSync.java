package vip.fubuki;


import vip.fubuki.chat.ChatSyncClient;
import vip.fubuki.chat.ChatSyncServer;

import java.io.IOException;

import static vip.fubuki.playersync.JdbcConfig;

public class ChatSync {

    public static void register(){
        if(JdbcConfig.IS_CHAT_SERVER) {
            new Thread(()->{
                ChatSyncServer chatSyncServer = new ChatSyncServer();
                try {
                    chatSyncServer.run();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }

        new Thread(()->{
            ChatSyncClient chatSyncClient = new ChatSyncClient();
            chatSyncClient.run();
        }).start();
    }
}

package vip.fubuki.chat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import vip.fubuki.playersync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatSyncClient implements Listener {
    static Socket clientSocket;
    static PrintWriter out;

    public void run() {
        try {
            clientSocket = new Socket(playersync.JdbcConfig.CHAT_SERVER_IP, playersync.JdbcConfig.CHAT_SERVER_PORT);
            out = new PrintWriter(clientSocket.getOutputStream(),true);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));

            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                playersync.instance.getServer().broadcastMessage(serverMessage);
            }
        } catch (IOException e) {
            e.printStackTrace();
            reconnectClient();
        }
    }

    private void reconnectClient() {
        //TODO
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String message= "<"+event.getPlayer().getName()+"> "+event.getMessage();
        if (out != null) {
            out.println(message);
        }
    }
}

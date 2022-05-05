package org.novau2333.npebot.fakeplayer;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.game.ClientCommand;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.*;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import main.java.org.novau2333.npebot.fakeplayer.WorldInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class FakePlayerFactory {
    private static final Logger logger = LogManager.getLogger();
    public static boolean loginPluginEnabled = true;
    public static String password;
    public static final ConcurrentMap<Session, WorldInfo> sessionInfo = new ConcurrentHashMap<>();
    public static Queue<String> messageTask = new ConcurrentLinkedQueue<>();
    private static final ConcurrentMap<Session,Thread> autoRespawners = new ConcurrentHashMap<>();
    public static TcpClientSession getNewSession(InetSocketAddress address,String userName,String accessToken){
        TcpClientSession session;
        if (accessToken != null) {
            session = new TcpClientSession(address.getHostName(),address.getPort(),new MinecraftProtocol(new GameProfile((String) null,userName),accessToken));
        } else {
            session = new TcpClientSession(address.getHostName(),address.getPort(),new MinecraftProtocol(userName));
        }
        session.setReadTimeout(3000);
        final BotHandler botHandler = new BotHandler();
        session.setWriteTimeout(3000);
        session.addListener(new SessionListener() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                if(packet instanceof ClientboundChatPacket){
                    ClientboundChatPacket chatPacket = (ClientboundChatPacket) packet;
                    Component message = chatPacket.getMessage();
                    String textComponent = LegacyComponentSerializer.legacySection().serialize(message);
                    logger.info("[PacketListener][ChatPacket] {}",textComponent);
                }
                if (packet instanceof ClientboundLoginPacket){
                    logger.info("[MCChatBot] Logged in as entityId {}",((ClientboundLoginPacket) packet).getEntityId());
                    ClientboundLoginPacket loginPacket = (ClientboundLoginPacket) packet;
                    sessionInfo.put(session,new WorldInfo(loginPacket));
                    session.setFlag("loggedin",true);
                    Thread autoRespawner = new Thread(()->{
                        while (session.isConnected()){
                            try {
                                for (String message : messageTask){
                                    session.send(new ServerboundChatPacket(message));
                                    messageTask.remove(message);
                                }
                                session.send(new ServerboundClientCommandPacket(ClientCommand.RESPAWN));
                                Thread.sleep(1000);
                            }catch (InterruptedException e){
                                e.printStackTrace();
                            }
                        }
                    },"AutoRespawner-"+Thread.currentThread().getId());
                    autoRespawner.setDaemon(true);
                    autoRespawner.start();
                    autoRespawners.put(session,autoRespawner);
                    botHandler.handleAuthmePlugin(session);
                }
            }
            @Override
            public void packetSending(PacketSendingEvent packetSendingEvent) {}
            @Override
            public void packetSent(Session session, Packet packet) {}
            @Override
            public void packetError(PacketErrorEvent packetErrorEvent) {}
            @Override
            public void connected(ConnectedEvent connectedEvent) {}
            @Override
            public void disconnecting(DisconnectingEvent disconnectingEvent) {}
            @Override
            public void disconnected(DisconnectedEvent disconnectedEvent) {
                botHandler.handleDisconnect(disconnectedEvent); //Handle disconnect
            }
        });
        return session;
    }

    private static final class BotHandler{
        public void handleDisconnect(DisconnectedEvent event){
            logger.info("[MCChatBot] Disconnected from server.Reason: {}",event.getReason());
            if (autoRespawners.get(event.getSession()).isAlive()){
                autoRespawners.get(event.getSession()).stop();
            }
            autoRespawners.remove(event.getSession());
            event.getSession().setFlag("loggedin",false);
        }

        public void handleAuthmePlugin(Session session){
            if (loginPluginEnabled) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //Register the fake player
                session.send(new ServerboundChatPacket("/register "+password+" "+password));
                session.send(new ServerboundChatPacket("/l "+password));
            }
        }
    }

}
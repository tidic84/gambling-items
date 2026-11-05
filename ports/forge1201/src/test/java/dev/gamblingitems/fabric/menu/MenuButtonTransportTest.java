package dev.gamblingitems.fabric.menu;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Real LocalChannel/TCP payload checks, without starting or authenticating a Minecraft client. */
class MenuButtonTransportTest {
    private static final int[] BUTTONS = {0, 1, 127, 128, 1000, 1004, 1100, 1216, 2515, 32767, 40000, 40008};

    @Test void legacyActionsReachTheHostButAreTruncatedForRemotePlayers() throws Exception {
        try (Transport host = new Transport(true, false); Transport guest = new Transport(false, false)) {
            for (int button : BUTTONS) {
                var packet = new ServerboundContainerButtonClickPacket(1, button);
                assertEquals(button, host.receive(packet), "Host keeps action " + button);
                assertEquals((int) (byte) button, guest.receive(packet), "TCP applies the legacy byte format");
            }
        }
    }

    @Test void the022PayloadKeepsEveryActionOverTcp() throws Exception {
        try (Transport guest = new Transport(false, true)) {
            for (int button : BUTTONS)
                assertEquals(button, guest.receive(new MenuButton(1, button)), "Remote action " + button);
        }
    }

    private static final class Transport implements AutoCloseable {
        private final EventLoopGroup group;
        private final LinkedBlockingQueue<Integer> received = new LinkedBlockingQueue<>();
        private Channel server, client;

        Transport(boolean local, boolean modern) throws Exception {
            group = local ? new DefaultEventLoopGroup(1) : new NioEventLoopGroup(1);
            try {
                server = new ServerBootstrap().group(group)
                        .channel(local ? LocalServerChannel.class : NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<Channel>() {
                            @Override protected void initChannel(Channel channel) {
                                if (!local) {
                                    // A test-only length prefix separates payloads; Minecraft supplies its own framing.
                                    channel.pipeline().addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                                    channel.pipeline().addLast(new MessageToMessageDecoder<ByteBuf>() {
                                        @Override protected void decode(ChannelHandlerContext context, ByteBuf bytes, List<Object> out) {
                                            FriendlyByteBuf buffer = new FriendlyByteBuf(bytes);
                                            out.add(modern ? MenuButton.read(buffer)
                                                    : new ServerboundContainerButtonClickPacket(buffer));
                                        }
                                    });
                                }
                                channel.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                                    @Override protected void channelRead0(ChannelHandlerContext context, Object message) {
                                        received.add(message instanceof MenuButton action ? action.button()
                                                : ((ServerboundContainerButtonClickPacket) message).getButtonId());
                                    }
                                });
                            }
                        }).bind(local ? new LocalAddress("menu-action-" + UUID.randomUUID())
                                : new InetSocketAddress("127.0.0.1", 0)).sync().channel();
                client = new Bootstrap().group(group)
                        .channel(local ? LocalChannel.class : NioSocketChannel.class)
                        .handler(new ChannelInitializer<Channel>() {
                            @Override protected void initChannel(Channel channel) {
                                if (!local) {
                                    channel.pipeline().addLast(new LengthFieldPrepender(2));
                                    channel.pipeline().addLast(new MessageToByteEncoder<Object>() {
                                        @Override protected void encode(ChannelHandlerContext context, Object message, ByteBuf bytes) {
                                            FriendlyByteBuf buffer = new FriendlyByteBuf(bytes);
                                            if (message instanceof MenuButton action) action.write(buffer);
                                            else ((ServerboundContainerButtonClickPacket) message).write(buffer);
                                        }
                                    });
                                }
                            }
                        }).connect(server.localAddress()).sync().channel();
            } catch (Exception error) {
                close();
                throw error;
            }
        }

        int receive(Object packet) throws Exception {
            client.writeAndFlush(packet).sync();
            Integer button = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(button, "The loopback transport delivers an action");
            return button;
        }

        @Override public void close() {
            if (client != null) client.close().syncUninterruptibly();
            if (server != null) server.close().syncUninterruptibly();
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}

package io.zrz.jpgsql.proto.replication;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.ScheduledFuture;
import io.zrz.jpgsql.proto.AbstractConnection;
import io.zrz.jpgsql.proto.WalPosition;
import io.zrz.jpgsql.proto.netty.handler.PostgreSQLClientNegotiation;
import io.zrz.jpgsql.proto.netty.handler.PostgreSQLDecoder;
import io.zrz.jpgsql.proto.netty.handler.PostgreSQLEncoder;
import io.zrz.jpgsql.proto.netty.handler.PostgreSQLHandshakeCompleteEvent;
import io.zrz.jpgsql.proto.wire.CommandComplete;
import io.zrz.jpgsql.proto.wire.CopyData;
import io.zrz.jpgsql.proto.wire.PostgreSQLPacket;
import io.zrz.jpgsql.proto.wire.Query;
import lombok.extern.slf4j.Slf4j;

/**
 * A postgresql replication protocol connection.
 * 
 * The connection is established, and then START_REPLICATION called with the given slot name and position.
 * 
 * If an error occurs, the connection is terminated.
 * 
 * @author theo
 *
 */

@Slf4j
public class ReplicationConnection extends AbstractConnection {

  private static final String DEFAULT_USERNAME = System.getProperty("user.name", "postgres");

  private EventLoopGroup group;
  private ChannelFuture connectFuture;
  private HashMap<String, String> params = new HashMap<>();
  private final String slotId;
  private WalPosition position;
  private String create;

  ReplicationConnection(ReplicationConnectionBuilder b) {

    if (b.slotId == null) {
      throw new IllegalArgumentException("slotId");
    }

    this.group = b.group;
    this.slotId = b.slotId;
    this.position = b.position;
    this.create = b.create;

    if (b.username == null) {
      params.put("user", DEFAULT_USERNAME);
    }
    else {
      params.put("user", b.username);
    }

    if (b.database == null) {
      params.put("database", params.get("user"));
    }
    else {
      params.put("database", b.database);
    }

    params.put("client_encoding", "UTF-8");
    params.put("replication", "database");

  }

  private static enum HandlerState {
    Waiting,
    Identifying,
    Creating,
    Starting
  }

  private HandlerState state = HandlerState.Waiting;

  /**
   * handler which dispatches events from the netty thread to the user thread.
   */

  private final class Handler extends SimpleChannelInboundHandler<PostgreSQLPacket> {

    private long outputWrittenLsn;
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> future;

    // note that any packets which contain byte buffers are NOT retained after we return, so need to copy if needed.

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PostgreSQLPacket msg) throws Exception {

      if (msg instanceof CommandComplete) {
        this.nextStage();
      }
      else if (msg instanceof CopyData) {

        final ByteBuf copydata = ((CopyData) msg).getData();

        final byte type = copydata.readByte();

        switch (type) {

          case 'w': // data

            final long startingPoint = copydata.readLong();
            final long currentEnd = copydata.readLong();
            final long txtime = copydata.readLong();

            log.debug("DATA: {}, {}, {}", startingPoint, currentEnd, txtime);

            // A single WAL record is never split across two XLogData messages. When a WAL record crosses a WAL page
            // boundary, and
            // is therefore already split using continuation records, it can be split at the page boundary. In other
            // words, the first
            // main WAL record and its continuation records can be sent in different XLogData messages.

            XLogData data = new XLogData(startingPoint, currentEnd, txtime, copydata.slice());

            break;

          case 'k': // keepalive

            this.processKeepalive(copydata);
            log.debug("KEEPALIVE");
            break;

          default:
            log.warn("Unknown type: '{}'", type);
            break;

        }

      }
      else {
        System.err.println(msg);
      }

    }

    /**
     * The previous command completed, do the next thing.
     */

    private void nextStage() {

      log.debug("Executing next stage in {}", state);

      switch (state) {
        case Waiting:
          state = HandlerState.Identifying;
          sendIdentify();
          break;
        case Identifying:
          if (create != null) {
            state = HandlerState.Creating;
            sendCreate();
            break;
          }
        case Creating:
          state = HandlerState.Starting;
          sendStart();
          break;
        case Starting:
          // hmmp, that's odd...
          throw new RuntimeException("Unexpected next stage for state");

      }

    }

    void sendIdentify() {
      ctx.writeAndFlush(new Query("IDENTIFY_SYSTEM"));
    }

    void sendCreate() {
      StringBuilder sb = new StringBuilder();
      sb.append("CREATE_REPLICATION_SLOT ").append(slotId).append(" LOGICAL ").append(create);
      ctx.writeAndFlush(new Query(sb.toString()));
    }

    void sendStart() {

      StringBuilder sb = new StringBuilder();

      sb.append("START_REPLICATION SLOT ").append(slotId).append(" LOGICAL ");

      if (position != null) {
        sb.append(position);
      }
      else {
        sb.append("0/0");
      }

      ctx.writeAndFlush(new Query(sb.toString()));

    }

    private void processKeepalive(final ByteBuf ptr) {

      final long currentEnd = ptr.readLong();
      final long serverTimeMicros = ptr.readLong();

      this.outputWrittenLsn = currentEnd;

      final byte reply = ptr.readByte();

      log.info(String.format("Current Time: %s, Server Time: %s, Reply? %s",
          Long.toHexString(currentEnd),
          Long.toString(serverTimeMicros),
          Boolean.toString(reply != 0)));

      if (reply == 1) {
        this.sendKeepalive();
      }

    }

    /**
     * While in CopyBoth mode, we need to send periodic keepalives.
     */

    private void sendKeepalive() {

      final long position = this.outputWrittenLsn;

      log.debug("Sending Keepalive position={}", Long.toHexString(position));

      // send feedback
      final ByteBuf xkp = this.ctx.alloc().buffer();

      xkp.writeByte('r');
      xkp.writeLong(position);
      xkp.writeLong(position);
      xkp.writeLong(0);
      xkp.writeLong(System.currentTimeMillis() * 1000); // micros since epoch
      xkp.writeByte(0);

      final ByteBuf xcd = this.ctx.alloc().buffer();
      xcd.writeByte('d');
      xcd.writeInt(xkp.readableBytes() + 4);
      xcd.writeBytes(xkp);
      ctx.writeAndFlush(xcd);

    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

      if (evt instanceof PostgreSQLHandshakeCompleteEvent) {
        this.ctx = ctx;
        // no need to leave it laying around.
        ctx.channel().pipeline().remove(PostgreSQLClientNegotiation.class);
        nextStage();
      }

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      this.future = ctx.executor().scheduleWithFixedDelay(this::sendKeepalive, 5, 5, TimeUnit.SECONDS);
      super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      this.future.cancel(true);
      super.channelInactive(ctx);
    }

  }

  void connect(String host, int port) {

    final Bootstrap b = new Bootstrap();

    b.group(group)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.TCP_NODELAY, true)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          public void initChannel(final SocketChannel ch) throws Exception {
            final ChannelPipeline p = ch.pipeline();
            p.addLast(new PostgreSQLDecoder());
            p.addLast(new PostgreSQLEncoder());
            p.addLast(new PostgreSQLClientNegotiation(ReplicationConnection.this.params, null));
            p.addLast(new Handler());
          }
        });

    // attempt to connect.
    this.connectFuture = b.connect(host, port);

  }

}

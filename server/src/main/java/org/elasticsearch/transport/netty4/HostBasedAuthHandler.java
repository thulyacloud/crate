
package org.elasticsearch.transport.netty4;

import java.net.InetAddress;
import java.nio.channels.ClosedChannelException;

import javax.security.sasl.AuthenticationException;

import org.elasticsearch.common.network.CloseableChannel;
import org.elasticsearch.http.netty4.Netty4HttpServerTransport;

import io.crate.auth.Authentication;
import io.crate.auth.Protocol;
import io.crate.protocols.SSL;
import io.crate.protocols.postgres.ConnectionProperties;
import io.crate.user.User;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

public class HostBasedAuthHandler extends ChannelInboundHandlerAdapter {

    private final Authentication authentication;
    private Exception authError;

    public HostBasedAuthHandler(Authentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (authError != null) {
            ReferenceCountUtil.release(msg);
            Netty4TcpChannel tcpChannel = ctx.channel().attr(Netty4Transport.CHANNEL_KEY).get();
            CloseableChannel.closeChannel(tcpChannel, true);
            throw authError;
        }

        Channel channel = ctx.channel();
        InetAddress remoteAddress = Netty4HttpServerTransport.getRemoteAddress(channel);
        ConnectionProperties connectionProperties = new ConnectionProperties(
            remoteAddress,
            Protocol.TRANSPORT,
            SSL.getSession(channel)
        );
        // TODO:
        // If we use the `crate` user it is likely that the common name in the certificate doesn't match
        // Options:
        // - use the remote hostname ?
        // - add option to control whether common-name and user name must match
        // - Always skip the common-name/user-name check
        String user = User.CRATE_USER.name();
        var authMethod = authentication.resolveAuthenticationType(user, connectionProperties);
        if (authMethod == null) {
            ReferenceCountUtil.release(msg);
            authError = new AuthenticationException("No valid auth.host_based entry found for: " + remoteAddress);
            Netty4TcpChannel tcpChannel = ctx.channel().attr(Netty4Transport.CHANNEL_KEY).get();
            CloseableChannel.closeChannel(tcpChannel, true);
            throw authError;
        }
        try {
            authMethod.authenticate(user, null, connectionProperties);
            ctx.pipeline().remove(this);
            super.channelRead(ctx, msg);
        } catch (Exception e) {
            ReferenceCountUtil.release(msg);
            authError = e;
            Netty4TcpChannel tcpChannel = ctx.channel().attr(Netty4Transport.CHANNEL_KEY).get();
            CloseableChannel.closeChannel(tcpChannel, true);
            throw e;
        }
    }
}

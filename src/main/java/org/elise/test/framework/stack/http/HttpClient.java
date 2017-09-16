package org.elise.test.framework.stack.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import org.elise.test.config.HttpStackConfiguration;
import org.elise.test.framework.transaction.http.HttpResultCallBack;

import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Created by Glenn on 2017/9/8.
 */
public final class HttpClient {

    private static final EventLoopGroup workerGroup = new NioEventLoopGroup(HttpStackConfiguration.getInstance().getWorkerGroupThreads());
    private static final Bootstrap b = new Bootstrap();

    private static final Map<SocketAddress, List<HttpConnection>> hostPool = new ConcurrentHashMap<>();
    private static final Map<String, HttpConnection> connectionMap = new ConcurrentHashMap<>();

    private static HttpClient client = null;
    private static Boolean isInitialized = false;

    public static HttpConnection getInstance(Integer flag, URI uri) {
        synchronized (isInitialized) {
            if (!isInitialized) {
                start();
            }
        }
        SocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort());
        List<HttpConnection> connList ;
        synchronized (hostPool) {
            connList = hostPool.get(address);
            if (connList == null) {
                connList = new ArrayList<>(HttpStackConfiguration.getInstance().getMaxConnCountPerPort());
                for (int i = 0; i < HttpStackConfiguration.getInstance().getMaxConnCountPerPort(); i++)
                    connList.add(new HttpConnection(client,i,address));
                hostPool.put(address,connList);
            }
        }
        return connList.get(flag % HttpStackConfiguration.getInstance().getMaxConnCountPerPort());
    }


    public static void start() {
        client = new HttpClient();
        isInitialized = true;
    }

    private HttpClient() {
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, HttpStackConfiguration.getInstance().getSocketKeepAlive());
        b.option(ChannelOption.SO_RCVBUF,HttpStackConfiguration.getInstance().getSocketReceiveBuffer());
        b.option(ChannelOption.SO_SNDBUF,HttpStackConfiguration.getInstance().getSocketSendBuffer());
        b.option(ChannelOption.SO_REUSEADDR,HttpStackConfiguration.getInstance().getSocketReuseAddress());
        b.option(ChannelOption.TCP_NODELAY, HttpStackConfiguration.getInstance().getTcpNoDelay());
        b.option(ChannelOption.AUTO_READ,HttpStackConfiguration.getInstance().getTcpAutoRead());
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, HttpStackConfiguration.getInstance().getConnectTimeoutMillis());
        b.option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                new WriteBufferWaterMark(HttpStackConfiguration.getInstance().getWriteBufferWaterMark()[0],
                HttpStackConfiguration.getInstance().getWriteBufferWaterMark()[1]));
        b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        b.option(ChannelOption.RCVBUF_ALLOCATOR,
                new AdaptiveRecvByteBufAllocator(HttpStackConfiguration.getInstance().getReceiveBufferAllocator()[0],
                        HttpStackConfiguration.getInstance().getReceiveBufferAllocator()[1],
                        HttpStackConfiguration.getInstance().getReceiveBufferAllocator()[2]));
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("HttpResponseDecoder", new HttpResponseDecoder());
                p.addLast("HttpRequestEncoder", new HttpRequestEncoder());
                p.addLast("HttpContentDecompressor",new HttpContentDecompressor());
                p.addLast("Aggregator", new HttpObjectAggregator(HttpStackConfiguration.getInstance().getMaxContentLength()));
                p.addLast("HttpClient", new HttpRespHandler());
            }
        });
    }

    public static void close() {
        workerGroup.shutdownGracefully();
    }

    protected void register(String channelId, HttpConnection conn){
        connectionMap.put(channelId,conn);
    }

    protected void unregister(String channelId){
        connectionMap.remove(channelId);
    }

    protected Bootstrap getBootstrap(){
        return b;
    }

    protected static long getCounter(String channelId) throws Exception {
        HttpConnection conn = connectionMap.get(channelId);
        if (conn == null) {
            throw new Exception("Http Connection is null");
        } else {
            return conn.getCounter();
        }
    }

    protected static HttpResultCallBack getCallBack(String channelId) throws Exception {
        HttpConnection conn = connectionMap.get(channelId);
        if (conn == null) {
            throw new Exception("Http Connection is null");
        } else {
            return conn.getCallBack();
        }
    }

    protected static void putCallBack(String channelId, HttpResultCallBack callBack) throws Exception {
        HttpConnection conn = connectionMap.get(channelId);
        if (conn == null) {
            throw new Exception("Http Connection is null");
        } else {
            conn.setCallBack(callBack);
        }
    }
}
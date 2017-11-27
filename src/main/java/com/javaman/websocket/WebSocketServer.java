package com.javaman.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * Author:彭哲
 * Date:2017/11/27
 */
public class WebSocketServer {

    public void run(int port) {

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            //添加HttpServerCodec,将请求和应答消息编码或者解码成HTTP消息
                            pipeline.addLast("http-codec", new HttpServerCodec());
                            //添加HttpObjectAggregator,将HTTP消息的多个部分,组合成一条完整的HTTP消息
                            pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                            //添加ChunkedWriteHandler,向客户端发送HTML5文件,主要用于支持浏览器和服务端进行WebSocket通信
                            socketChannel.pipeline().addLast("http-chunked", new ChunkedWriteHandler());
                            //增加WebSocket服务端Handler
                            pipeline.addLast("handler", new WebSocketHandler());
                        }
                    });
            Channel channel = bootstrap.bind(port).sync().channel();
            System.out.println("WebSocket Server started on port" + port);
            System.out.println("Open your browser and navigate to:http://127.0.0.1: " + port);
            channel.closeFuture().sync();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }


    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        new WebSocketServer().run(port);
    }
}

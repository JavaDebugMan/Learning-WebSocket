package com.javaman.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import sun.text.resources.cldr.ru.FormatData_ru_UA;

import java.nio.charset.Charset;
import java.util.Date;

/**
 * @author:彭哲 Date:2017/11/27
 */
public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = Logger.getLogger(WebSocketHandler.class);

    private WebSocketServerHandshaker handshaker;

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        //传统的Http接入
        if (msg instanceof FullHttpMessage) {
            //第一次握手请求消息由HTTP协议承载,所以它是一个HTTP消息,执行handleHttpRequest来处理WebSocket握手请求
            handleHttpRequest(ctx, (FullHttpRequest) msg);
            //WebSocket接入
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        //如果HTTP解码失败,返回Http异常
        if (!request.getDecoderResult().isSuccess() ||
                (!"websocket".equals(request.headers().get("Upgrade")))) {
            //首先对握手请求消息进行判断,如果消息头中没有包含Upgrade字段或者它的值不是websocket,则返回HTTP400响应
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        //构造握手响应返回,本机测试
        /*
        握手请求简单检验通过后,开始构造握手工厂,创建握手处理类WebSocketServerHandshaker
        通过它构造握手响应消息给客户端,同时将WebSocket相关的编码和解码类动态添加到ChannelPipeline中,
        用于WebSocket消息的编解码
        添加WebSocket Encoder和WebSocket Decoder之后,服务端就可以自动对WebSocket消息进行编解码了,
        后面的业务handler可以直接对WebSocket对象进行操作

        链路建立成功之后:
        客户端通过文本框提交消息给服务端,WebSocketHandler接收到是已经解码后的WebSocketFrame消息
         */
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory
                ("ws://127.0.0.1:8080/websocket", null, false);
        handshaker = wsFactory.newHandshaker(request);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), request);
        }
    }

    /**
     * @param ctx
     * @param frame 对请求消息进行处理,首先需要对控制帧进行判断
     *              如果是关闭链路的消息,就调用WebSocketServerHandler的close方法关闭WebSocket连接
     *              如果是维持链路的Ping消息,则构造PONG消息返回
     *              由于本例的WebSocket通信双方使用的都是文本消息,所以对请求消息的类型进行判断,不是文本的抛出异常
     *              最后从TextWebSocketFrame中获取请求消息的字符串,对他处理后通过构造新的TextWebSocketFrame
     *              消息返回给客户端,由于握手应答时动态添加了TextWebSocketFrame的编码类,所以可以直接发送TextWebSocketFrame对象
     *              客户端接收到服务器的应答后,将其内容取出展示到浏览器页面中
     */

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        //判断是否关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        //判断是否是Ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        //本例值支持文本消息,不支持二进制消息
        if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported",
                    frame.getClass().getName()));
        }
        //返回应答消息
        String request = ((TextWebSocketFrame) frame).text();
        ctx.channel().write(new TextWebSocketFrame(request + ",欢迎使用Netty WebSocket服务,现在时刻:" + new Date().toString()));
    }


    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        //返回给客户端
        if (response.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(response.getStatus().toString(), CharsetUtil.UTF_8);
            response.content().writeBytes(buf);
            buf.release();
        }
        ChannelFuture channelFuture = ctx.channel().writeAndFlush(response);
        if (response.getStatus().code() != 200) {
            channelFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }


}

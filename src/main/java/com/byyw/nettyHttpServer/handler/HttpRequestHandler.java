package com.byyw.nettyHttpServer.handler;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.byyw.nettyHttpServer.controller.FileController;
import com.byyw.nettyHttpServer.controller.TestController;
import com.byyw.nettyHttpServer.entity.HttpParams;
import com.byyw.nettyHttpServer.enums.ResultCode;
import com.byyw.nettyHttpServer.util.NettyHttpResponseUtils;

import cn.hutool.json.JSONObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ChannelHandler.Sharable
public class HttpRequestHandler extends SimpleChannelInboundHandler<Object> {

    @Value("${netty.http.websocket_port}")
    private Integer wsPort;

    private WebSocketServerHandshaker handshaker;

    @Autowired
    private TestController testController;
    @Autowired
    private FileController fileController;

    /** 
     * @param ctx
     * @param obj
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object obj) throws Exception {
        if(obj instanceof FullHttpRequest){
            httpRequestHandler(ctx, (FullHttpRequest)obj);
        } else if(obj instanceof WebSocketFrame){
            webSocketHandler(ctx, obj); 
        }
    }
    
    /** 
     * @param ctx
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    
    /** 
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.writeAndFlush("").addListener(ChannelFutureListener.CLOSE);
        new Exception(cause).printStackTrace();
    }

    
    /** 
     * @param ctx
     * @param fullHttpRequest
     * @throws UnsupportedEncodingException
     */
    private void httpRequestHandler(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws UnsupportedEncodingException{
        fullHttpRequest.setUri(URLDecoder.decode(fullHttpRequest.uri(), "utf-8"));
        // 识别为websocket连接请求
        if (fullHttpRequest.decoderResult().isSuccess()
                && "websocket".equals(fullHttpRequest.headers().get("Upgrade"))) {
            webSocketHandler(ctx,fullHttpRequest);
            return;
        }
        // 请求类型为get或post之外的类型
        if (!(fullHttpRequest.method() == HttpMethod.GET || fullHttpRequest.method() == HttpMethod.POST)) {
            JSONObject json = new JSONObject();
            json.set("code", ResultCode.OTHER_ERROR);
            json.set("msg", "不支持的http请求类型");
            HttpParams hp = new HttpParams();
            hp.setCtx(ctx);
            hp.setFullHttpRequest(fullHttpRequest);
            NettyHttpResponseUtils.response(hp, json);
            return;
        }
        // 将业务过程放入其他线程，防止堵塞businessGroup线程
        HttpParams hp = new HttpParams(ctx, fullHttpRequest);
        // TODO 负载均衡
        new Thread(()->{
            try {
                String uri = fullHttpRequest.uri();
                if(uri.startsWith("/test")) {
                    testController.work(hp);
                } else if(uri.startsWith("/file")){
                    fileController.work(hp);
                } else {
                    JSONObject j = new JSONObject();
                    j.set("code", ResultCode.NO_INTERFACE).set("msg", "url不存在");
                    NettyHttpResponseUtils.response(hp, j);
                }
            } catch (Exception e) {
                e.printStackTrace();
                JSONObject j = new JSONObject();
                j.set("code", ResultCode.EXCEPTION).set("msg", "异常报错");
                NettyHttpResponseUtils.response(hp, j);
            }
        }).start();
    }

    
    /** 
     * @param ctx
     * @param obj
     */
    protected void webSocketHandler(ChannelHandlerContext ctx, Object obj){
        // websocket 连接请求
        if(obj instanceof FullHttpRequest){
            FullHttpRequest fullHttpRequest = (FullHttpRequest) obj;
            if (fullHttpRequest.decoderResult().isSuccess()
                    && "websocket".equals(fullHttpRequest.headers().get("Upgrade"))) {


                WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                    "ws://localhost:"+wsPort+"/websocket", null, false,Integer.MAX_VALUE);
                handshaker = wsFactory.newHandshaker(fullHttpRequest);
                if(handshaker == null){
                    WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                } else {
                    handshaker.handshake(ctx.channel(),fullHttpRequest);
                }
            }
            return;
        } else if(obj instanceof WebSocketFrame){
            WebSocketFrame webSocketFrame = (WebSocketFrame)obj;
            // 关闭链路指令
            if (webSocketFrame instanceof CloseWebSocketFrame) {
                channelClose(ctx.channel());
                handshaker.close(ctx.channel(), (CloseWebSocketFrame) webSocketFrame.retain());
                ctx.writeAndFlush("").addListener(ChannelFutureListener.CLOSE);
                return;
            }
            // ping消息,协议方式
            if (webSocketFrame instanceof PingWebSocketFrame) {
                ctx.channel().write(new PongWebSocketFrame(webSocketFrame.content().retain()));
                return;
            }
            // text文本消息
            if (webSocketFrame instanceof TextWebSocketFrame) {
                String request = ((TextWebSocketFrame) webSocketFrame).text();
                System.out.println(request);
                ctx.channel().writeAndFlush("response: "+request);
            }
        }
    }
    
    /** 
     * @param channel
     */
    private void channelClose(Channel channel){
        log.info(channel.id().asLongText()+" is close.");
    }
}
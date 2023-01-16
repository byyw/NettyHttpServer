package com.byyw.nettyHttpServer.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLEncoder;

import com.byyw.nettyHttpServer.entity.HFile;
import com.byyw.nettyHttpServer.entity.HttpParams;
import com.byyw.nettyHttpServer.enums.ResultCode;
import com.byyw.nettyHttpServer.exception.ParamErrorException;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * http响应工具
 */
@Slf4j
public class NettyHttpResponseUtils {
    /** 
     * @param hp
     * @param json
     */
    public static void response(HttpParams hp, JSONObject json) {
        ChannelHandlerContext ctx = hp.getCtx();
        FullHttpRequest fullHttpRequest = hp.getFullHttpRequest();
        // 创建http响应
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);

        if (json.containsKey("code") && Integer.valueOf(String.valueOf(json.get("code"))) == ResultCode.NO_INTERFACE) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            response.content().writeBytes((fullHttpRequest.uri() + " not found.").getBytes());
        } else {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            response.content().writeBytes(Unpooled.copiedBuffer(json.toString(), CharsetUtil.UTF_8));
        }
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    
    /**   
     * @param hp
     * @param file
     */
    public static void responseFile(HttpParams hp, HFile file) {
        ChannelHandlerContext ctx = hp.getCtx();
        FullHttpRequest fullHttpRequest = hp.getFullHttpRequest();
        try {
            final RandomAccessFile raf = new RandomAccessFile(file.getFile(), "r");

            Long offset=null, end=null;
            long fileLength = file.getFile().length();
            // 断点续传
            String range = hp.getFullHttpRequest().headers().get(HttpHeaderNames.RANGE);
            if(StrUtil.isNotBlank(range)){
                range = range.substring(6);
                String[] split = range.split("-");
                offset = Long.parseLong(split[0]);
                if (split.length > 1 && StrUtil.isNotEmpty(split[1])) {
                    end = Long.parseLong(split[1]);
                }
            }

            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            if(offset != null){
                response.setStatus(HttpResponseStatus.PARTIAL_CONTENT);
            }
            // 跨域请求
            if (fullHttpRequest.headers().get("origin") != null) {
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN,
                        fullHttpRequest.headers().get("origin"));
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, DELETE, PUT, OPTIONS");
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS,
                        "Content-Type, CDS-REQ-TYPE, CDS-SM-VERSION");
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            }
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, file.getContentType());
            // 是否下载
            if(hp.getParamStr("d","0").equals("1")){
                response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(file.getName(), "UTF-8") + "\";");
            }
            
            response.headers().set(HttpHeaderNames.ACCEPT_RANGES, HttpHeaderValues.BYTES);
            if(offset!=null || end!=null){
                response.headers().set(HttpHeaderNames.CONTENT_RANGE, "bytes " + offset + "-" + ((end==null)?fileLength-1:end) + "/" + fileLength);
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, ((end==null)?fileLength:end+1)-offset);
            } else {
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileLength);
            }

            // todo 文件唯一标识符优化
            // response.headers().set(HttpHeaderNames.ETAG, "w/123123123");

            ctx.write(response);
            if(offset == null)  offset = 0l;
            if(end == null)  end = fileLength-1;
            ChannelFuture sendFileFuture = ctx.write(new ChunkedFile(raf, offset, end-offset+1, 1024),ctx.newProgressivePromise());

            // 文件传输进度
            sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationComplete(ChannelProgressiveFuture future)
                        throws Exception {
                    // System.out.println("Transfer complete.");
                }

                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total)
                        throws Exception {
                    // if(total < 0)
                    // System.err.println("Transfer progress: " + progress);
                    // else
                    // System.err.println("Transfer progress: " + progress + "/" + total);
                }
            });
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
        } catch (IOException e) {
            e.printStackTrace();
            JSONObject json = new JSONObject();
            json.set("code", ResultCode.OPEN_FILE_FAILED);
            json.set("msg", "打开文件失败");
            response(hp,json);
        } catch (ParamErrorException e) {
        }
    }
    
    /** 
     * @param hp
     */
    public static void responseMedia(HttpParams hp){
        FullHttpRequest fullHttpRequest = hp.getFullHttpRequest();

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        // 跨域请求
        if (fullHttpRequest.headers().get("origin") != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN,
                    fullHttpRequest.headers().get("origin"));
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, DELETE, PUT, OPTIONS");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS,
                    "Content-Type, CDS-REQ-TYPE, CDS-SM-VERSION");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                .set(HttpHeaderNames.CONTENT_TYPE, "video/x-flv")
                .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
                .set(HttpHeaderNames.SERVER, "fj");
        // 实时流，不应该 .addListener(ChannelFutureListener.CLOSE)
        hp.getCtx().writeAndFlush(response);
    }
    /** 
     * @param hp
     */
    public static void responseMediHLS(HttpParams hp){
        FullHttpRequest fullHttpRequest = hp.getFullHttpRequest();

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        // 跨域请求
        if (fullHttpRequest.headers().get("origin") != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN,
                    fullHttpRequest.headers().get("origin"));
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, DELETE, PUT, OPTIONS");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS,
                    "Content-Type, CDS-REQ-TYPE, CDS-SM-VERSION");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                .set(HttpHeaderNames.CONTENT_TYPE, "video/x-hlv")
                .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
                .set(HttpHeaderNames.SERVER, "fj");
        // 实时流，不应该 .addListener(ChannelFutureListener.CLOSE)
        hp.getCtx().writeAndFlush(response);
    }
}
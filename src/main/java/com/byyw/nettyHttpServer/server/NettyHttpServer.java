package com.byyw.nettyHttpServer.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.byyw.nettyHttpServer.config.SslContextFactory;
import com.byyw.nettyHttpServer.handler.HttpRequestHandler;

import cn.hutool.core.util.StrUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.OptionalSslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NettyHttpServer{
	
	@Autowired
    @Qualifier("bossGroup")
    private NioEventLoopGroup bossGroup;

    @Autowired
    @Qualifier("workerGroup")
    private NioEventLoopGroup workerGroup;
    
    @Autowired
    @Qualifier("businessGroup")
    private EventExecutorGroup businessGroup;
    
	@Autowired
    private HttpRequestHandler httpRequestHandler;

    private ServerBootstrap serverBootstrap;
    private ChannelFuture channelFuture = null;

    @Value("${netty.http.port}")
    private Integer port;
    @Value("${netty.http.key}")
    private String sslKey;
    @Value("${netty.http.pass}")
    private String sslPass;
    
    /** 
     * @throws Exception
     */
    @PostConstruct
    public void construct(){
        this.serverBootstrap = new ServerBootstrap();
        this.serverBootstrap.group(bossGroup,workerGroup)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {
                        
                        //添加sslhandler
                        if(StrUtil.isNotBlank(sslKey)){
                            channel.pipeline().addLast(new OptionalSslHandler(SslContextFactory.newSslContext(sslKey,sslPass)));
                        }
                        channel.pipeline().addLast(new HttpServerCodec());// http 编解码
                        // channel.pipeline().addLast(new HttpContentCompressor()); 数据压缩
                        channel.pipeline().addLast(new HttpObjectAggregator(1073741824)); //对post参数编解码 接收的最大contentlength为1G(1024^3)
                        channel.pipeline().addLast(new ChunkedWriteHandler());//用于大数据的分区传输
                        channel.pipeline().addLast(new CorsHandler(CorsConfigBuilder.forAnyOrigin().allowNullOrigin()
                                .allowedRequestHeaders("Content-Type", "CDS-REQ-TYPE", "CDS-SM-VERSION")
                                .allowedRequestMethods(HttpMethod.GET,HttpMethod.POST,HttpMethod.OPTIONS,HttpMethod.DELETE,HttpMethod.PUT)
                                .allowCredentials().build()));// 跨域支持,仅过滤Preflight
                        channel.pipeline().addLast(businessGroup,httpRequestHandler);// 请求处理器
                    }
                });
        start();
    }

    public void start(){
        if(channelFuture == null){
            try {
                this.channelFuture = this.serverBootstrap.bind(port).sync();
                if (this.channelFuture.isSuccess()) {
                    log.info("http服务启动,port={}", port);
                }
            } catch (NumberFormatException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop(){
        if(channelFuture != null){
            this.channelFuture.channel().write("").addListener(ChannelFutureListener.CLOSE);
            this.channelFuture = null;
            log.info("http服务停止");
        }
    }
    
}

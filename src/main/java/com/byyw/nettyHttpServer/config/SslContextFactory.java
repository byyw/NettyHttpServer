package com.byyw.nettyHttpServer.config;

import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

@Component
public class SslContextFactory {
    
    /** 
     * @param key
     * @param pass
     * @return SslContext
     * @throws Exception
     */
    public static SslContext newSslContext(String key,String pass) throws Exception{
        KeyStore keyStore = KeyStore.getInstance("JKS");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        InputStream ksInputStream = new ClassPathResource(key).getInputStream();
        keyStore.load(ksInputStream, pass.toCharArray());
        kmf.init(keyStore, pass.toCharArray());
        return SslContextBuilder.forServer(kmf).build();
    }
}

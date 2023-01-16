package com.byyw.nettyHttpServer.entity;

import lombok.Data;

/**
 * 微服务
 */
@Data
public abstract class MiModule{
    protected String name;
    protected String attr;
    protected boolean run;
    public MiModule(){}
    public MiModule(String name){
        this.name = name;
    }
    public MiModule(String name,String attr){
        this.name = name;
        this.attr = attr;
    }
    public abstract void construct();
    public abstract void start();
    public abstract void stop();
}

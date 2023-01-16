package com.byyw.nettyHttpServer.controller;

import org.springframework.stereotype.Component;

import com.byyw.nettyHttpServer.annotation.Uri;
import com.byyw.nettyHttpServer.entity.HttpParams;
import com.byyw.nettyHttpServer.entity.MController;
import com.byyw.nettyHttpServer.enums.ResultCode;
import com.byyw.nettyHttpServer.exception.ParamErrorException;
import com.byyw.nettyHttpServer.util.NettyHttpResponseUtils;

import cn.hutool.json.JSONObject;

@Component
public class TestController extends MController<Object> {
    
    public TestController() throws Exception {
        super();
    }

    @Override
    public void work(HttpParams httpParams) throws Exception {
        try {
            String uri = httpParams.getFullHttpRequest().uri();
            int i = uri.indexOf("?");
            if (i >= 0) {
                uri = uri.substring(0, i);
            }
            if (methods.containsKey(uri)) {
                methods.get(uri).invoke(this, new Object[] { httpParams });
            } else {
                JSONObject json = new JSONObject();
                json.set("code", ResultCode.NO_INTERFACE);
                json.set("msg", "接口不存在");
                NettyHttpResponseUtils.response(httpParams, json);
            }
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            if (e.getCause() instanceof ParamErrorException) {
                json.set("code", ResultCode.NO_PARAM);
                json.set("msg", e.getCause().getMessage());
            } else {
                json.set("code", ResultCode.EXCEPTION);
                json.set("msg", e.toString());
                e.printStackTrace();
            }
            NettyHttpResponseUtils.response(httpParams, json);
        }
    }
    
    @Uri("/test/test")
    public void test(HttpParams hp){
        System.out.println(new JSONObject(hp.getParams()));
        JSONObject json = new JSONObject();
        json.set("code", ResultCode.SUCCESS);
        NettyHttpResponseUtils.response(hp, json);
    }
}

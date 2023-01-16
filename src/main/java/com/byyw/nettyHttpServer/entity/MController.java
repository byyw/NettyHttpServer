package com.byyw.nettyHttpServer.entity;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.byyw.nettyHttpServer.annotation.Uri;

import cn.hutool.json.JSONObject;
import lombok.Data;

@Data
public abstract class MController<T> {
    protected Map<String, Method> methods = null;
    public MController() throws Exception{
        methods = new HashMap<>();
        Method[] ms = this.getClass().getDeclaredMethods();
        Uri u = null;
        for (int i = 0; i < ms.length; i++) {
            if (ms[i].isAnnotationPresent(Uri.class)) {
                u = ms[i].getAnnotation(Uri.class);
                String[] url = u.value();
                for (int j = 0; j < url.length; j++) {
                    if (methods.containsKey(url[j])) {
                        throw new Exception("url不能重复:"+url[j]);
                    }
                    methods.put(url[j], ms[i]);
                }
            }
        }
    }
    public Method getMethod(String key){
        return this.methods.get(key);
    }
    public abstract void work(HttpParams httpParams) throws Exception;
    
    protected interface ResponseEvent<T>{
        Object excite(T res, JSONObject json);
    }
}

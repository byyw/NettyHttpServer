package com.byyw.nettyHttpServer.entity;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.byyw.nettyHttpServer.exception.ParamErrorException;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.ReferenceCountUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
public class HttpParams {
    private ChannelHandlerContext ctx;
    private FullHttpRequest fullHttpRequest;
    private Map<String,Object> params;

    public HttpParams(){}
    public HttpParams(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest){
        this.ctx = ctx;
        this.fullHttpRequest = fullHttpRequest;
        setParams(fullHttpRequest);
    }
    
    /** 
     * @param fullHttpRequest
     */
    public void setParams(FullHttpRequest fullHttpRequest){
        // 获取uri参数
        this.params = getGetParamsFromChannel(fullHttpRequest);
        // 获取from数据
        if (fullHttpRequest.method() == HttpMethod.POST) {
            Map<String,Object> t = getPostParamsFromChannel(fullHttpRequest);
            if(t != null){
                if(this.params == null) this.params = t;
                else this.params.putAll(t);
            }
        }
    }
    
    /** 
     * @param fullHttpRequest
     * @return Map<String, Object>
     */
    /*
     * 获取GET方式传递的参数
     */
    private Map<String, Object> getGetParamsFromChannel(FullHttpRequest fullHttpRequest) {

        Map<String, Object> params = new HashMap<String, Object>();
        
        QueryStringDecoder decoder = new QueryStringDecoder(fullHttpRequest.uri());
        Map<String, List<String>> paramList = decoder.parameters();
        for (Map.Entry<String, List<String>> entry : paramList.entrySet()) {
            params.put(entry.getKey(), entry.getValue().get(0));
        }
        return params;
    }

    
    /** 
     * @param fullHttpRequest
     * @return Map<String, Object>
     */
    /*
     * 获取POST方式传递的参数
     */
    private Map<String, Object> getPostParamsFromChannel(FullHttpRequest fullHttpRequest) {

        Map<String, Object> params = new HashMap<String, Object>();

        if (fullHttpRequest.method() == HttpMethod.POST) {
            // 处理POST请求
            String strContentType = fullHttpRequest.headers().get("Content-Type");
            if(strContentType == null){
                return null;
            } else {
                strContentType = strContentType.trim();
            }
            if (strContentType.contains("x-www-form-urlencoded")) {
                params = getFormParams(fullHttpRequest);
            } else if (strContentType.contains("application/json")) {
                try {
                    params = getJSONParams(fullHttpRequest);
                } catch (UnsupportedEncodingException e) {
                    return null;
                }
            } else if (strContentType.contains("multipart/form-data")){
                try {
                    params = getMultParams(fullHttpRequest);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
                return null;
            }
            return params;
        } else {
            return null;
        }
    }

    
    /** 
     * @param fullHttpRequest
     * @return Map<String, Object>
     */
    /*
     * 解析from表单数据（Content-Type = x-www-form-urlencoded）
     */
    private Map<String, Object> getFormParams(FullHttpRequest fullHttpRequest) {
        Map<String, Object> params = new HashMap<String, Object>();

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), fullHttpRequest);
        List<InterfaceHttpData> postData = decoder.getBodyHttpDatas();

        JSONObject jo = new JSONObject();
        for (InterfaceHttpData data : postData) {
            if (data.getHttpDataType() == HttpDataType.Attribute) {
                MemoryAttribute attribute = (MemoryAttribute) data;
                String name = attribute.getName();
                String[] ns = name.split("\\[");
                for(int i=0;i<ns.length;i++){
                    ns[i] = ns[i].replace("]","");
                }
                setJSONObject(jo,ns,0,attribute.getValue());
            } else if(data.getHttpDataType() == HttpDataType.FileUpload){
                FileUpload fileUpload = (FileUpload) data;
                params.put(fileUpload.getName(), fileUpload);
            }
        }
        for (Object key : jo.keySet()) {
            params.put(key.toString(), jo.get(key));
        }
        return params;
    }

    
    /** 
     * @param jo
     * @param ns
     * @param i
     * @param value
     */
    private void setJSONObject(JSONObject jo, String[] ns,int i, String value){
        if(i == ns.length-1){
            jo.set(ns[i], value);
            return;
        }
        if(!jo.containsKey(ns[i])){
            if(ns[i+1].equals("") || StringUtils.isNumeric(ns[i+1])){
                jo.set(ns[i], new JSONArray());
            } else {
                jo.set(ns[i], new JSONObject());
            }
        }
        Object obj = jo.get(ns[i]);
        if(obj instanceof JSONObject){
            setJSONObject((JSONObject)obj,ns,i+1,value);
        } else {
            setJSONArray((JSONArray)obj,ns,i+1,value);
        }
    }
    
    /** 
     * @param ja
     * @param ns
     * @param i
     * @param value
     */
    private void setJSONArray(JSONArray ja, String[] ns,int i, String value){
        if(i == ns.length-1){
            if(ns[i].equals("")){
                ja.set(value);
            } else {
                ja.set(Integer.valueOf(ns[i]),value);
            }
            return;
        }
        Integer index = Integer.valueOf(ns[i]);
        while(index > ja.size()-1){
            ja.put(null);
        }
        if(ja.get(index) == null){
            if(ns[i+1].equals("") || StringUtils.isNumeric(ns[i+1])){
                ja.set(index, new JSONArray());
            } else {
                ja.set(index, new JSONObject());
            }
        }
        Object obj = ja.get(index);
        if(obj instanceof JSONObject){
            setJSONObject((JSONObject)obj,ns,i+1,value);
        } else {
            setJSONArray((JSONArray)obj,ns,i+1,value);
        }
    }


    
    /** 
     * @param fullHttpRequest
     * @return Map<String, Object>
     * @throws UnsupportedEncodingException
     */
    /*
     * 解析json数据（Content-Type = application/json）
     */
    private Map<String, Object> getJSONParams(FullHttpRequest fullHttpRequest) throws UnsupportedEncodingException {
        Map<String, Object> params = new HashMap<String, Object>();

        ByteBuf content = fullHttpRequest.content();
        byte[] reqContent = new byte[content.readableBytes()];
        content.readBytes(reqContent);
        String strContent = new String(reqContent, "UTF-8");
        strContent = strContent.trim();
        if(strContent.length()==0 || strContent.equals("null")){
            strContent = "{}";
        }
        JSONObject jsonParams = JSONUtil.parseObj(strContent);
        for (Object key : jsonParams.keySet()) {
            params.put(key.toString(), jsonParams.get(key));
        }

        return params;
    }
    
    /** 
     * @param fullHttpRequest
     * @return Map<String, Object>
     * @throws UnsupportedEncodingException
     */
    /*
     * 解析json数据（Content-Type = multipart/form-data）
     */
    private Map<String, Object> getMultParams(FullHttpRequest fullHttpRequest) throws UnsupportedEncodingException {
        Map<String, Object> params = new HashMap<String, Object>();
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(
                                            new DefaultHttpDataFactory(DefaultHttpDataFactory.MAXSIZE), 
                                            fullHttpRequest,
                                            Charset.forName("utf-8"));
        List<InterfaceHttpData> datas = decoder.getBodyHttpDatas();
        try {
            for(InterfaceHttpData data:datas){
                if(data.getHttpDataType() == HttpDataType.Attribute){
                    Attribute attribute = (Attribute)data;
                    // TODO 待优化，关于base64字符串
                    params.put(attribute.getName(),attribute.getValue());
                } else if(data.getHttpDataType() == HttpDataType.FileUpload){
                    FileUpload fileUpload = (FileUpload) data;
                    params.put(fileUpload.getName(), fileUpload);
                }
            }
        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(fullHttpRequest);
            decoder.destroy();
            e.printStackTrace();
        }
        return params;
    }
    
    /** 
     * @return boolean
     */
    public boolean isEmpty(){
        return this.params==null || this.params.isEmpty();
    }
    
    
    /** 
     * @param key
     * @return Object
     * @throws ParamErrorException
     */
    public Object getParamObj(String key) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            throw new ParamErrorException("参数:"+key+"不能为空");
        return obj;
    }
    
    /** 
     * @param key
     * @param o
     * @return Object
     */
    public Object getParamObj(String key,Object o){
        Object obj = this.params.get(key);
        if(obj == null)
            return o;
        return obj;
    }

    
    /** 
     * @param key
     * @return String
     * @throws ParamErrorException
     */
    public String getParamStr(String key) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            throw new ParamErrorException("参数:"+key+"不能为空");
        String str = String.valueOf(obj);
        if(str.equals(""))
            throw new ParamErrorException("参数:"+key+"不能为空");
        return str;
    }
    
    /** 
     * @param key
     * @param s
     * @return String
     * @throws ParamErrorException
     */
    public String getParamStr(String key,String s) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            return s;
        String str = String.valueOf(obj);
        if(str.equals(""))
            return s;
        return str;
    }

    
    /** 
     * @param key
     * @return Integer
     * @throws ParamErrorException
     */
    public Integer getParamInt(String key) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            throw new ParamErrorException("参数:"+key+"不能为空");
        try {
            return Integer.valueOf(String.valueOf(obj));
        } catch (NumberFormatException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }
    
    /** 
     * @param key
     * @param i
     * @return Integer
     * @throws ParamErrorException
     */
    public Integer getParamInt(String key,Integer i) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            return i;
        try {
            return Integer.valueOf(String.valueOf(this.params.get(key)));
        } catch (NumberFormatException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }

    
    /** 
     * @param key
     * @return Long
     * @throws ParamErrorException
     */
    public Long getParamLong(String key) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            throw new ParamErrorException("参数:"+key+"不能为空");
        try {
            return Long.valueOf(String.valueOf(obj));
        } catch (NumberFormatException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }
    
    /** 
     * @param key
     * @param i
     * @return Long
     * @throws ParamErrorException
     */
    public Long getParamLong(String key,Long i) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            return i;
        try {
            return Long.valueOf(String.valueOf(this.params.get(key)));
        } catch (NumberFormatException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }

    
    /** 
     * @param key
     * @return Double
     * @throws ParamErrorException
     */
    public Double getParamDouble(String key) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            throw new ParamErrorException("参数:"+key+"不能为空");
        try {
            return Double.valueOf(String.valueOf(obj));
        } catch (NumberFormatException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }
    
    /** 
     * @param key
     * @param i
     * @return Double
     * @throws ParamErrorException
     */
    public Double getParamDouble(String key,Double i) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            return i;
        try {
            return Double.valueOf(String.valueOf(this.params.get(key)));
        } catch (NumberFormatException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }

    
    /** 
     * @param key
     * @return Boolean
     * @throws ParamErrorException
     */
    public Boolean getParamBool(String key) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            throw new ParamErrorException("参数:"+key+"不能为空");
        try {
            return Boolean.valueOf(String.valueOf(obj));
        } catch (NumberFormatException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }
    
    /** 
     * @param key
     * @param i
     * @return Boolean
     * @throws ParamErrorException
     */
    public Boolean getParamBool(String key,Boolean i) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            return i;
        try {
            return Boolean.valueOf(String.valueOf(this.params.get(key)));
        } catch (NumberFormatException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }

    
    /** 
     * @param key
     * @return JSONObject
     * @throws ParamErrorException
     */
    public JSONObject getParamJSONObject(String key) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            throw new ParamErrorException("参数:"+key+"不能为空");
        try {
            return (JSONObject)obj;
        } catch (ClassCastException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }
    
    /** 
     * @param key
     * @param i
     * @return JSONObject
     * @throws ParamErrorException
     */
    public JSONObject getParamJSONObject(String key, JSONObject i) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            return i;
        try {
            return (JSONObject)obj;
        } catch (ClassCastException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }

    
    /** 
     * @param key
     * @return JSONArray
     * @throws ParamErrorException
     */
    public JSONArray getParamJSONArray(String key) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            throw new ParamErrorException("参数:"+key+"不能为空");
        try {
            return (JSONArray)obj;
        } catch (ClassCastException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }
    
    /** 
     * @param key
     * @param i
     * @return JSONArray
     * @throws ParamErrorException
     */
    public JSONArray getParamJSONArray(String key,JSONArray i) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            return i;
        try {
            return (JSONArray)obj;
        } catch (ClassCastException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }
    
    
    /** 
     * @param key
     * @return FileUpload
     * @throws ParamErrorException
     */
    public FileUpload getParamFile(String key) throws ParamErrorException{
        Object obj = this.params.get(key);
        if(obj == null)
            throw new ParamErrorException("参数:"+key+"不能为空");
        try {
            return (FileUpload)obj;
        } catch (ClassCastException e) {
            throw new ParamErrorException("参数:"+key+"格式错误");
        }
    }

    
    /** 
     * @param key
     * @return boolean
     */
    public boolean isNull(String key){
        return !this.params.containsKey(key);
    }
    
    /** 
     * @param key
     * @return boolean
     */
    public boolean isNotNull(String key){
        return this.params.containsKey(key);
    }
}

package com.byyw.nettyHttpServer.enums;

public class ResultCode{
    /**
     * 0
     * 成功
     */
    public static int SUCCESS = 0;
    /**
     * 404
     * 接口不存在
     */
    public static int NO_INTERFACE = 404;
    /**
     * -1
     * 连接不存在
     */
    public static int NO_CHANNEL = -1;
    /**
     * -2
     * 发送失败
     */
    public static int SEND_FAILURE = -2;
    /**
     * -3
     * 回应超时
     */
    public static int TIMEOUT = -3;
    /**
     * -4
     * 回应格式错误
     */
    public static int RESPONSE_ERROR = -4;
    /**
     * -5
     * 业务性错误
     */
    public static int OTHER_ERROR = -5;
    /**
     * -6
     * 无服务/服务未启动
     */
    public static int NO_SERVER = -6;
    /**
     * -7
     * 未传参数
     */
    public static int NO_PARAM = -7;
    /**
     * -8
     * 异常错误
     */
    public static int EXCEPTION = -8;
    /**
     * -9
     * 文件打开失败
     */
    public static int OPEN_FILE_FAILED = -9;
    /**
     * 10000
     * 视频打开失败
     */
    public static int MEDIA_SETUP_FAILED = 10000;
    /**
     * 10001
     * 媒体流不存在
     */
    public static int MEDIA_NOT_FIND = 10001;
}

package com.byyw.nettyHttpServer.entity;

import java.io.File;

import lombok.Data;

@Data
public class HFile {
    private File file;
    private String name;
    private String contentType;
}

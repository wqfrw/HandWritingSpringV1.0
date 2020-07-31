package com.wqfrw.service.impl;

import com.framework.annotation.MyService;
import com.wqfrw.service.ITestService;

@MyService
public class TestServiceImpl implements ITestService {

    @Override
    public String query(String name) {
        return "hello  " + name + "!";
    }

}

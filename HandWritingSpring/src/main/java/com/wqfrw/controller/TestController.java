package com.wqfrw.controller;

import com.framework.annotation.MyAutowired;
import com.framework.annotation.MyController;
import com.framework.annotation.MyRequestMapping;
import com.framework.annotation.MyRequestParam;
import com.wqfrw.service.ITestService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/test")
public class TestController {

    @MyAutowired
    private ITestService testService;

    @MyRequestMapping("/query")
    public  void query(HttpServletRequest req, HttpServletResponse resp, @MyRequestParam("name") String name){
        String result = testService.query(name);
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MyRequestMapping("/add")
    public  void add(HttpServletRequest req, HttpServletResponse resp, @MyRequestParam("name") String name){
        System.out.println("add");
    }

    @MyRequestMapping("/remove")
    public  void remove(HttpServletRequest req, HttpServletResponse resp, @MyRequestParam("name") String name){
        System.out.println("remove");
    }

}

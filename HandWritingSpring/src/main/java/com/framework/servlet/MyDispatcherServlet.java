package com.framework.servlet;


import com.framework.annotation.MyAutowired;
import com.framework.annotation.MyController;
import com.framework.annotation.MyRequestMapping;
import com.framework.annotation.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

public class MyDispatcherServlet extends HttpServlet {

    //IoC容器
    private Map<String, Object> ioc = new HashMap<>();

    //加载配置文件对象
    private Properties contextConfig = new Properties();

    //扫描到的类名带包路径
    private List<String> classNameList = new ArrayList<>();

    //url与方法的映射 也就是请求分发器
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception Detail:" + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //加载配置文件 拿到需要扫描的包路径
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //实例化Bean到IoC容器
        doInstance();

        //依赖注入(DI)
        doAutowired();

        //初始化HandlerMapping url和对应方法的键值对
        doInitHandlerMapping();

        //初始化完成
        System.out.println("MySpring framework is init!");
    }

    /**
     * 功能描述: 接收到浏览器的请求，执行方法
     *
     * @创建人: Peter
     * @创建时间: 2020年07月28日 20:54:04
     * @param req
     * @param resp
     * @return: void
     **/
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"");

        //根据请求路径如果没有找到对应的方法则抛出404错误
        if (!handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!");
            return;
        }

        Map<String,String[]> params = req.getParameterMap();
        Method method = handlerMapping.get(url);
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        //方法调用
        method.invoke(ioc.get(beanName),new Object[]{req,resp,params.get("name")[0]});
    }

    /**
     * 功能描述: 初始化HandlerMapping
     *
     * @创建人: Peter
     * @创建时间: 2020年07月28日 20:56:09
     * @param
     * @return: void
     **/
    private void doInitHandlerMapping() {
        ioc.forEach((k, v) -> {
            Class<?> clazz = v.getClass();

            //加了MyController注解的类才操作
            if (clazz.isAnnotationPresent(MyController.class)) {

                String baseUrl = "";
                //如果类上面加了MyRequestMapping注解,则需要拿到url进行拼接
                if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                    MyRequestMapping annotation = clazz.getAnnotation(MyRequestMapping.class);
                    baseUrl = annotation.value();
                }

                //获取所有public修饰的方法
                Method[] methods = clazz.getMethods();
                //过滤拿到所有MyRequestMapping注解的方法,put到handlerMapping中
                String finalBaseUrl = baseUrl;
                Stream.of(methods)
                        .filter(m -> m.isAnnotationPresent(MyRequestMapping.class))
                        .forEach(m -> {
                            MyRequestMapping annotation = m.getAnnotation(MyRequestMapping.class);
                            String url = (finalBaseUrl + annotation.value()).replaceAll("/+", "/");
                            handlerMapping.put(url, m);
                        });
            }

        });
    }

    /**
     * 功能描述: 依赖注入(DI)
     *
     * @创建人: Peter
     * @创建时间: 2020年07月28日 20:55:57
     * @param
     * @return: void
     **/
    private void doAutowired() {
        //循环IoC容器中所管理的对象 注入属性
        ioc.forEach((k, v) -> {
            //拿到bean所有的字段 包括private、public、protected、default
            Field[] Fields = v.getClass().getDeclaredFields();
            //过滤拿到所有加了MyAutowired注解的字段并循环注入
            Stream.of(Fields)
                    .filter(f -> f.isAnnotationPresent(MyAutowired.class))
                    .forEach(f -> {

                        MyAutowired annotation = f.getAnnotation(MyAutowired.class);
                        String beanName = annotation.value().trim();
                        if ("".equals(beanName)) {
                            beanName = f.getType().getName();
                        }

                        //强制访问
                        f.setAccessible(true);

                        try {
                            //赋值
                            f.set(v, ioc.get(beanName));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    });
        });
    }

    /**
     * 功能描述: 实例化bean至IoC容器
     *
     * @创建人: Peter
     * @创建时间: 2020年07月28日 20:55:39
     * @param
     * @return: void
     **/
    private void doInstance() {
        classNameList.forEach(v -> {
            try {
                Class<?> clazz = Class.forName(v);

                //只初始化加了MyController注解和MyService注解的类
                if (clazz.isAnnotationPresent(MyController.class)) {
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(MyService.class)) {
                    // 1.默认首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());

                    // 2.自定义beanName
                    MyService myService = clazz.getAnnotation(MyService.class);
                    if (!"".equals(myService.value())) {
                        beanName = myService.value();
                    }

                    // 3.如果是接口 必须创建实现类的实例
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i)) {
                            throw new Exception("This beanName is exists!");
                        }
                        beanName = i.getName();
                    }
                    //将实例化的对象放入IoC容器中
                    ioc.put(beanName, clazz.newInstance());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 功能描述: 扫描相关的类 加入到classNameList
     *
     * @创建人: Peter
     * @创建时间: 2020年07月28日 20:55:10
     * @param scanPackage
     * @return: void
     **/
    private void doScanner(String scanPackage) {
        //获取根目录  拿到com.wqfry替换成/com/wqfrw
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classFile = new File(url.getFile());
        for (File file : classFile.listFiles()) {
            //如果file是文件夹  则递归调用
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                //如果非class文件 则跳过
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = (scanPackage + "." + file.getName()).replace(".class", "");
                //类名+包路径放入到类名集合中  方便后续实例化
                classNameList.add(className);
            }
        }
    }

    /**
     * 功能描述: 加载配置文件
     *
     * @创建人: Peter
     * @创建时间: 2020年07月28日 20:54:57
     * @param contextConfigLocation
     * @return: void
     **/
    private void doLoadConfig(String contextConfigLocation) {
        InputStream resource = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            //加载配置文件
            contextConfig.load(resource);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //关闭文件流
            if (resource != null) {
                try {
                    resource.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 首字母转小写
     *
     * @param className
     * @return
     */
    private String toLowerFirstCase(String className) {
        char[] chars = className.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
}

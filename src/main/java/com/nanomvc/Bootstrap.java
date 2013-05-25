package com.nanomvc;

import com.nanomvc.exceptions.ControllerException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
//import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@MultipartConfig(location = "/tmp")
public class Bootstrap extends HttpServlet
{
    private static Logger _log = LoggerFactory.getLogger(Bootstrap.class);
    
    private static final String ActionPrefix = "do";
    private static final String DefaultAction = "index";
    private static final String InitMethod = "init";
    private static final String ConfigMethod = "config";
    private static final String FlushMethod = "flush";
    private static final String RoutesMethod = "routes";
    private HttpServletRequest request;
    private HttpServletResponse response;
    protected Map<String, Object> files;
    protected Map<String, String> fields;
    private String viewsPath;
    private String controllersPath;
    private String defaultController;
    private String routerClass;
    private StringBuffer output;
    private Long startTime;

    public void init(ServletConfig config)
            throws ServletException {
        this.viewsPath = config.getInitParameter("viewsPath");
        this.controllersPath = config.getInitParameter("controllersPath");
        this.defaultController = config.getInitParameter("defaultController");
        this.routerClass = config.getInitParameter("routerClass");
        super.init(config);
        try {
            HibernateUtil.setConfigurationFile(new File(getServletContext().getRealPath("/WEB-INF/conf/hibernate.cfg.xml")));
        } catch (Exception ex) {
        }
    }

    private void config() {
        try {
            this.request.setCharacterEncoding("UTF-8");
            Boolean isMultipart = Boolean.valueOf(ServletFileUpload.isMultipartContent(this.request));
            if (isMultipart.booleanValue()) {
                FileItemFactory factory = new DiskFileItemFactory();
                ServletFileUpload upload = new ServletFileUpload(factory);
                try {
                    List items = upload.parseRequest(this.request);
                    Iterator iterator = items.iterator();
                    while (iterator.hasNext()) {
                        FileItem item = (FileItem) iterator.next();
                        if (!item.isFormField()) {
                            if (this.files == null) {
                                this.files = new HashMap();
                            }
                            String field = item.getFieldName();
                            if (this.files.containsKey(field)) {
                                if ((this.files.get(field) instanceof List)) {
                                    List files = (List) this.files.get(field);
                                    files.add(item);
                                } else {
                                    List files = new ArrayList();
                                    files.add(item);
                                    this.files.put(field, files);
                                }
                            } else {
                                this.files.put(item.getFieldName(), item);
                            }
                        } else {
                            if (this.fields == null) {
                                this.fields = new HashMap();
                            }
                            this.fields.put(item.getFieldName(), item.getString("UTF-8"));
                        }
                    }
                } catch (Exception e) {
                    _log.error("exception", e);
                }
            } else {
                this.fields = null;
                this.files = null;
            }
        } catch (Exception ex) {
            _log.error("exception", ex);
        }
    }

    protected void processRequest() throws ServletException, IOException {
        config();

        this.startTime = Long.valueOf(System.currentTimeMillis());
        this.output = null;

        String controllerClassName = null;
        String controllerMethodName = null;
        String path = this.request.getServletPath();

        RequestHandler handler = new RequestHandler(path, this.routerClass);
        Request req = handler.parseRequest();
        req.setDefaultController(this.defaultController);

        controllerClassName = new StringBuilder().append(this.controllersPath).append(".").append(req.getControllerClassName()).toString();
        controllerMethodName = req.getControllerMethodName();
        List args = req.getArguments();
        try {
            ClassLoader classLoader = getClass().getClassLoader();

            Class cc = classLoader.loadClass(controllerClassName);
            Constructor constructor = cc.getConstructor(new Class[0]);
            Object co = constructor.newInstance(new Object[0]);
            Method method = null;
            try {
                method = cc.getMethod("config", new Class[]{HttpServletRequest.class, HttpServletResponse.class, ServletContext.class, String.class, String.class, String.class, Router.class, Map.class, Map.class});

                method.invoke(co, new Object[]{this.request, this.response, getServletContext(), this.viewsPath, req.getController(), req.getAction(), handler.getRouter(), this.files, this.fields});

                method = cc.getMethod("init", new Class[0]);
                method.invoke(co, new Object[0]);

                Method[] allMethods = cc.getDeclaredMethods();
                method = null;
                for (Method m : allMethods) {
                    if (m.getName().equalsIgnoreCase(controllerMethodName)) {
                        method = m;
                    }
                }
                controllerMethodName = method.getName();

                if (method == null) {
                    throw new NoSuchMethodException();
                }

                Class[] params = method.getParameterTypes();
                Object[] arguments = new Object[params.length];
                for (int i = 0; i < params.length; i++) {
                    try {
                        switch (params[i].getSimpleName()) {
                            case "String":
                                arguments[i] = ((String) args.get(i));
                                break;
                            case "Integer":
                                arguments[i] = Integer.valueOf((String) args.get(i));
                                break;
                            case "Long":
                                arguments[i] = Long.valueOf((String) args.get(i));
                                break;
                            case "Float":
                                arguments[i] = Float.valueOf((String) args.get(i));
                                break;
                            case "Double":
                                arguments[i] = Double.valueOf((String) args.get(i));
                                break;
                            case "Boolean":
                                arguments[i] = Boolean.valueOf((String) args.get(i));
                                break;
                            default:
                                arguments[i] = args.get(i);
                        }
                    } catch (Exception ex) {
                        arguments[i] = null;
                    }
                }

                method = cc.getDeclaredMethod(controllerMethodName, params);

                method.invoke(co, arguments);
                method = cc.getMethod("flush", new Class[0]);
                method.setAccessible(true);
                method.invoke(co, new Object[0]);

                co = null;
                cc = null;
            } catch (NoSuchMethodException | IllegalArgumentException | IllegalAccessException e) {
                _log.error(new StringBuilder().append("Undefined action: ").append(path).toString());
                _log.error(e.toString());
                error(new StringBuilder().append("Undefined action: ").append(req.getAction()).append(" in <b>").append(controllerClassName).append("</b>").toString());
            } catch (InvocationTargetException e) {
                if ((e.getCause() instanceof ControllerException)) {
                    error(new StringBuilder().append("Error: ").append(e.getCause().getMessage()).toString());
                } else {
                    error(e.getCause());
                }
            } catch (Exception e) {
                error(e);
            }
        } catch (InvocationTargetException ex) {
            if ((ex.getCause() instanceof ControllerException)) {
                error(new StringBuilder().append("Error: ").append(ex.getCause().getMessage()).toString());
            } else {
                error(ex.getCause());
            }
        } catch (ClassNotFoundException | IllegalArgumentException | IllegalAccessException | InstantiationException | SecurityException | NoSuchMethodException ex) {
            _log.error(new StringBuilder().append("Undefined controller: ").append(path).toString());
            _log.error(ex.toString());
            error(new StringBuilder().append("Undefined controller: ").append(req.getControllerClassName()).toString());
        }
        flush();
        log();
    }

    private void log() {
        Long time = Long.valueOf(System.currentTimeMillis() - this.startTime.longValue());
        String path = this.request.getServletPath();
        if (time.longValue() > 100L) {
            try {
                JSONObject req = new JSONObject();
                JSONObject arr = new JSONObject();
                arr.put("path", path);
                arr.put("time", time);
                req.put("request", arr);
                _log.info(req.toString());
            } catch (JSONException ej) {
            }
        }
    }

    private void error(Throwable error) {
        StringBuilder message = new StringBuilder();
        message.append("Exception: ").append(error.toString()).append("<br />\n");

        if (error.getMessage() != null) {
            message.append(error.getMessage()).append("<br />\n");
        }
        message.append("<br />\n");

        StackTraceElement[] trace = error.getStackTrace();
        for (int i = 0; i < trace.length; i++) {
            message.append(trace[i]).append("<br />\n");
        }
        error(message.toString());
    }

    private void error(String error) {
        this.request.setAttribute("error", error);
        RequestDispatcher dispatcher = this.request.getRequestDispatcher("/WEB-INF/views/error.jsp");
        try {
            dispatcher.include(this.request, this.response);
        } catch (ServletException | IOException ex) {
        }
    }

    private void flush() throws IOException {
        if (this.output == null) {
            this.response.getWriter().flush();
            this.response.getWriter().close();
            return;
        }

        this.response.getWriter().print(this.output.toString());
        this.response.getWriter().flush();
        this.response.getWriter().close();
    }

    protected final void output(String value) {
        if (this.output == null) {
            this.output = new StringBuffer();
        }
        this.output.append(value);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        this.request = request;
        this.response = response;
        processRequest();
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        this.request = request;
        this.response = response;
        processRequest();
    }

    public String getServletInfo() {
        return "";
    }
}
package club.hsspace.sqlplugin;

import club.hsspace.whypps.action.Injection;
import club.hsspace.whypps.framework.app.annotation.DataParam;
import club.hsspace.whypps.framework.manage.EventListener;
import club.hsspace.whypps.framework.manage.EventManage;
import club.hsspace.whypps.framework.manage.RunningSpace;
import club.hsspace.whypps.framework.manage.SpaceManage;
import club.hsspace.whypps.framework.manage.event.AfterRequestHandleEvent;
import club.hsspace.whypps.framework.manage.event.AppStartEvent;
import club.hsspace.whypps.framework.manage.event.BeforeRequestHandleEvent;
import club.hsspace.whypps.framework.manage.event.FrameworkStartedEvent;
import club.hsspace.whypps.framework.plugin.ScanPlugin;
import club.hsspace.whypps.manage.ContainerManage;
import club.hsspace.whypps.model.ContainerClosable;
import club.hsspace.whypps.util.NumberTools;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * @ClassName: ParameterHandle
 * @CreateTime: 2022/7/15
 * @Comment: 事件处理器
 * @Author: Qing_ning
 * @Mail: 1750359613@qq.com
 */
@ScanPlugin
public class ParameterHandle implements ContainerClosable {

    private static final Logger logger = LoggerFactory.getLogger(ParameterHandle.class);

    public Map<ClassLoader, SqlSessionFactory> factoryMap = new HashMap<>();

    private RunningSpace pluginRunningSpace;

    @EventListener
    public void appStart(FrameworkStartedEvent event, RunningSpace runningSpace, ContainerManage containerManage) throws IOException, ParserConfigurationException, SAXException, TransformerException {

        this.pluginRunningSpace = runningSpace;

        Properties prop = new Properties();
        InputStream propInputStream = runningSpace.getInputStream("config.properties");
        prop.load(propInputStream);

        int property = Integer.parseInt(prop.getProperty("idworker.mcode"));
        IdWorker idWorker = new IdWorker((short) property);
        containerManage.registerObject(idWorker);

        logger.info("SQLPlugin加载成功，机器ID({})...", property);

        containerManage.dpi(SqlSessionFactory.class, dpi -> {
            try {
                return getSqlSessionFactory(dpi.getDeclaringClass().getClassLoader());
            } catch (IOException | ParserConfigurationException | SAXException | TransformerException e) {
                throw new RuntimeException(e);
            }
        });

        logger.info("注册SqlSessionFactor DPI成功");
    }

    private Map<Object, SqlSession> sqlSessionMap = new HashMap<>();

    @Injection
    private SpaceManage spaceManage;

    private SqlSessionFactory getSqlSessionFactory(ClassLoader classLoader) throws IOException, ParserConfigurationException, SAXException, TransformerException {
        SqlSessionFactory sqlSessionFactory = factoryMap.get(classLoader);
        if (sqlSessionFactory == null) {
            Thread.currentThread().setContextClassLoader(classLoader);
            RunningSpace runningSpace = spaceManage.getRunningSpace(classLoader);

            InputStream sqlInputStream = pluginRunningSpace.getInputStream("mybatis-config.xml");

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(sqlInputStream);
            Element configuration = doc.getDocumentElement();

            Element mappers = doc.createElement("mappers");
            configuration.appendChild(mappers);

            File mapperFile = runningSpace.getFile("mapper");
            File[] files = mapperFile.listFiles((dir, name) -> name.endsWith(".xml"));
            for (File file : files) {
                Element mapper = doc.createElement("mapper");
                mappers.appendChild(mapper);

                Attr resource = doc.createAttribute("url");
                resource.setValue("file:///" + file);
                mapper.setAttributeNode(resource);
            }

            TransformerFactory tfac = TransformerFactory.newInstance();
            Transformer tra = tfac.newTransformer();
            DOMSource domSource = new DOMSource(configuration);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            StreamResult sr = new StreamResult(os);
            tra.transform(domSource, sr);

            byte[] bytes = os.toByteArray();
            bytes = Arrays.copyOfRange(bytes, 38, bytes.length);
            bytes = NumberTools.bytesMerger("""
                    <?xml version="1.0" encoding="UTF-8" ?>
                    <!DOCTYPE configuration
                            PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
                            "http://mybatis.org/dtd/mybatis-3-config.dtd">
                    """.getBytes(), bytes);

            sqlSessionFactory = new SqlSessionFactoryBuilder().build(new ByteArrayInputStream(bytes));

            factoryMap.put(classLoader, sqlSessionFactory);
        }
        return sqlSessionFactory;
    }

    /*@EventListener
    public void listenAppStartEvent(AppStartEvent appStartEvent, EventManage) throws IOException, ParserConfigurationException, TransformerException, SAXException {
        Method method = appStartEvent.getRunMethod();
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type == SqlSessionFactory.class) {
                appStartEvent.getObjects()[i] = getSqlSessionFactory(method.getDeclaringClass().getClassLoader());
            }
        }

    }*/

    @EventListener
    public void listenBeforeRequestHandleEvent(BeforeRequestHandleEvent event) throws IOException, ParserConfigurationException, TransformerException, SAXException {
        Method runMethod = event.getRunMethod();
        SQLStatement sqlStatement = runMethod.getAnnotation(SQLStatement.class);
        Object[] objects = event.getObjects();
        Parameter[] parameters = runMethod.getParameters();

        Object runObject = event.getObject();

        Map<String, String> param = new HashMap<>();
        boolean cancel = false;

        Class<?> returnType = null;
        int returnIdx = -1;
        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();

            SQLParam sqlParam = parameters[i].getAnnotation(SQLParam.class);
            if (sqlParam != null) {
                if (objects[i] != null)
                    param.put(sqlParam.value(), objects[i].toString());
                else if (sqlStatement.nullCancel())
                    cancel = true;
            }

            SQLReturn sqlReturn = parameters[i].getAnnotation(SQLReturn.class);
            if (sqlReturn != null) {
                returnType = type;
                returnIdx = i;
            }

            //SQLMapper
            SQLMapper sqlMapper = type.getAnnotation(SQLMapper.class);
            if (sqlMapper != null) {
                //二次包装代理SQLMapper (实现1)
                /*objects[i] = Proxy.newProxyInstance(type.getClassLoader(),
                        new Class[]{type}, (proxy, method, args) -> {
                            try (SqlSession session = sqlSessionFactory.openSession()) {
                                Object mapper = session.getMapper(type);
                                Method mapperMethod = mapper.getClass().getMethod(method.getName(), method.getParameterTypes());
                                return mapperMethod.invoke(mapper, args);
                            }
                        });*/

                //开启session，回调事件回收(实现2) (后期调整)
                SqlSession session = getSqlSessionFactory(runObject.getClass().getClassLoader()).openSession();
                Object mapper = session.getMapper(type);
                sqlSessionMap.put(mapper, session);
                objects[i] = mapper;
            }

            if (type == SqlSession.class) {
                objects[i] = getSqlSessionFactory(runObject.getClass().getClassLoader()).openSession();
            }
        }

        if (returnIdx != -1 && cancel != true && sqlStatement != null) {
            try (SqlSession session = getSqlSessionFactory(runObject.getClass().getClassLoader()).openSession()) {
                if (returnType != List.class) {
                    objects[returnIdx] = session.selectOne(sqlStatement.namespace(), param);
                } else {
                    objects[returnIdx] = session.selectList(sqlStatement.namespace(), param);
                }
            }
        }
    }

    @EventListener
    public void listenAfterRequestHandleEvent(AfterRequestHandleEvent event) {
        Method runMethod = event.getRunMethod();
        Object[] objects = event.getObjects();
        Parameter[] parameters = runMethod.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();

            if (type == SqlSession.class) {
                SqlSession sqlSession = (SqlSession) objects[i];
                sqlSession.commit();
                sqlSession.close();
            }

            SQLMapper sqlMapper = type.getAnnotation(SQLMapper.class);
            if (sqlMapper != null) {
                SqlSession sqlSession = sqlSessionMap.get(objects[i]);
                sqlSession.commit();
                sqlSession.close();
            }
        }
    }


    @Override
    public void close() throws IOException {

    }

    @Override
    public void closeTask() {
        for (SqlSession value : sqlSessionMap.values()) {
            value.close();
        }
    }
}

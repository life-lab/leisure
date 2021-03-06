package com.github.lifelab.leisure.common.utils.reflect;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ColorsClassLoader extends ClassLoader {

    private static final ColorsClassLoader fantasyClassLoader = AccessController.doPrivileged(new PrivilegedAction<ColorsClassLoader>() {
        @Override
        public ColorsClassLoader run() {
            return new ColorsClassLoader();
        }
    });
    /**
     * 已被加载的对象
     */
    private static List<String> loadclass = new ArrayList<String>();
    /**
     * 已被卸载的对象
     */
    private static List<String> unloadclass = new ArrayList<String>();
    private Lock lock = new ReentrantLock();
    private DynamicClassLoader classLoader = AccessController.doPrivileged(new PrivilegedAction<DynamicClassLoader>() {
        @Override
        public DynamicClassLoader run() {
            return new DynamicClassLoader(DynamicClassLoader.class.getClassLoader());
        }
    });

    public static ColorsClassLoader getClassLoader() {
        return fantasyClassLoader;
    }

    /**
     * 从文件加载类
     *
     * @param classpath classpath
     * @param classname classname
     * @return Class
     * @throws ClassNotFoundException
     */
    public Class loadClass(String classpath, String classname) throws ClassNotFoundException {
        try {
            lock.lock();
            // 如果对象已经被加载
            if (loadclass.contains(classname)) {
                // 创建新的加载器
                classLoader = new DynamicClassLoader(classLoader);
                loadclass.clear();
            }
            loadclass.add(classname);
            unloadclass.remove(classname);
            return classLoader.loadClass(classpath, classname);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 通过字节码加载类
     *
     * @param classdata classdata
     * @param classname classname
     * @return Class
     * @throws ClassNotFoundException
     */
    public Class loadClass(byte[] classdata, String classname) throws ClassNotFoundException {
        try {
            lock.lock();
            // 如果对象已经被加载
            if (loadclass.contains(classname)) {
                // 创建新的加载器
                classLoader = new DynamicClassLoader(classLoader);
                loadclass.clear();
            }
            loadclass.add(classname);
            unloadclass.remove(classname);
            return classLoader.loadClass(classdata, classname);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 自动加载类
     *
     * @param classname 类路径
     * @return Class
     * @throws ClassNotFoundException
     */
    @Override
    public Class loadClass(String classname) throws ClassNotFoundException {
        if (unloadclass.contains(classname)) {
            throw new ClassNotFoundException(classname);
        }
        return classLoader.loadClass(classname);
    }

    public void removeClass(String className) {
        unloadclass.add(className);
    }
}

package org.xo;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;

public class LauncherHijack {

    public LauncherHijack() {
        new Thread(() -> {
            ClassLoader objClassLoader;

            loop:
            while (true) {
                objClassLoader = (ClassLoader) UIManager.get("ClassLoader");
                if (objClassLoader != null) {
                    for (Package pack : objClassLoader.getDefinedPackages()) {
                        if (pack.getName().equals("net.runelite.client.rs")) {
                            break loop;
                        }
                    }
                }

                try {
                    Thread.sleep(100);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            try {
                Class<?> clazz = getClass((URLClassLoader) objClassLoader);
                clazz.getConstructor().newInstance();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private static Class<?> getClass(URLClassLoader objClassLoader) throws NoSuchMethodException, URISyntaxException, IllegalAccessException, InvocationTargetException, MalformedURLException, ClassNotFoundException {
        Method addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        addUrl.setAccessible(true);

        URI uri = LauncherHijack.class.getProtectionDomain().getCodeSource().getLocation().toURI();

        if (uri.getPath().endsWith("classes/")) {
            uri = uri.resolve("..");
        }

        if (!uri.getPath().endsWith(".jar")) {
            uri = uri.resolve("XoHijacker.jar");
        }

        addUrl.invoke(objClassLoader, uri.toURL());

        return objClassLoader.loadClass(ClientHijack.class.getName());
    }

    public static void main(String[] args) {
        System.setProperty("runelite.launcher.reflect", "true");

        new LauncherHijack();

        try {
            Class<?> clazz = Class.forName("net.runelite.launcher.Launcher");
            clazz.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (Exception ignored) {
            // Ignore
        }
    }

}

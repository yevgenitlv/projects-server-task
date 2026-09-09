package com.ivory.employees;

import com.ivory.employees.config.AppConfig;
import com.ivory.employees.web.WebApp;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Runs the application standalone on an embedded Tomcat, so no container has to be installed:
 *
 * <pre>mvn -f employees-web compile exec:java</pre>
 *
 * <p>The WAR produced by {@code mvn package} deploys to any Jakarta EE 10 web container without
 * this class; {@link WebApp} does the wiring in both cases.
 */
public final class Launcher {

    private static final Logger LOG = Logger.getLogger(Launcher.class.getName());

    private Launcher() {
    }

    public static void main(String[] args) throws LifecycleException {
        WebApp.configureLogging();
        AppConfig config = AppConfig.load();

        Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "employees-web-tomcat");
        try {
            Files.createDirectories(workDir);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot create the Tomcat work directory " + workDir, e);
        }

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(workDir.toString());
        tomcat.setPort(config.port());
        Connector connector = tomcat.getConnector();

        // addContext (rather than addWebapp) keeps annotation scanning out of the picture: the
        // listener below performs the whole registration, exactly as it does inside a container.
        Context context = tomcat.addContext("", new File(".").getAbsolutePath());
        // Tomcat loads the listener through the web application class loader; give that loader this
        // class loader as its parent so the application classes are visible however the JVM was
        // started (java -cp, mvn exec:java, an IDE).
        context.setParentClassLoader(Launcher.class.getClassLoader());
        context.addApplicationListener(WebApp.class.getName());

        tomcat.start();
        // Tomcat only logs a failed bind, it does not stop; refuse to pretend the server is up.
        if (!connector.getState().isAvailable()) {
            tomcat.destroy();
            throw new IllegalStateException("Could not listen on port " + config.port()
                    + " - the port is in use. Start with -Demployees.port=<free port>.");
        }
        LOG.info(() -> "Listening on http://localhost:" + config.port() + "/ - press Ctrl+C to stop");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (LifecycleException e) {
                LOG.warning("Shutdown failed: " + e.getMessage());
            }
        }, "employees-shutdown"));
        tomcat.getServer().await();
    }
}

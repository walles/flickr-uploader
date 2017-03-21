package com.rafali.flickruploader;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import com.rafali.flickruploader.logging.LoggingUtils;
import com.rafali.flickruploader.model.FlickrSet;
import com.rafali.flickruploader.model.Folder;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.tool.Utils;

import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.ref.WeakReference;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import se.emilsjolander.sprinkles.Migration;
import se.emilsjolander.sprinkles.Sprinkles;

public class FlickrUploader extends Application {

    static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrUploader.class);

    private static WeakReference<Context> weakContext;

    @Override
    public void onCreate() {
        super.onCreate();
        FlickrUploader.weakContext = new WeakReference<>(getApplicationContext());
        LoggingUtils.setUpLogging(this);
        getHandler();

        try {
            Sprinkles sprinkles = Sprinkles.init(getApplicationContext());
            Migration initialMigration = new Migration();
            initialMigration.createTable(Media.class);
            initialMigration.createTable(FlickrSet.class);
            initialMigration.createTable(Folder.class);
            sprinkles.addMigration(initialMigration);
            Sprinkles.getDatabase();
        } catch (Exception e) {
            LOG.error("{}", e.getMessage(), e);
        }
    }

    @Nullable
    public static Context getAppContext() {
        return weakContext.get();
    }

    private static Handler handler;

    public static Handler getHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    public static String getLogFilePath() {
        return getAppContext().getFilesDir().getPath() + "/logs/flickruploader.log";
    }

    public static void flushLogs() {
        try {
            Logger logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            LoggerContext lc = logbackLogger.getLoggerContext();
            Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);
            Appender<ILoggingEvent> appender = rootLogger.getAppender("file");
            appender.stop();
            appender.start();
        } catch (Exception e) {
            LOG.error("{}", e.getMessage(), e);
        }
    }

    public static void cleanLogs() {
        try {
            String path = getAppContext().getFilesDir().getPath() + "/logs/old";
            File folder = new File(path);
            if (folder.exists() && folder.isDirectory()) {
                File[] listFiles = folder.listFiles();
                if (listFiles != null) {
                    for (File file : listFiles) {
                        try {
                            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L)
                                file.delete();
                        } catch (Exception e) {
                            LOG.error("???", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("{}", e.getMessage(), e);
        }
    }

    public static long getLogSize() {
        flushLogs();
        return Utils.getFileSize(new File(getAppContext().getFilesDir().getPath() + "/logs/"));
    }
}

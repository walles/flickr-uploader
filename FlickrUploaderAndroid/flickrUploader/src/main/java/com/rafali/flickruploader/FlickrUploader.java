package com.rafali.flickruploader;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.logging.LoggingUtils;
import com.rafali.flickruploader.model.FlickrSet;
import com.rafali.flickruploader.model.Folder;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader2.BuildConfig;

import org.androidannotations.api.BackgroundExecutor;
import org.slf4j.LoggerFactory;

import java.io.File;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import se.emilsjolander.sprinkles.Migration;
import se.emilsjolander.sprinkles.Sprinkles;

public class FlickrUploader extends Application {

    static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrUploader.class);

    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        FlickrUploader.context = getApplicationContext();
        LoggingUtils.setUpLogging(context);
        getHandler();

        try {
            Sprinkles sprinkles = Sprinkles.init(getApplicationContext());
            Migration initialMigration = new Migration();
            initialMigration.createTable(Media.class);
            initialMigration.createTable(FlickrSet.class);
            initialMigration.createTable(Folder.class);
            sprinkles.addMigration(initialMigration);
            Sprinkles.getDatabase();
        } catch (Throwable e) {
            Log.e("Flickr Uploader", e.getMessage(), e);
        }

        BackgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final long previousVersionCode = Utils.getLongProperty(STR.versionCode);
                    if (BuildConfig.VERSION_CODE != previousVersionCode) {
                        Utils.setLongProperty(STR.versionCode, (long) BuildConfig.VERSION_CODE);
                        if (previousVersionCode < 40) {
                            if (FlickrApi.isAuthentified()) {
                                FlickrApi.syncMedia();
                            }
                        }
                    }
                } catch (Throwable e) {
                    LOG.error(ToolString.stack2string(e));
                }
            }
        });
    }

    public static Context getAppContext() {
        return context;
    }

    private static Handler handler;

    public static Handler getHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    public static String getLogFilePath() {
        return context.getFilesDir().getPath() + "/logs/flickruploader.log";
    }

    public static void flushLogs() {
        try {
            Logger logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            LoggerContext lc = logbackLogger.getLoggerContext();
            Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);
            Appender<ILoggingEvent> appender = rootLogger.getAppender("file");
            appender.stop();
            appender.start();
        } catch (Throwable e) {
            Log.e("Flickr Uploader", e.getMessage(), e);
        }
    }

    public static void cleanLogs() {
        try {
            String path = context.getFilesDir().getPath() + "/logs/old";
            File folder = new File(path);
            if (folder.exists() && folder.isDirectory()) {
                File[] listFiles = folder.listFiles();
                if (listFiles != null) {
                    for (File file : listFiles) {
                        try {
                            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L)
                                file.delete();
                        } catch (Throwable e) {
                            LOG.error("???", e);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            Log.e("Flickr Uploader", e.getMessage(), e);
        }
    }

    public static long getLogSize() {
        flushLogs();
        return Utils.getFileSize(new File(context.getFilesDir().getPath() + "/logs/"));
    }
}

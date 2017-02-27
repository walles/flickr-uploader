/*
 * Copyright (C) 2017 Johan Walles <johan.walles@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.rafali.flickruploader.logging;

import static com.rafali.flickruploader.FlickrUploader.getLogFilePath;

import android.content.Context;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.crashlytics.android.answers.LoginEvent;
import com.rafali.flickruploader2.BuildConfig;

import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import io.fabric.sdk.android.Fabric;

public class LoggingUtils {
    public static final boolean IS_CRASHLYTICS_ENABLED = isCrashlyticsEnabled();

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(LoggingUtils.class);

    private LoggingUtils() {
        // Don't let people instantiate this class
    }

    private static boolean isCrashlyticsEnabled() {
        if (EmulatorUtils.IS_ON_EMULATOR) {
            return false;
        }
        if (!EmulatorUtils.IS_ON_ANDROID) {
            return false;
        }

        return true;
    }

    public static void logCustom(CustomEvent event) {
        if (!IS_CRASHLYTICS_ENABLED) {
            return;
        }

        event.putCustomAttribute("App Version", BuildConfig.VERSION_NAME); //NON-NLS
        Answers.getInstance().logCustom(event);
    }

    public static void logLogin() {
        if (!IS_CRASHLYTICS_ENABLED) {
            return;
        }

        LoginEvent event = new LoginEvent();
        event.putCustomAttribute("App Version", BuildConfig.VERSION_NAME); //NON-NLS
        Answers.getInstance().logLogin(event);
    }

    public static void logException(Throwable t) {
        if (!IS_CRASHLYTICS_ENABLED) {
            return;
        }

        Crashlytics.logException(t);
    }

    public static void setUpLogging(Context context) {
        if (IS_CRASHLYTICS_ENABLED) {
            // Note that Crashlytics implicitly adds Answers as well, and mentioning Answers here
            // seems to make the Crashlytics reports stop working. /JW-2017feb27
            Fabric.with(context, new Crashlytics());
        }

        initLogs(context);

        LOG.info("Logging configured: Crashlytics={}, DEBUG={}, Emulator={}, Android={}",
                IS_CRASHLYTICS_ENABLED,
                BuildConfig.DEBUG,
                EmulatorUtils.IS_ON_EMULATOR,
                EmulatorUtils.IS_ON_ANDROID);
    }

    private static void initLogs(Context context) {
        Logger logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        LoggerContext lc = logbackLogger.getLoggerContext();

        Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAndStopAllAppenders();

        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<ILoggingEvent>();
        rollingPolicy.setMaxHistory(3);
        SizeAndTimeBasedFNATP<ILoggingEvent> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<ILoggingEvent>();
        sizeAndTimeBasedFNATP.setMaxFileSize("2MB");
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(sizeAndTimeBasedFNATP);
        rollingPolicy.setFileNamePattern(context.getFilesDir().getPath() + "/logs/old/flickruploader.%d{yyyy-MM-dd}.%i.log");
        rollingPolicy.setContext(lc);

        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<ILoggingEvent>();
        fileAppender.setContext(lc);
        fileAppender.setFile(getLogFilePath());
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setTriggeringPolicy(rollingPolicy);
        rollingPolicy.setParent(fileAppender);

        PatternLayoutEncoder pl = new PatternLayoutEncoder();
        pl.setContext(lc);
        pl.setCharset(Charset.defaultCharset());
        pl.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %class{0}.%method:%L > %msg%n");
        pl.setImmediateFlush(false);
        pl.start();

        fileAppender.setEncoder(pl);
        fileAppender.setName("file");

        rollingPolicy.start();
        fileAppender.start();

        if (BuildConfig.DEBUG) {
            final PatternLayoutEncoder logcatTagPattern = new PatternLayoutEncoder();
            logcatTagPattern.setContext(lc);
            logcatTagPattern.setPattern("%class{0}");
            logcatTagPattern.start();

            final PatternLayoutEncoder logcatPattern = new PatternLayoutEncoder();
            logcatPattern.setContext(lc);
            logcatPattern.setPattern("%msg%n");
            logcatPattern.start();

            final LogcatAppender logcatAppender = new LogcatAppender();
            logcatAppender.setContext(lc);
            logcatAppender.setTagEncoder(logcatTagPattern);
            logcatAppender.setEncoder(logcatPattern);
            logcatAppender.start();

            rootLogger.addAppender(logcatAppender);
        }

        rootLogger.addAppender(fileAppender);

        CrashlyticsAppender crashlyticsAppender = new CrashlyticsAppender();
        crashlyticsAppender.setContext(lc);
        crashlyticsAppender.start();
        rootLogger.addAppender(crashlyticsAppender);
    }
}

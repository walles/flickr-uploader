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

import com.crashlytics.android.Crashlytics;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;

/**
 * Sends {@link Throwable}s to Crashlytics.
 */
class CrashlyticsAppender extends AppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!LoggingUtils.IS_CRASHLYTICS_ENABLED) {
            return;
        }

        IThrowableProxy throwableProxy = eventObject.getThrowableProxy();
        if (throwableProxy == null) {
            return;
        }

        Throwable t;
        if (throwableProxy instanceof ThrowableProxy) {
            t = ((ThrowableProxy)throwableProxy).getThrowable();
            if (t == null) {
                t = new NullPointerException("Got a null throwable from the proxy");
            }
        } else {
            t = new IllegalArgumentException(
                    "Unsupported IThrowable implementation: " + throwableProxy.getClass().getCanonicalName());
        }

        Crashlytics.logException(t);
    }
}

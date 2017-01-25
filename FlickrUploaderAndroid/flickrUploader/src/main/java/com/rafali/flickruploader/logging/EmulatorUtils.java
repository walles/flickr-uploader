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

import android.os.Build;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class EmulatorUtils {
    public static final boolean IS_ON_EMULATOR = isRunningOnEmulator();
    public static final boolean IS_ON_ANDROID = isRunningOnAndroid();

    private EmulatorUtils() {
        throw new UnsupportedOperationException("Utility class, please don't instantiate");
    }

    private static boolean isRunningOnEmulator() {
        // Inspired by
        // http://stackoverflow.com/questions/2799097/how-can-i-detect-when-an-android-application-is-running-in>
        if (Build.PRODUCT == null) {
            return false;
        }

        Set<String> parts = new HashSet<>(Arrays.asList(Build.PRODUCT.split("_")));
        if (parts.size() == 0) {
            return false;
        }

        parts.remove("sdk");
        parts.remove("google");
        parts.remove("x86");
        parts.remove("phone");

        // If the build identifier contains only the above keywords in some order, then we're
        // in an emulator
        return parts.isEmpty();
    }

    private static boolean isRunningOnAndroid() {
        // Inspired by: https://developer.android.com/reference/java/lang/System.html#getProperties()
        // Developed using trial and error...
        final Properties properties = System.getProperties();
        final String httpAgent = (String)properties.get("http.agent");
        if (httpAgent == null) {
            return false;
        }
        return httpAgent.contains("Android");
    }
}

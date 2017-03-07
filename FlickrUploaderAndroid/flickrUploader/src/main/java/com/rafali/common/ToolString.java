package com.rafali.common;

import com.rafali.flickruploader.logging.LoggingUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.Locale;

public class ToolString {
	public static final String REGEX_EMAIL_INSIDE = "[a-zA-Z0-9][\\w\\.-]*[a-zA-Z0-9]@[a-zA-Z0-9][\\w\\.-]*[a-zA-Z0-9]\\.[a-zA-Z][a-zA-Z\\.]*[a-zA-Z]";
	public static final String REGEX_EMAIL = "^" + REGEX_EMAIL_INSIDE + "$";

	public static boolean isNotBlank(String str) {
		return !isBlank(str);
	}

	public static boolean isBlank(String str) {
		if (str == null)
			return true;
		return str.trim().length() == 0;
	}

	public static String formatDuration(long durationMs) {
		StringBuilder strb = new StringBuilder();
		long diffInSeconds = durationMs / 1000L;
		long sec, min, hours, days = 0;
		sec = (diffInSeconds >= 60 ? diffInSeconds % 60 : diffInSeconds);
		min = (diffInSeconds = (diffInSeconds / 60)) >= 60 ? diffInSeconds % 60 : diffInSeconds;
		hours = (diffInSeconds = (diffInSeconds / 60)) >= 24 ? diffInSeconds % 24 : diffInSeconds;
		days = (diffInSeconds = (diffInSeconds / 24));
		if (days > 0) {
			strb.append(days + "d");
			if (hours > 0) {
				strb.append(" " + hours + "h");
			}
		} else if (hours > 0) {
			strb.append(hours + "h");
			if (min > 0) {
				strb.append(" " + min + "m");
			}
		} else if (min > 0) {
			strb.append(min + "m");
			strb.append(" " + String.format(Locale.ENGLISH, "%02d", sec) + "s");
		} else {
			strb.append(sec + "s");
		}
		return strb.toString();
	}

	public static String readableFileSize(long size) {
		if (size <= 0)
			return "0";
		final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
		int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
		if (decimalFormat == null) {
			decimalFormat = new DecimalFormat("#,##0.#");
		}
		return decimalFormat.format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}

	static DecimalFormat decimalFormat;

	/**
	 * @deprecated Simply pass the exception as is to your logging framework instead.
	 */
	@Deprecated
	public static String stack2string(Throwable e) {
		LoggingUtils.logException(e);

		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			return "------\r\n" + sw + "------\r\n";
		} catch (Throwable e2) {
			return "bad stack2string";
		}
	}

	public static String getFileName(String path) {
		if (path.endsWith("/")) {
			return getFileName(path.substring(0, path.length() - 1));
		} else {
			int lastIndexOf = path.lastIndexOf("/");
			if (lastIndexOf > 0) {
				return path.substring(lastIndexOf + 1);
			}
			return "";
		}
	}

	public static String getParentPath(String path) {
		if (path.endsWith("/")) {
			return getParentPath(path.substring(0, path.length() - 1));
		} else {
			int lastIndexOf = path.lastIndexOf("/");
			if (lastIndexOf > 0) {
				return path.substring(0, lastIndexOf);
			}
			return "/";
		}
	}
}

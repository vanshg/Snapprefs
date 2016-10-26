/*
 * Copyright (C) 2014  Sturmen, stammler, Ramis and P1nGu1n
 *
 * This file is part of Keepchat.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.marz.snapprefs;

import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.marz.snapprefs.Util.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;

import de.robv.android.xposed.XposedBridge;

public class Logger {

    public static final String LOG_TAG = "Snapprefs: ";
    private static int printWidth = 70;
    private static boolean defaultForced = false;
    private static boolean defaultPrefix = true;

    private static boolean hasLoaded = false;
    private static HashSet<LogType> logTypes = new HashSet<>();

    /**
     * Restrict instantiation of this class, it only contains static methods.
     */
    private Logger() {
    }


    /**
     * Write debug information to the Xposed Log if enabled or forced by the parameter.
     *
     * @param message The message you want to log
     * @param prefix  Whether it should be prefixed by the log-tag
     * @param forced  Whether to force log and thus overrides the debug setting
     */
    public static void log(String message, boolean prefix, boolean forced) {
        try {
            if (!Preferences.getBool(Preferences.Prefs.DEBUGGING) && !forced)
                return;

        } catch (Throwable t) {
            Log.d("SNAPPREFS", "Tried to log before fully loaded: [" + message + "]");
            return;
        }

        if (prefix) {
            message = LOG_TAG + message;
        }

        try {
            XposedBridge.log(message);
        } catch (Throwable e) {
            Log.d("snapprefs", message);
        }
    }

    public static void afterHook(String message) {
        log("AfterHook: " + message, defaultPrefix, defaultForced);
    }

    public static void beforeHook(String message) {
        log("BeforeHook: " + message, defaultPrefix, defaultForced);
    }

    /**
     * Prints a title in a line width of at least {@link #printWidth} with areas before and after filled with '#'s
     *
     * @param message The message to print in the title
     */
    public static void printTitle(String message) {
        log("", defaultPrefix, defaultForced);
        printFilledRow();
        printMessage(message);
        printFilledRow();
    }

    /**
     * Prints a message with left and right aligned '#'s, to be used with {@link #printTitle(String)} and {@link #printFilledRow()}
     *
     * @param message The message to print between the '#'s
     */
    public static void printMessage(String message) {
        log("#" + StringUtils.center(message, printWidth) + "#", defaultPrefix, defaultForced);
    }

    /**
     * Prints a message using @printMessage and then prints a filled row with @printFilledRow
     *
     * @param message The final message that is going to be printed
     */
    public static void printFinalMessage(String message) {
        printMessage(message);
        printFilledRow();
    }

    /**
     * Print a '#' Filled row of width {@link #printWidth}
     */
    public static void printFilledRow() {
        log(StringUtils.repeat("#", printWidth + 2), defaultPrefix, defaultForced);
    }

    /**
     * Write debug information to the Xposed Log if enabled.
     *
     * @param message The message you want to log
     * @param prefix  Whether it should be prefixed by the log-tag
     */
    public static void log(String message, boolean prefix) {
        log(message, prefix, defaultForced);
    }

    /**
     * Write a throwable to the Xposed Log, even when debugging is disabled.
     *
     * @param throwable The throwable to log
     */
    public static void log(Throwable throwable) {
        try {
            XposedBridge.log(throwable);
        } catch (Throwable t) {
            Log.e("SNAPPREFS", "Throwable: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Write a throwable with a message to the Xposed Log, even when debugging is disabled.
     *
     * @param message   The message to log
     * @param throwable The throwable to log after the message
     */
    public static void log(String message, Throwable throwable) {
        log(message, true, true);
        log(throwable);
    }

    /**
     * Logs the current stack trace(ie. the chain of calls to get where you are now)
     */
    public static void logStackTrace() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

        for (StackTraceElement traceElement : stackTraceElements)
            Logger.log("Stack trace: [Class: " + traceElement.getClassName() + "] [Method: " + traceElement.getMethodName() + "]", defaultPrefix, defaultForced);
    }


    public static void log(String message) {
        log(message, LogType.DEBUG);
    }

    public static void log(String message, @Nullable LogType logType) {
        if (!Preferences.getBool(Preferences.Prefs.DEBUGGING) &&
                (logType != null && !logType.isForced))
            return;

        if (logType == null) {
            assignPrefixAndPrint(message);
            return;
        }

        if (!hasLoaded || logType.isForced || logTypes.contains(logType)) {
            String outputMsg = logType.tag + " " + message;
            assignPrefixAndPrint(outputMsg);
        }
    }

    private static void assignPrefixAndPrint(String message) {
        if (defaultPrefix)
            message = LOG_TAG + message;

        try {
            XposedBridge.log(message);
        } catch (Throwable t) {
            Log.d("Snapprefs", message);
        }
    }

    @SuppressWarnings("unchecked")
    static void loadSelectedLogTypes() {
        File logTypeFile = new File(Preferences.getContentPath(), "LogTypes.json");
        log("Performing LogType load", LogType.FORCED);

        if (!logTypeFile.exists()) {
            loadDefaultLogTypes();
            return;
        }

        Gson gson = new Gson();
        FileReader reader = null;

        try {
            reader = new FileReader(logTypeFile);
            logTypes = gson.fromJson(reader, HashSet.class);
            log(String.format("Loaded %s log types", logTypes.toString()), LogType.FORCED);
        } catch (FileNotFoundException e) {
            log("LogType list file not found", LogType.FORCED);
            loadDefaultLogTypes();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void saveSelectedLogTypes() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        File logTypeFile = new File(Preferences.getContentPath(), "/LogTypes.json");

        try {
            logTypeFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        FileWriter writer = null;
        try {
            writer = new FileWriter(logTypeFile);
            gson.toJson(logTypes, writer);
            log(String.format("Saved %s log types", logTypes.toString()), LogType.FORCED);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void loadDefaultLogTypes() {
        log("Loading default LogTypes", LogType.FORCED);

        Collections.addAll(logTypes, LogType.values());
        saveSelectedLogTypes();
    }

    public enum LogType {
        DEBUG("Debug"),
        CHAT("Chat"),
        LENS("Lens"),
        GROUPS("Groups"),
        DATABASE("Database"),
        SAVING("Saving"),
        FORCED("Forced", true);

        public String tag;
        public boolean isForced = false;

        LogType(String tag) {
            this.tag = String.format("[%s]", tag);
        }

        LogType(String tag, boolean isForced) {
            this(tag);
            this.isForced = isForced;
        }
    }
}

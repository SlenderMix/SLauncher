package ru.spark.slauncher.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import ru.spark.slauncher.Metadata;
import ru.spark.slauncher.ui.CrashWindow;
import ru.spark.slauncher.upgrade.UpdateChecker;
import ru.spark.slauncher.util.i18n.I18n;
import ru.spark.slauncher.util.platform.OperatingSystem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static java.util.Collections.newSetFromMap;

/**
 * @author spark1337
 */
public class CrashReporter implements Thread.UncaughtExceptionHandler {


    private static final Map<String, String> SOURCE = new HashMap<String, String>() {
        {
            put("javafx.fxml.LoadException", I18n.i18n("crash.NoClassDefFound"));
            put("Location is not set", I18n.i18n("crash.NoClassDefFound"));
            put("UnsatisfiedLinkError", I18n.i18n("crash.user_fault"));
            put("java.lang.NoClassDefFoundError", I18n.i18n("crash.NoClassDefFound"));
            put("ru.spark.slauncher.util.ResourceNotFoundError", I18n.i18n("crash.NoClassDefFound"));
            put("java.lang.VerifyError", I18n.i18n("crash.NoClassDefFound"));
            put("java.lang.NoSuchMethodError", I18n.i18n("crash.NoClassDefFound"));
            put("java.lang.NoSuchFieldError", I18n.i18n("crash.NoClassDefFound"));
            put("netscape.javascript.JSException", I18n.i18n("crash.NoClassDefFound"));
            put("java.lang.IncompatibleClassChangeError", I18n.i18n("crash.NoClassDefFound"));
            put("java.lang.ClassFormatError", I18n.i18n("crash.NoClassDefFound"));
            put("com.sun.javafx.css.StyleManager.findMatchingStyles", I18n.i18n("launcher.update_java"));
            put("NoSuchAlgorithmException", "Has your operating system been installed completely or is a ghost system?");
        }
    };
    private static Set<String> CAUGHT_EXCEPTIONS = newSetFromMap(new ConcurrentHashMap<>());

    private boolean checkThrowable(Throwable e) {
        String s = StringUtils.getStackTrace(e);
        for (HashMap.Entry<String, String> entry : SOURCE.entrySet())
            if (s.contains(entry.getKey())) {
                if (StringUtils.isNotBlank(entry.getValue())) {
                    String info = entry.getValue();
                    Logging.LOG.severe(info);
                    try {
                        Alert alert = new Alert(AlertType.INFORMATION, info);
                        alert.setTitle(I18n.i18n("message.info"));
                        alert.setHeaderText(I18n.i18n("message.info"));
                        alert.showAndWait();
                    } catch (Throwable t) {
                        Logging.LOG.log(Level.SEVERE, "Unable to show message", t);
                    }
                }
                return false;
            }
        return true;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Logging.LOG.log(Level.SEVERE, "Uncaught exception in thread " + t.getName(), e);

        try {
            String stackTrace = StringUtils.getStackTrace(e);
            if (!stackTrace.contains("ru.spark"))
                return;

            if (CAUGHT_EXCEPTIONS.contains(stackTrace))
                return;
            CAUGHT_EXCEPTIONS.add(stackTrace);

            String text = "---- SLauncher Crash Report ----\n" +
                    "  Version: " + Metadata.VERSION + "\n" +
                    "  Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n" +
                    "  Thread: " + t.toString() + "\n" +
                    "\n  Content: \n    " +
                    stackTrace + "\n\n" +
                    "-- System Details --\n" +
                    "  Operating System: " + System.getProperty("os.name") + ' ' + OperatingSystem.SYSTEM_VERSION + "\n" +
                    "  Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor") + "\n" +
                    "  Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor") + "\n" +
                    "  JVM Max Memory: " + Runtime.getRuntime().maxMemory() + "\n" +
                    "  JVM Total Memory: " + Runtime.getRuntime().totalMemory() + "\n" +
                    "  JVM Free Memory: " + Runtime.getRuntime().freeMemory() + "\n";

            Logging.LOG.log(Level.SEVERE, text);

            if (checkThrowable(e)) {
                Platform.runLater(() -> new CrashWindow(text).show());
                if (!UpdateChecker.isOutdated()) {
                    Analytics.recordLauncherCrash(t, text);
                }
            }
        } catch (Throwable handlingException) {
            Logging.LOG.log(Level.SEVERE, "Unable to handle uncaught exception", handlingException);
        }
    }
}
package com.ivory.employees.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * One-line log format that carries the request correlation id and the acting user, so a request and
 * its answer can be followed end to end:
 *
 * <pre>2026-09-09 10:15:42.031 INFO  [7f3c1a92] [admin] EmployeesServlet - ...</pre>
 */
public class LogFormatter extends Formatter {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord record) {
        StringBuilder line = new StringBuilder(160);
        line.append(TIMESTAMP.format(Instant.ofEpochMilli(record.getMillis())))
                .append(' ')
                .append(String.format("%-5s", record.getLevel().getName()))
                .append(" [").append(RequestContext.requestId()).append(']')
                .append(" [").append(RequestContext.user()).append("] ")
                .append(shortName(record.getLoggerName()))
                .append(" - ")
                .append(formatMessage(record))
                .append(System.lineSeparator());

        if (record.getThrown() != null) {
            StringWriter stackTrace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(stackTrace));
            line.append(stackTrace);
        }
        return line.toString();
    }

    private static String shortName(String loggerName) {
        if (loggerName == null) {
            return "-";
        }
        int lastDot = loggerName.lastIndexOf('.');
        return lastDot < 0 ? loggerName : loggerName.substring(lastDot + 1);
    }
}

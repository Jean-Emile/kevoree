package org.kevoree.library.javase.slf4j;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import org.kevoree.annotation.*;
import org.kevoree.framework.AbstractComponentType;
import org.slf4j.LoggerFactory;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 04/06/13
 * Time: 10:39
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@Library(name = "slf4j")
@ComponentType
@DictionaryType({
        @DictionaryAttribute(name = "pattern", optional = true, defaultValue = "%-5level [%d{HH:mm:ss.SSS}:%file:%line] - %msg%n"),
        @DictionaryAttribute(name = "rootLevel", optional = true, defaultValue = "WARN", vals = {"INFO", "WARN", "DEBUG", "ERROR", "TRACE"}),
        @DictionaryAttribute(name = "loggers", optional = true)
})
public class LogBackConfigurator extends AbstractComponentType {

    private String pattern;
    private String rootLevel;
    private String loggers;

    @Start
    public void start() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        ConsoleAppender<ILoggingEvent> ca = new ConsoleAppender<ILoggingEvent>();
        ca.setContext(context);
        ca.setName("console");

        PatternLayoutEncoder pl = new PatternLayoutEncoder();
        pl.setContext(context);
        pattern = (String) getDictionary().get("pattern");
        pl.setPattern(pattern);
        pl.start();

        ca.setEncoder(pl);
        ca.start();

        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAndStopAllAppenders();
        rootLogger.addAppender(ca);

        rootLevel = (String) getDictionary().get("rootLevel");
        setLevel(rootLogger, rootLevel);

        for (Logger logger : context.getLoggerList()) {
            setLevel(logger, rootLevel);
        }

        loggers = (String) getDictionary().get("loggers");
        if (null != loggers && !"".equals(loggers)) {
            for (String loggerString : loggers.split(";")) {
                String[] loggerSplit = loggerString.split("=");
                Logger logger = context.getLogger(loggerSplit[0]);
                setLevel(logger, loggerSplit[1]);
            }
        }
    }

    @Stop
    public void stop() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (Logger logger : context.getLoggerList()) {
            logger.setLevel(Level.WARN);
        }
    }

    @Update
    public void update() {
        String tmpPattern = (String) getDictionary().get("pattern");
        String tmpRoot = (String) getDictionary().get("rootLevel");
        String tmpLoggers = (String) getDictionary().get("loggers");
        if (!pattern.equals(tmpPattern)
                || !rootLevel.equals(tmpRoot)
                || (loggers == null && tmpLoggers != null && !"".equals(tmpLoggers))
                || (loggers != null && tmpLoggers == null)
                || (loggers != null && !loggers.equals(tmpLoggers))) {
            stop();
            start();
        }
    }

    private void setLevel(Logger logger, String level) {
        if ("DEBUG".equalsIgnoreCase(level)) {
            logger.setLevel(Level.DEBUG);
        } else if ("WARN".equalsIgnoreCase(level)) {
            logger.setLevel(Level.WARN);
        } else if ("INFO".equalsIgnoreCase(level)) {
            logger.setLevel(Level.INFO);
        } else if ("ERROR".equalsIgnoreCase(level)) {
            logger.setLevel(Level.ERROR);
        } else if ("TRACE".equalsIgnoreCase(level)) {
            logger.setLevel(Level.TRACE);
        }
    }
}

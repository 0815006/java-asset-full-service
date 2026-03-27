#!/bin/bash

# ==============================================================================
# Spring Boot Application Startup Script
# ==============================================================================

# Application JAR file name
APP_NAME="java-asset-full-service-0.0.1-SNAPSHOT.jar"
# JVM Options
JVM_OPTS="-Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m"
# Active Profile
PROFILE="dev"

usage() {
    echo "Usage: sh start.sh [start|stop|restart|status]"
    exit 1
}

is_exist() {
    pid=`ps -ef | grep $APP_NAME | grep -v grep | awk '{print $2}'`
    if [ -z "${pid}" ]; then
        return 1
    else
        return 0
    fi
}

start() {
    is_exist
    if [ $? -eq 0 ]; then
        echo "${APP_NAME} is already running. pid=${pid}"
    else
        echo "Starting ${APP_NAME}..."
        nohup java $JVM_OPTS -jar $APP_NAME --spring.profiles.active=$PROFILE > app.log 2>&1 &
        echo "${APP_NAME} started successfully."
    fi
}

stop() {
    is_exist
    if [ $? -eq "0" ]; then
        echo "Stopping ${APP_NAME} (pid=${pid})..."
        kill -9 $pid
        echo "${APP_NAME} stopped."
    else
        echo "${APP_NAME} is not running."
    fi
}

status() {
    is_exist
    if [ $? -eq "0" ]; then
        echo "${APP_NAME} is running. pid is ${pid}"
    else
        echo "${APP_NAME} is NOT running."
    fi
}

restart() {
    stop
    sleep 2
    start
}

case "$1" in
    "start")
        start
        ;;
    "stop")
        stop
        ;;
    "status")
        status
        ;;
    "restart")
        restart
        ;;
    *)
        # Default to start if no argument provided
        start
        ;;
esac

@echo off
title DonutSMP Server
echo Starting the DonutSMP server... Leave this window open while you play!
echo Type "stop" here to close the server.
java -Xmx4G -Xms1G -jar fabric-server-launch.jar nogui
echo.
echo The server stopped. If you see "java is not recognized", install Java 25 first (see HOW TO PLAY.txt).
pause

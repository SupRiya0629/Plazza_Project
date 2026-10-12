#!/bin/bash
# Starts the EV Charging Network backend on port 8080.

# Free the port if an old server is still running.
pkill -9 -f "evcharging.Server" 2>/dev/null
fuser -k 8080/tcp 2>/dev/null
sleep 1

# Launch the server.
sbt run

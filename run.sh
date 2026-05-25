#!/usr/bin/env bash
set -e
if [ ! -f target/javadb.jar ]; then
  mvn -q -DskipTests package
fi
java -jar target/javadb.jar "$@"

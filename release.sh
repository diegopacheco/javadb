#!/usr/bin/env bash
set -e
mvn -q clean package
ls -la target/javadb.jar

#!/bin/bash
rm -rf target 
mvn clean package
mvn spring-boot:run

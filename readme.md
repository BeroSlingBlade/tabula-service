# Tabula-service

Simple Camel REST service around [Tabula](https://github.com/tabulapdf/tabula-java). The service allows uploading a pdf (not image based) to http://host:port/upload/pdf
and returns any found tables as csv.

## Give it a spin in the IDE

    mvn clean compile exec:java -Pmain

## Build fat jar

    mvn clean package

## Run it

    java -Dhost=localhost -Dport=8083 -cp ./tabula-service-fat-0.0.1.jar nl.beroco.tools.PxQ

## Install with systemd

Contents of /etc/systemd/system/tabula.service:

    [Unit]
    Description=Tabula service
    
    [Service]
    WorkingDirectory=/somewhere/tabula
    ExecStart=/bin/java -Xms128m -Xmx256m -cp /path/to/tabula-service-fat-0.0.1.jar -Dhost=0.0.0.0 -Dport=8083 nl.beroco.tools.PxQ
    User=user
    Type=simple
    Restart=on-failure
    RestartSec=10
    
    [Install]
    WantedBy=multi-user.target


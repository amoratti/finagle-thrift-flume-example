finagle-thrift-flume-example
============================

Ejemplo con finagle + thrift (o http) + flume


Para ejecutar el cliente-servidor
---------------------------------

    mvn clean install
    mvn exec:java -Dexec.mainClass="sample.thrift.ThriftServer" # server
    mvn exec:java -Dexec.mainClass="sample.thrift.ThriftClient" # cliente 1
    mvn exec:java -Dexec.mainClass="sample.thrift.ThriftClient" # cliente 2


Para compilar thrift
--------------------
    compile_thrift.sh
    
    
Flume
-----

Requiere un servidor flume configurado con lo siguiente

    app-agent: avroSource(12345) | collectorSink("s3n://[key]:[secret]@[bucket]/logs/%Y/%m/%d/%H00", "%{host}-");

    
Para correr flume

    ./bin/flume master # master
    ./bin/flume node -n app-agent # agente
    
    
#!/usr/bin/env bash

# influx
docker service create --name influxdb -p 8086:8086 -p 8083:8083 --network dsanet \
-e INFLUXDB_DB=dsa -e INFLUXDB_HTTP_LOG_ENABLED=false -e INFLUXDB_DATA_QUERY_LOG_ENABLED=false \
influxdb

sleep 2

# broker
docker service create --name broker -p 9000:9000 --network dsanet iotdsa/broker-scala:0.4.0-SNAPSHOT

sleep 5

# responder
docker service create --name responder \
--entrypoint "/opt/docker/bin/benchmark-responder-app -Dinflux.host=influxdb" --network dsanet \
-e broker.url=http://broker:9000/conn \
-e responder.count=20 -e responder.nodes=50 -e responder.autoinc.interval=500 -e responder.autoinc.collate=true \
-e rampup.delay=100 -e stats.interval=2000 \
iotdsa/broker-benchmarks

sleep 10

# requester
docker service create --name requester \
--entrypoint "/opt/docker/bin/benchmark-requester-app -Dinflux.host=influxdb" --network dsanet \
-e broker.url=http://broker:9000/conn \
-e requester.subscribe=true -e requester.count=20 -e requester.batch=50 -e rampup.delay=100 -e stats.interval=2000 \
iotdsa/broker-benchmarks
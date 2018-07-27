#!/bin/bash -e

rm -rf infinispan-server
cp -r /home/slaskawi/work/infinispan/infinispan/server/integration/build/target/distribution/infinispan-server-9.4.0-SNAPSHOT ./infinispan-server
cp ../configurations/cloud-single-port.xml ./infinispan-server/standalone/configuration
cp ./jolokia.sh ./infinispan-server/bin
cp ../keystore/server-keystore.jks ./infinispan-server/standalone/configuration
docker build -t slaskawi/infinispan-single-port .

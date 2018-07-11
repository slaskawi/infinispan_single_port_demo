rm -rf infinispan-server
cp -r /home/slaskawi/work/infinispan/infinispan/server/integration/build/target/distribution/infinispan-server-9.4.0-SNAPSHOT ./infinispan-server
cp ./jolokia.sh ./infinispan-server/bin
docker build -t slaskawi/infinispan-single-port .

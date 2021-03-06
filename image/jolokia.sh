#!/bin/sh

if [ -z "$JMX_CONF" ]
then
   if [ -z "$1" ]
   then
       echo "Neither JMX_CONF nor input parameters are defined. Exiting"
       exit 1
   else
       JMX_CONF=$1
   fi
fi

AGENT_BOND_JAR=$(ls $JBOSS_HOME/modules/system/add-ons/ispn/io/fabric8/agent-bond/ispn-9.4/*.jar | sed -e "s| |:|g")
export JAVA_OPTS="$JAVA_OPTS -javaagent:$AGENT_BOND_JAR=$JMX_CONF"

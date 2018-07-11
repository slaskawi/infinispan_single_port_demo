_TEST_PROJECT = myproject
_IMAGE = slaskawi/infinispan-single-port

clear-templates:
	oc delete all,secrets,sa,templates,configmaps,daemonsets,clusterroles,rolebindings,serviceaccounts --selector=template=infinispan-single-port || true
	oc delete template infinispan-single-port || true
	oc delete configmap infinispan-configuration || true
.PHONY: clear-templates

test-single-port:
	oc create configmap infinispan-app-configuration --from-file=./configurations/cloud-single-port.xml || true
	oc create -f templates/infinispan-single-port.json || true
	oc process infinispan-single-port -p NAMESPACE=$(shell oc project -q) -p IMAGE=$(_IMAGE) | oc create -f -
.PHONY: test-single-port

build-image:	
	cd image && ./build.sh
.PHONY: build-image

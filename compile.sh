#!/bin/bash

##############################################
# Configure this, if you don't have 'mvn' as an environment variable!
MAVEN="mvn"
##############################################

echo ""
cd ..
cd l2j-mmocore
$MAVEN clean:clean install -Dmaven.test.skip=true
cd ..
cd l2j-commons
$MAVEN clean:clean install -Dmaven.test.skip=true
cd ..
cd l2jfree-core
$MAVEN clean:clean install assembly:assembly -Dmaven.test.skip=true
cd ..
cd l2jfree-login
$MAVEN clean:clean assembly:assembly -Dmaven.test.skip=true
cd ..
cd l2jfree-datapack
$MAVEN clean:clean compile -Dmaven.test.skip=true
cd ..
echo ""
echo "Done."

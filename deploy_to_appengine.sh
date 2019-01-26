#!bash

rm target/*-standalone.war
lein clean
lein cljsbuild once production
lein ring uberwar
if [ -d "target/war" ]; then
	rm -rf target/war
fi
mkdir target/war
cd target/war
jar xf ../*standalone.war 
cd ../..
ln -s ../../../appengine-web.xml target/war/WEB-INF/
ln -s ../../../cron.xml target/war/WEB-INF/
ln -s ../../../logging.properties target/war/WEB-INF/
gcloud app deploy target/war --project=photosync-2018

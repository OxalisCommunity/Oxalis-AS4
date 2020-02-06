FROM maven:3.3.9-jdk-8 AS mvn

ADD . $MAVEN_HOME

RUN cd $MAVEN_HOME \
 && mvn -B clean package \
 && cp -r target/$(ls target | grep "\-dist$" | head -1) /dist


FROM difi/oxalis:4.1.1

COPY --from=mvn /dist /oxalis/ext
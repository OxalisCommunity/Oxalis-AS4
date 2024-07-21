FROM maven:3.8.6-jdk-11 AS mvn

ADD . $MAVEN_HOME

RUN cd $MAVEN_HOME \
 && mvn -B clean package -DskipTests=true \
 && cp -r target/$(ls target | grep "\-dist$" | head -1) /dist


FROM norstella/oxalis:6.6.0

COPY --from=mvn /dist /oxalis/ext
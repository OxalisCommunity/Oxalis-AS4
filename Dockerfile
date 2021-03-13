FROM maven:3.3.9-jdk-8 AS mvn

ADD . $MAVEN_HOME

RUN cd $MAVEN_HOME \
 && mvn -B clean package -DskipTests=true \
 && cp -r target/$(ls target | grep "\-dist$" | head -1) /dist


FROM norstella/oxalis:latest

COPY --from=mvn /dist /oxalis/ext
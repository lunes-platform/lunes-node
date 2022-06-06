FROM alpine

ARG node_type
WORKDIR /home
COPY $node_type.conf lunesnode.conf
COPY lunesnode-latest.jar lunesnode-latest.jar 
EXPOSE 7770

RUN apk add --no-cache openjdk8-jre wget

CMD java -jar lunesnode-latest.jar 
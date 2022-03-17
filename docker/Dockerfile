FROM alpine

ARG node_type
WORKDIR /home
COPY $node_type.conf lunesnode.conf

EXPOSE 7770

RUN apk add --no-cache openjdk8-jre wget
RUN wget https://github.com/lunes-platform/lunesnode/releases/download/0.1.0/lunesnode-0.1.0.jar

CMD java -jar lunesnode-0.1.0.jar
FROM alpine:latest

COPY ./target/lunesnode-latest.jar /root/lunesnode-latest.jar
COPY ./lunesnode.conf /root/

RUN apk add openjdk8-jre

VOLUME [ "/root/" ]

EXPOSE 7770 5555

WORKDIR /root

ENTRYPOINT ["java", "-jar", "/root/lunesnode-latest.jar", "/root/lunesnode.conf"]

# To build image
# docker build -t lunesnode:0.1.3 .

# To Create silently
# docker run \
# --name lunesnode \
# --restart always \
# --mount source=/root,target=/root/ \
# -d \
# -p 7770:7770 \
# -p 80:5555 \
# lunesplatform/node:0.1.3

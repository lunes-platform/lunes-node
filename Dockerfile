FROM alpine:latest
RUN apk add --no-cache openjdk8-jre
RUN apk add --no-cache vim

COPY ./target/lunesnode-latest.jar /root/lunesnode-latest.jar
COPY ./lunesnode.conf /root/

VOLUME [ "/root/" ]

EXPOSE 7770 5555

WORKDIR /root

ENTRYPOINT ["java", "-jar", "/root/lunesnode-latest.jar", "/root/lunesnode.conf"]

# To build image
# docker build -t your-image-name .

# To Create silently
# docker run \
# -d \
# -p 7770:7770 \
# -p 5555:5555 \
# -v lunes-blockchain-data:/root/ \
# --name lunesnode \
# --restart always \
# your-image-name

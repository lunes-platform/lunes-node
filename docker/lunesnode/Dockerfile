FROM alpine:latest
RUN apk add --no-cache openjdk8-jre
RUN apk add --no-cache vim

RUN  wget -O - "https://github.com/Lunes-platform/LunesNode/releases/download/v0.1.4/lunesnode-latest.jar" > /root/lunesnode-latest.jar
RUN wget -O - "https://github.com/Lunes-platform/LunesNode/raw/v0.1.4/lunesnode.conf" > /root/lunesnode.conf

VOLUME [ "/root" ]
WORKDIR /root
EXPOSE 7770 5555

ENTRYPOINT ["java", "-jar", "lunesnode-latest.jar", "lunesnode.conf"]

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

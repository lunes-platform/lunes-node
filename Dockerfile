# Defaults if not specified in --build-arg
ARG sbt_version=1.5.5
ARG sbt_home=/usr/local/sbt

FROM alpine:latest
ARG sbt_version
ARG sbt_home

RUN apk add --no-cache --update bash
RUN apk add --no-cache openjdk8-jre
RUN apk add --no-cache git
RUN apk add --no-cache openssh
RUN apk add --no-cache --update wget


RUN mkdir -pv "$sbt_home"
RUN wget -qO - "https://github.com/sbt/sbt/releases/download/v$sbt_version/sbt-$sbt_version.tgz" >/tmp/sbt.tgz
RUN tar xzf /tmp/sbt.tgz -C "$sbt_home" --strip-components=1

RUN ln -sv "$sbt_home"/bin/sbt /usr/bin/sbt

WORKDIR /root/
VOLUME /root/

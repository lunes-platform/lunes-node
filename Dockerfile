FROM alpine:3.15

# Defaults if not specified in --build-arg
ARG sbt_version=1.6.2
ARG sbt_home=/usr/local/sbt


# Install dependencies
RUN apk add --no-cache --update --upgrade  bash openjdk8-jre wget


# Install SBT
RUN mkdir -pv "$sbt_home"
RUN wget -qO - "https://github.com/sbt/sbt/releases/download/v$sbt_version/sbt-$sbt_version.tgz" >/tmp/sbt.tgz
RUN tar xzf /tmp/sbt.tgz -C "$sbt_home" --strip-components=1
RUN ln -sv "$sbt_home"/bin/sbt /usr/bin/sbt
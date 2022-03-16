FROM lunesplatform/node:based

ARG B="20-resolve-all-branches"

WORKDIR /home
RUN git clone -b $B https://github.com/lunes-platform/lunesnode.git .
CMD ["sbt", "assembly"]
# ------ run for build lunesnode ------
# docker run \
# -v $PWD:/home/target/ \
# lunesplatform/node:builder

# ------ run for build this container ------
# docker buildx build \
# --push \
# --no-cache \
# --file Dockerfile.builder \
# --build-arg B=BRANCH_OF_LUNESNODE \
# --platform linux/amd64,linux/arm64/v8 \
# -t lunesplatform/node:builder .
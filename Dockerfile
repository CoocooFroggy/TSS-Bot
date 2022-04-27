FROM gradle:7.4.2-jdk17 AS TEMP_BUILD_IMAGE
ENV APP_HOME=/usr/app/
WORKDIR $APP_HOME
COPY build.gradle settings.gradle $APP_HOME
  
COPY gradle $APP_HOME/gradle
COPY --chown=gradle:gradle . /home/gradle/src
USER root
RUN chown -R gradle /home/gradle/src

COPY . .
RUN gradle shadowJar

FROM eclipse-temurin:17
ENV ARTIFACT_NAME='TSS Portable-1.0-all.jar'
ENV APP_HOME=/usr/app/
    
WORKDIR $APP_HOME
COPY --from=TEMP_BUILD_IMAGE $APP_HOME/build/libs/$ARTIFACT_NAME .
RUN apt-get update
RUN apt-get install -y \
    autoconf \
    autoconf-archive \
    autogen \
    automake \
    libtool \
    m4 \
    make \
    pkg-config \
    libzip-dev \
    build-essential \
    checkinstall \
    git \
    libtool-bin \
    libreadline-dev \
    libusb-1.0-0-dev \
    libplist-dev \
    libcurl4-openssl-dev \
    python-dev \
    libssl-dev
COPY script.sh .
RUN ./script.sh
RUN ldconfig -v
ENTRYPOINT exec java -jar "${ARTIFACT_NAME}"

#!/bin/bash

sudo apt install \
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
libplist-dev

# libgeneral
git clone 'https://github.com/tihmstar/libgeneral.git'
cd libgeneral/
./autogen.sh
make
make install
cd ../
# libfragmentzip
git clone 'https://github.com/tihmstar/libfragmentzip.git'
cd libfragmentzip/
./autogen.sh
make
make install
cd ../
# libplist
git clone 'https://github.com/libimobiledevice/libplist.git'
cd libplist/
./autogen.sh
make
make install
cd ../
# libimobiledevice-glue
git clone 'https://github.com/libimobiledevice/libimobiledevice-glue.git'
cd libimobiledevice-glue/
./autogen.sh
make
make install
cd ../
# libirecovery
git clone 'https://github.com/libimobiledevice/libirecovery.git'
cd libirecovery/
./autogen.sh
make
make install
cd ../
# tsschecker
git clone --recursive 'https://github.com/1Conan/tsschecker.git'
cd tsschecker/
./autogen.sh
make
make install
# img4tool
git clone 'https://github.com/tihmstar/img4tool.git'
cd img4tool/
./autogen.sh
make
make install
echo 'Done!'

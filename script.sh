#!/bin/bash -i

# notroot
git clone 'https://github.com/Gregwar/notroot.git' $HOME/notroot
echo 'source "$HOME/notroot/bashrc"' >> ~/.bashrc
source ~/.bashrc

notroot install -y \
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

mkdir ~/installed/

export PKG_CONFIG_PATH=$HOME/installed/lib/pkgconfig

# libgeneral
git clone 'https://github.com/tihmstar/libgeneral.git'
cd libgeneral/
./autogen.sh --prefix=$HOME/installed/ CPPFLAGS=-I$HOME/installed/include/
make
make install
cd ../
# libfragmentzip
git clone 'https://github.com/tihmstar/libfragmentzip.git'
cd libfragmentzip/
./autogen.sh --prefix=$HOME/installed/ CPPFLAGS=-I$HOME/installed/include/
make
make install
cd ../
# libplist
git clone 'https://github.com/libimobiledevice/libplist.git'
cd libplist/
./autogen.sh --prefix=$HOME/installed/ CPPFLAGS=-I$HOME/installed/include/
make
make install
cd ../
# libimobiledevice-glue
git clone 'https://github.com/libimobiledevice/libimobiledevice-glue.git'
cd libimobiledevice-glue/
./autogen.sh --prefix=$HOME/installed/ CPPFLAGS=-I$HOME/installed/include/
make
make install
cd ../
# libirecovery
git clone 'https://github.com/libimobiledevice/libirecovery.git'
cd libirecovery/
./autogen.sh --prefix=$HOME/installed/ CPPFLAGS=-I$HOME/installed/include/
make
make install
cd ../
# tsschecker
git clone --recursive 'https://github.com/1Conan/tsschecker.git'
cd tsschecker/
./autogen.sh --prefix=$HOME/installed/ CPPFLAGS=-I$HOME/installed/include/
make
make install
cd ../
# liblzfse
git clone 'https://github.com/lzfse/lzfse.git'
cd lzfse/
make install INSTALL_PREFIX=$HOME/installed/
cd ../
# img4tool
git clone 'https://github.com/tihmstar/img4tool.git'
cd img4tool/
./autogen.sh --prefix=$HOME/installed/ CPPFLAGS=-I$HOME/installed/include/
make
make install
cd ../
echo 'Done!'

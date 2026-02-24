#!/bin/sh

# USAGE: Run this script from the `fit-protocol` directory
# to fetch the latest proto files from the `transactions-fit-performer` repo.
# Then manually commit any changes.
#
# Why not use git subtree or submodule? The repo hosting the proto files uses
# an unconventional organizational scheme with overlapping source roots.
# This script rearranges the files so the source roots do not overlap.

set -e

if [ "$(basename "$(pwd)")" != "fit-protocol" ]; then
    echo "ERROR: Working directory must be fit-protocol" >&2
    exit 1
fi

DEST_DIR=src/main/protobuf
BACKUP_DIR=${DEST_DIR}.bak

mkdir -p target
rm -rf target/update-protos
# Download only the required directories https://stackoverflow.com/a/52269934/611819
git clone --no-checkout --depth 1 --filter=tree:0 https://github.com/couchbaselabs/transactions-fit-performer.git target/update-protos
cd target/update-protos
git sparse-checkout set --no-cone /gRPC
git checkout
cd ../..

mv $DEST_DIR $BACKUP_DIR

mkdir $DEST_DIR
cp target/update-protos/gRPC/*.proto $DEST_DIR
cp target/update-protos/gRPC/columnar/*.proto $DEST_DIR

rm -rf $BACKUP_DIR

echo
echo "Protobuf update complete! Please manually commit any modified files."
echo

git add $DEST_DIR/*
set -x
git status $DEST_DIR

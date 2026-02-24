#!/bin/sh

# Updates files mirrored from other repositories.

set -e

cd fit-protocol
scripts/update-protos.sh
cd ..

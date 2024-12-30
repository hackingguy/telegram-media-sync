#!/bin/bash

# Decode keystore
echo $KEYSTORE_BASE64 | base64 -d > telegram-drive.keystore 
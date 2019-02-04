#!/bin/bash
# get login information using AWS CLI and this information redirect to new shell
aws ecr get-login --no-include-email --region ap-northeast-1 > __al.sh
# change mode for use eass-build
chmod 777 __al.sh
# execute shell script for login
sudo sh ./__al.sh

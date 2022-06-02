#!/bin/bash

source config.sh

# Install java.
cmd="sudo yum update -y;sudo amazon-linux-extras install java-openjdk11 -y;sudo yum-config-manager --enable rhel-7-server-optional-rpms;sudo yum install java-11-openjdk-devel -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

# Install web server. Add GIT_REPO_PATH and SERVER_CONFIG_PATH to config.sh
sudo scp -r -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $GIT_REPO_PATH/target/imageproc-1.0-SNAPSHOT-jar-with-dependencies.jar ec2-user@$(cat instance.dns):
sudo scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $SERVER_CONFIG_PATH/server-config.sh ec2-user@$(cat instance.dns):

# Set the JAVA_HOME and PATH variables through the server-config.sh script
cmd="sudo mv /home/ec2-user/server-config.sh /etc/profile.d/"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

# Verify that the JAVA_HOME variable is correctly set
cmd='echo javahome $JAVA_HOME;'
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

# Setup web server to start on instance launch.
cmd="echo \"java -cp /home/ec2-user/imageproc-1.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.imageproc.WebServer\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

# Run the web server immediately as well
cmd="java -cp /home/ec2-user/imageproc-1.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.imageproc.WebServer"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd
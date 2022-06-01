#!/bin/bash

source config.sh

# Install java.
cmd="sudo yum update -y;sudo amazon-linux-extras install java-openjdk11 -y;sudo yum-config-manager --enable rhel-7-server-optional-rpms;sudo yum install java-11-openjdk-devel -y;sudo yum install maven -y;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

# Install web server.
sudo scp -r -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ~/cnv-shared/cnv22-g16/src ec2-user@$(cat instance.dns):
sudo scp -r -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ~/cnv-shared/cnv22-g16/pom.xml ec2-user@$(cat instance.dns):
sudo scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ~/lab2/server-config.sh ec2-user@$(cat instance.dns):

cmd="sudo mv /home/ec2-user/server-config.sh /etc/profile.d/"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

cmd='echo javahome $JAVA_HOME;'
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

cmd="mvn package;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

# Setup web server to start on instance launch.
cmd="echo \"java -cp /home/ec2-user/target/imageproc-1.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.imageproc.WebServer\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat instance.dns) $cmd

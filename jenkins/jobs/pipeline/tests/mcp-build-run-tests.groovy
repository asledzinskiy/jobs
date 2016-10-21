node('devops') {

  def WORKSPACE = "${env.WORKSPACE}"
  def DEVOPS_DIR = WORKSPACE + "/utils/fuel-devops"
  def CONF_PATH = DEVOPS_DIR + "/default.yaml"
  def IMAGE_PATH = WORKSPACE + "/image.qcow2"
  def ENV_NAME = "mcp-build-run-test.${env.BUILD_NUMBER}"
  def VENV_DIR = "/home/jenkins/venv-fuel-devops-3.0"
  def DEVOPS_DB_ENGINE = "django.db.backends.sqlite3"
  def DEVOPS_DB_NAME = "/home/jenkins/venv-fuel-devops-3.0.sqlite3.db"
  def SSHPASS = "vagrant"

  deleteDir()

  stage('project-config code checkout') {
    def HOST = env.GERRIT_HOST
    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "master"
      host = HOST
      project = "mcp-ci/project-config"
    }
  }

  stage('mcp-cicd-poc code checkout') {
    dir('mcp-cicd-poc') {
      gerritPatchsetCheckout {
        credentialsId = "mcp-ci-gerrit"
      }
    }
  }

  stage('Fetch the VM image') {
    sh '''
      export API_URL=$(curl https://artifactory.mcp.mirantis.net/artifactory/api/storage/vm-images/packer/?lastModified | awk '/uri/ {print $3}'|tr -d '",')
      export IMAGE_URL=$(curl ${API_URL}| awk '/downloadUri/ {print $3}'|tr -d '",')
      curl ${IMAGE_URL} -o image.qcow2
    '''
  }

  withEnv(["CONF_PATH=${CONF_PATH}",
           "IMAGE_PATH=${IMAGE_PATH}",
           "ENV_NAME=${ENV_NAME}",
           "DEVOPS_DIR=${DEVOPS_DIR}",
           "VENV_DIR=${VENV_DIR}",
           "DEVOPS_DB_ENGINE=${DEVOPS_DB_ENGINE}",
           "DEVOPS_DB_NAME=${DEVOPS_DB_NAME}",
           "SSHPASS=${SSHPASS}" ]) {

    try {
      writeFile file: WORKSPACE + '/create_env.sh', text: '''\
        #!/bin/bash
        source ${VENV_DIR}/bin/activate
        python ${DEVOPS_DIR}/env_manage.py create_env && \
        python ${DEVOPS_DIR}/env_manage.py get_node_ip > env_node_ip
      '''.stripIndent()
      writeFile file: WORKSPACE + '/erase_env.sh', text: '''\
        #!/bin/bash
        source ${VENV_DIR}/bin/activate
        dos.py erase ${ENV_NAME}
      '''.stripIndent()
      stage('Create the fuel-devops env') {
        sh '''
          chmod +x ${WORKSPACE}/create_env.sh
          chmod +x ${WORKSPACE}/erase_env.sh
          ${WORKSPACE}/create_env.sh
        '''
      }

      writeFile file: WORKSPACE + '/run_tests_on_node.sh', text: '''\
        #!/bin/bash

        export PKGS="
        haveged
        mariadb-client
        python-tox
        libffi-dev
        libssl-dev
        libyaml-dev
        python-pip
        python-dev
        python-virtualenv
        virtualenv
        "
        export ENV_DIR=~/.tox/mcp-ci
        export WORKSPACE=~/mcp-cicd-poc
        export TEST_USER=vagrant

        sudo apt-get update
        sudo apt-get install -y ${PKGS}
        cd mcp-cicd-poc/
        tox -e mcp-ci
        set -e
        ./tests/runtests.sh
      '''.stripIndent()
      stage('Execute test in env') {
        sh '''
          sleep 1m
          export ENV_NODE_IP=$(cat env_node_ip)
          sshpass -e scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -r mcp-cicd-poc vagrant@${ENV_NODE_IP}:.
          sshpass -e scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no run_tests_on_node.sh vagrant@${ENV_NODE_IP}:.
          sshpass -e ssh -t -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no vagrant@${ENV_NODE_IP} "bash ./run_tests_on_node.sh"
        '''
      }
    } catch (InterruptedException x) {
      echo "The job was aborted"
    } finally {
      sh "${WORKSPACE}/erase_env.sh"
    }
  }
}

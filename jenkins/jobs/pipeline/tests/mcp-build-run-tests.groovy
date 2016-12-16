def server = Artifactory.server("mcp-ci")
def gitTools = new com.mirantis.mcp.Git()

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
    gitTools.gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "master"
      host = HOST
      project = "mcp-ci/project-config"
    }
  }

  stage('mcp-cicd-installer code checkout') {
    dir('mcp-cicd-installer') {
      gitTools.gerritPatchsetCheckout {
        credentialsId = "mcp-ci-gerrit"
      }
    }
  }

  stage('Fetch the VM image') {
    def downloadSpec = """{
      "files": [
      {
        "pattern": "vm-images/packer/ubuntu-16.04*.qcow2",
        "props": "com.mirantis.latest=true",
        "target": "downloaded/"
      }
     ]
    }"""
    server.download(downloadSpec)
    sh "mv downloaded/packer/ubuntu-16.04*.qcow2 image.qcow2"
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
      writeFile file: WORKSPACE + '/ssh-config', text: '''\
        StrictHostKeyChecking no
        UserKnownHostsFile /dev/null
        ForwardAgent yes
        User vagrant
      '''.stripIndent()
      writeFile file: WORKSPACE + '/create_env.sh', text: '''\
        #!/bin/bash
        source ${VENV_DIR}/bin/activate
        python ${DEVOPS_DIR}/env_manage.py create_env && \
        python ${DEVOPS_DIR}/env_manage.py get_node_ip > env_node_ip
      '''.stripIndent()
      writeFile file: WORKSPACE + '/erase_env.sh', text: '''\
        #!/bin/bash
        # Before we erase env we should collect the logs
        export ENV_NODE_IP=$(cat env_node_ip)
        sshpass -e ssh -qF ${WORKSPACE}/ssh-config ${ENV_NODE_IP} "bash ./run_tests.sh logs"
        sshpass -e scp -r -qF ${WORKSPACE}/ssh-config ${ENV_NODE_IP}:~/logs/ .
        # Compress the logs
        gzip -r ${WORKSPACE}/logs/
        # Destroy the Env (fuel-devops VM)
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
      writeFile file: WORKSPACE + '/run_tests.sh', text: '''\
        #!/bin/bash
        set +x

        function tests {
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
        export WORKSPACE=~/mcp-cicd-installer
        export TEST_USER=vagrant

        sudo apt-get update
        sudo apt-get install -y ${PKGS}
        cd mcp-cicd-installer/
        tox -e mcp-ci
        set -e
        ./tests/runtests.sh
        }
        function logs {
        mkdir -p logs/{artifactory,gerrit,jenkins}
        sudo chmod -R +rx /var/lib/docker/
        sudo cp /var/lib/docker/containers/*/*-json.log logs/
        sudo cp -aR /srv/mcp-data/artifactory/logs/ logs/artifactory/
        sudo cp -aR /srv/mcp-data/gerrit/logs/ logs/gerrit/
        sudo cp -aR /srv/mcp-data/jenkins/logs/ logs/jenkins/
        }
        $@
      '''.stripIndent()
      stage('Execute test in env') {
        sh '''
          sleep 1m
          export ENV_NODE_IP=$(cat env_node_ip)
          sshpass -e scp -r -qF ${WORKSPACE}/ssh-config mcp-cicd-installer run_tests.sh ${ENV_NODE_IP}:.
          sshpass -e ssh -qF ${WORKSPACE}/ssh-config ${ENV_NODE_IP} "bash ./run_tests.sh tests"
        '''
      }
    } catch (InterruptedException x) {
      echo "The job was aborted"
    } finally {
      sh "${WORKSPACE}/erase_env.sh"
      archiveArtifacts allowEmptyArchive: true, artifacts: 'logs/*', excludes: null
    }
  }
}

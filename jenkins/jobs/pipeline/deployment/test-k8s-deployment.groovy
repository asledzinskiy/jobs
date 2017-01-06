def server = Artifactory.server("mcp-ci")
def gitTools = new com.mirantis.mcp.Git()
def String CLUSTER = env.CLUSTER_NAME
def Boolean ERASE_ENV = env.ERASE_ENV
def String SLAVE_NODE_LABEL = "deployment-${CLUSTER}"

node("${SLAVE_NODE_LABEL}") {

  def WORKSPACE = "${env.WORKSPACE}"
  def DEVOPS_DIR = WORKSPACE + "/utils/fuel-devops/"
  def CONF_PATH = DEVOPS_DIR + "k8s_cluster_default.yaml"
  def IMAGE_PATH = WORKSPACE + "/image.qcow2"
  def ENV_NAME = "mcp-test-deploy-k8s-cluster-${CLUSTER}.${env.BUILD_NUMBER}"
  def VENV_DIR = "${WORKSPACE}/venv-fuel-devops-3.0"
  def DEVOPS_DB_ENGINE = "django.db.backends.sqlite3"
  def DEVOPS_DB_NAME = "${WORKSPACE}/venv-fuel-devops-3.0.sqlite3.db"
  def SSHPASS = "vagrant"
  def POOL_DEFAULT = "10.100.0.0/16:24"
  def SLAVE0_IP_LEASE="+100"
  def SLAVE1_IP_LEASE="+101"
  def SLAVE2_IP_LEASE="+102"
  def NODE_IPS = "10.100.0.100 10.100.0.101 10.100.0.102"

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

  stage('Fetch the VM image') {
    def downloadSpec = """{
      "files": [
      {
        "pattern": "vm-images/packer/ubuntu-16.04*.qcow2",
        "props": "com.mirantis.latest=true",
        "target": "/home/jenkins/images/"
      }
     ]
    }"""
    server.download(downloadSpec)
    sh "ln -sf /home/jenkins/images/packer/ubuntu-16.04*.qcow2 image.qcow2"
  }

  stage('Install and configure DevOps') {
    // Need to use latest Fuel-Devops.
    // FIXME: Switch to 3.0.4 or other next tag after 3.0.3
    withEnv(["DEVOPS_DB_ENGINE=${DEVOPS_DB_ENGINE}",
           "DEVOPS_DB_NAME=${DEVOPS_DB_NAME}"]) {
        sh """
          virtualenv --no-site-packages ${VENV_DIR}
          . ${VENV_DIR}/bin/activate
          pip install git+https://github.com/openstack/fuel-devops.git --upgrade
          django-admin.py syncdb --settings=devops.settings
          django-admin.py migrate devops --settings=devops.settings
        """
      }
  }

  withEnv(["CONF_PATH=${CONF_PATH}",
           "IMAGE_PATH=${IMAGE_PATH}",
           "ENV_NAME=${ENV_NAME}",
           "DEVOPS_DIR=${DEVOPS_DIR}",
           "VENV_DIR=${VENV_DIR}",
           "DEVOPS_DB_ENGINE=${DEVOPS_DB_ENGINE}",
           "DEVOPS_DB_NAME=${DEVOPS_DB_NAME}",
           "SSHPASS=${SSHPASS}",
           "POOL_DEFAULT=${POOL_DEFAULT}",
           "SLAVE0_IP_LEASE=${SLAVE0_IP_LEASE}",
           "SLAVE1_IP_LEASE=${SLAVE1_IP_LEASE}",
           "SLAVE2_IP_LEASE=${SLAVE2_IP_LEASE}" ]) {

    try {
      writeFile file: WORKSPACE + '/ssh-config', text: '''\
        StrictHostKeyChecking no
        UserKnownHostsFile /dev/null
        ForwardAgent yes
        User vagrant
      '''.stripIndent()
      writeFile file: WORKSPACE + '/create_env.sh', text: """\
        #!/bin/bash -ex
        source ${VENV_DIR}/bin/activate
        dos.py create-env ${CONF_PATH}
        dos.py start ${ENV_NAME}
        echo 'Waiting for VMs to become up'
        sleep 60
      """.stripIndent()
      writeFile file: WORKSPACE + '/erase_env.sh', text: """\
        #!/bin/bash -ex
        source ${VENV_DIR}/bin/activate
        dos.py erase ${ENV_NAME}
      """.stripIndent()
      stage('Create the fuel-devops env') {
        sh '''
          chmod +x ${WORKSPACE}/create_env.sh
          chmod +x ${WORKSPACE}/erase_env.sh
          ${WORKSPACE}/create_env.sh
        '''
      }
      stage("Run ${CLUSTER}-configure-system job") {
          build job: "${CLUSTER}-configure-system",
              parameters: [
                        string(name: 'SLAVE_NODE_LABEL', value: "${SLAVE_NODE_LABEL}"),
                        string(name: 'NODE_IPS', value: "${NODE_IPS}"),
                        booleanParam(name: 'TEST_MODE', value: true) ]
      }

      stage("Run ${CLUSTER}-deploy-k8s job") {
          build job: "${CLUSTER}-deploy-k8s",
              parameters: [
                        string(name: 'SLAVE_NODE_LABEL', value: "${SLAVE_NODE_LABEL}"),
                        string(name: 'NODE_IPS', value: "${NODE_IPS}"),
                        booleanParam(name: 'TEST_MODE', value: true) ]
      }
    } catch (InterruptedException x) {
        echo "The job was aborted"
    } finally {
        if (ERASE_ENV) {
          sh "${WORKSPACE}/erase_env.sh"
        }
    }
  }
}

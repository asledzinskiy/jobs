def server = Artifactory.server("mcp-ci")
def gitTools = new com.mirantis.mcp.Git()
def String CLUSTER = env.CLUSTER_NAME
def Boolean ERASE_ENV = env.ERASE_ENV
def String SLAVE_NODE_LABEL = "mcp-ci-k8s-test-deployment"

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
  def POOL_DEFAULT = "10.109.0.0/16:24"
  def SLAVE0_IP_LEASE="+100"
  def SLAVE1_IP_LEASE="+101"
  def SLAVE2_IP_LEASE="+102"

  deleteDir()

  stage('project-config code checkout') {
    def HOST = env.GERRIT_HOST
    gitTools.gitSSHCheckout ([
      credentialsId : "mcp-ci-gerrit",
      branch : "master",
      host : HOST,
      project : "mcp-ci/project-config"
    ])
  }

  stage('Fetch the VM image') {
    def imgPath = '/home/jenkins/images'
    def downloadSpec = """{
      "files": [
      {
        "pattern": "vm-images/packer/ubuntu-16.04*.qcow2",
        "props": "com.mirantis.latest=true",
        "target": "${imgPath}/"
      }
     ]
    }"""
    def qcowPath = server.download(downloadSpec)
    // we must have only ONE artifact if it's not true then fail
    if (qcowPath.publishedDependencies.size() != 1) {
      throw new RuntimeException("Please check that you correctly specified the artifact")
    }
    // Get downloaded filename
    String img = new File(qcowPath.publishedDependencies[0].getId()).getName()

    // FIXME(skulanov): Let's live a little bit without cleaning:
    // delete all images except the one we've downloaded before
    // (just replace -exec with -delete in order to delete old images)
    sh "find ${imgPath}/packer/ -type f -not -name ${img} -exec ls {} \\; || true"
    sh "ln -sf ${imgPath}/packer/${img} image.qcow2"
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
  stage('Create env') {
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
          sleep 20
        """.stripIndent()
        writeFile file: WORKSPACE + '/node_ips.sh', text: """\
          #!/bin/bash -ex
          source ${VENV_DIR}/bin/activate
          dos.py slave-ip-list ${ENV_NAME} --address-pool-name=private-pool01 --ip-only
        """.stripIndent()
        writeFile file: WORKSPACE + '/erase_env.sh', text: """\
          #!/bin/bash -ex
          source ${VENV_DIR}/bin/activate
          export DEVOPS_DB_ENGINE=${DEVOPS_DB_ENGINE}
          export DEVOPS_DB_NAME=${DEVOPS_DB_NAME}
          dos.py erase ${ENV_NAME}
        """.stripIndent()

        sh '''
           chmod +x ${WORKSPACE}/create_env.sh
           chmod +x ${WORKSPACE}/node_ips.sh
           chmod +x ${WORKSPACE}/erase_env.sh
           ${WORKSPACE}/create_env.sh
           ${WORKSPACE}/node_ips.sh | tee ${WORKSPACE}/node-ips.txt
        '''
      } catch (InterruptedException x) {
          echo "The job was aborted"
      }
    }
  }
  stage('Archive artifacts') {
    archiveArtifacts artifacts: 'node-ips.txt, erase_env.sh'
  }
}

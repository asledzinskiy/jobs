def gitTools = new com.mirantis.mcp.Git()
node('tools') {
  try {
    def GERRIT_NAME = env.GERRIT_NAME ?: "mcp-ci-gerrit"
    def GERRIT_PORT = env.GERRIT_PORT ?: "29418"
    def GERRIT_PROJECT = env.GERRIT_PROJECT ?: "mcp-ci/mcp-cicd-installer"
    def GERRIT_BRANCH = env.GERRIT_BRANCH ?: "master"
    def TEST_MODE = "True"
    def VENV_PATH = "${env.WORKSPACE}/.tox/mcp-ci"
    def ANSIBLE_HOST_KEY_CHECKING="False"
    def INVENTORY = "${env.WORKSPACE}/conf/slave_nodes_inventory"
    def HOSTS = "${env.HOSTS_LIST}"
    def USER = "${env.USERNAME}"
    def HOSTNAME = sh(returnStdout: true, script: "hostname").trim()
    ArrayList HOST = new ArrayList(Arrays.asList(HOSTS.split("\\s* \\s*")));

    withEnv(["GERRIT_NAME=${GERRIT_NAME}",
             "GERRIT_PORT=${GERRIT_PORT}",
             "GERRIT_PROJECT=${GERRIT_PROJECT}",
             "GERRIT_BRANCH=${GERRIT_BRANCH}"
    ]) {
      stage ('Code checkout') {
        gitTools.gerritPatchsetCheckout ([
          credentialsId : "mcp-ci-gerrit",
        ])
      }
    }
    stage('Construct the inventory') {
      def str = "[slave-nodes]\n"
      for ( int i = 0; i < HOST.size(); i++ ) {
        if (HOST.get(i) == "localhost" || HOST.get(i) =~ /^127\./ || HOST.get(i) =~ /^${HOSTNAME}.*/) {
          println "localhost detected - skipping!"
        } else {
          str += HOST.get(i) + " ansible_user=" + USER + " ansible_connection=ssh\n"
        }
      }
      writeFile file: INVENTORY, text: "${str}"
    }

    withEnv(["VENV_PATH=${VENV_PATH}",
             "TEST_MODE=${TEST_MODE}",
             "ANSIBLE_HOST_KEY_CHECKING=${ANSIBLE_HOST_KEY_CHECKING}" ]) {
      stage ('Create VENV') {
        sh "tox -e mcp-ci"
      }

      stage ('Create initial config') {
        // we need to create jenkins_admin key-pare before init process
        // private key can be fake,
        writeFile file: "${env.WORKSPACE}/conf/ssh/jenkins_admin",
        text: '''\
        -----BEGIN RSA PRIVATE KEY-----
        FAKE KEYS
        -----END RSA PRIVATE KEY-----
        '''.stripIndent()
        // but pub key is real
        writeFile file: "${env.WORKSPACE}/conf/ssh/jenkins_admin.pub",
        text: "${env.JENKINS_MASTER_ID_RSA_PUB}"

        writeFile file: "${env.WORKSPACE}/run.sh", text: '''\
          #!/bin/bash -ex

          source ${WORKSPACE}/tests/vars.sh
          ${WORKSPACE}/mcp-ci.sh init-config
        '''.stripIndent()
        sh "chmod +x ${env.WORKSPACE}/run.sh"
        sh "${env.WORKSPACE}/run.sh"
      }

      stage ('Execute prepare-slave-nodes playbook') {
        sshagent(['jenkins']) {
          sh "./mcp-ci.sh prepare-slave-nodes"
        }
      }
    }

    currentBuild.description = HOSTS
  }
  catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }
}

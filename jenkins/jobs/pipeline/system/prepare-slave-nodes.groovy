node('tools') {
  try {

    def TEST_MODE = "True"
    def VENV_PATH = "${env.WORKSPACE}/.tox/mcp-ci"
    def ANSIBLE_HOST_KEY_CHECKING="False"
    def INVENTORY = "${env.WORKSPACE}/conf/slave_nodes_inventory"
    def HOSTS = "${env.HOSTS_LIST}"
    def USER = "${env.USERNAME}"
    def BRANCH = "${env.BRANCH_NAME}"
    def HOSTNAME = sh(returnStdout: true, script: "hostname").trim()
    ArrayList HOST = new ArrayList(Arrays.asList(HOSTS.split("\\s* \\s*")));

    stage ('Code checkout') {
      def GERRIT_HOST = "${env.GERRIT_HOST}"
      gitSSHCheckout {
        credentialsId = "mcp-ci-gerrit"
        branch = BRANCH
        host = GERRIT_HOST
        project = "mcp-ci/mcp-cicd-installer"
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
        writeFile file: "${env.WORKSPACE}/run.sh", text: '''\
          #!/bin/bash -ex

          source ${WORKSPACE}/tests/vars.sh
          ${WORKSPACE}/mcp-ci.sh init-config
        '''.stripIndent()
        sh "chmod +x ${env.WORKSPACE}/run.sh"
        sh "${env.WORKSPACE}/run.sh"
        writeFile file: "${env.WORKSPACE}/conf/ssh/jenkins_admin.pub",
                  text: "${env.JENKINS_MASTER_ID_RSA_PUB}"
      }

      stage ('Execute prepare-slave-nodes playbook') {
        sshagent(['jenkins']) {
          sh "./mcp-ci.sh prepare-slave-nodes"
        }
      }
    }

  }
  catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }
  finally {
  }
}

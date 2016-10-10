node('tools') {
  try {

    def TEST_MODE = "True"
    def VENV_PATH = "${WORKSPACE}/.tox/mcp-ci"
    def ANSIBLE_HOST_KEY_CHECKING="False"
    def INVENTORY = "${WORKSPACE}/conf/slave_nodes_inventory"
    def HOSTS = "${HOSTS_LIST}"
    def USER = "${USERNAME}"
    def BRANCH = "${BRANCH_NAME}"
    def HOSTNAME = sh(returnStdout: true, script: "hostname").trim()
    ArrayList HOST = new ArrayList(Arrays.asList(HOSTS.split("\\s* \\s*")));

    stage ('Code checkout') {
      gitSSHCheckout {
        credentialsId = "mcp-ci-gerrit"
        branch = BRANCH
        host = "review.fuel-infra.org"
        project = "mcp-ci/mcp-cicd-poc"
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
        sh "./mcp-ci.sh init-config"
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

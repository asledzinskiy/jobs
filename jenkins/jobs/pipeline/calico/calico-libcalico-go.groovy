gitTools = new com.mirantis.mcp.Git()
common = new com.mirantis.mcp.Common()

jenkinsSlaveImg = 'docker-prod-virtual.docker.mirantis.net/mirantis/jenkins-slave-images/projectcalico-debian-slave-20161223134732:latest'
jnlpSlaveImg = 'docker-prod-virtual.docker.mirantis.net/mirantis/jenkins-slave-images/jnlp-slave:latest'

try {
  def artifacts = []
  if (env.RUN_ON_K8S == 'true') {
    // run job on k8s cluster
    artifacts = common.runOnKubernetes([
      function : this.&buildLibcalicoGo,
      jnlpImg  : jnlpSlaveImg,
      slaveImg : jenkinsSlaveImg
    ])
  } else {
    // run job on HW node with label
    node ('calico'){
      artifacts = buildLibcalicoGo()
    }
  }
}
catch(err) {
  echo "Failed: ${err}"
  currentBuild.result = 'FAILURE'
}

def buildLibcalicoGo(){
  try {
    def HOST = env.GERRIT_HOST

    stage ('Checkout libcalico-go'){
      if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
          gitTools.gerritPatchsetCheckout ([
            credentialsId : "mcp-ci-gerrit",
            withWipeOut : true
          ])
      } else {
          gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : "mcp",
            host : HOST,
            project : "projectcalico/libcalico-go"
          ])
      } // else
    }

    stage ('Running libcalico-go unittests') {
      sh "make stop-etcd stop-kubernetes-master"
      sh "make test-containerized"
    }

  }
  catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }
  finally {
    // fix workspace owners
    sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE} ${env.HOME}/.glide || true"
    sh "make stop-etcd stop-kubernetes-master"
  }
}

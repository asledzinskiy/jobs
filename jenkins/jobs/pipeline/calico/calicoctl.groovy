// Add library functions from pipeline-library
artifactory = new com.mirantis.mcp.MCPArtifactory()
calico = new com.mirantis.mcp.Calico()
common = new com.mirantis.mcp.Common()
docker = new com.mirantis.mcp.Docker()
git = new com.mirantis.mcp.Git()
// Artifactory server
artifactoryServer = Artifactory.server("mcp-ci")
buildInfo = Artifactory.newBuildInfo()

projectNamespace = "mirantis/projectcalico"
docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"

if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
  try {
    def artifacts = []
    // run job on HW node with label
    node ('calico'){
      artifacts = buildCalicoContainers()
    }
    // run system test on HW node
    node ('calico'){
      stage ("Run system tests") {
         build job: 'calico.system-test.deploy', propagate: true, wait: true, parameters:
          [
              [$class: 'StringParameterValue', name: 'CALICO_NODE_IMAGE_REPO', value: artifacts["CALICO_NODE_IMAGE_REPO"]],
              [$class: 'StringParameterValue', name: 'CALICOCTL_IMAGE_REPO', value: artifacts["CALICOCTL_IMAGE_REPO"]],
              [$class: 'StringParameterValue', name: 'CALICO_VERSION', value: artifacts["CALICO_VERSION"]],
              [$class: 'StringParameterValue', name: 'MCP_BRANCH', value: 'mcp'],
          ]
      }
    }
  }
  catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }

} else if ( env.GERRIT_EVENT_TYPE == 'change-merged' ) {
  // run job on HW node with calico label
  node ('calico'){
    promoteArtifacts()
  }
} else {
  throw new RuntimeException("Job should be triggered only on patchset-created or change-merged events")
}


def buildCalicoContainers(){
  try {

    stage ('Checkout calicoctl'){
      git.gerritPatchsetCheckout ([
        credentialsId : "mcp-ci-gerrit",
        withWipeOut : true
      ])
    }

    stage ('Run unittest') { sh "make test-containerized"  }

    // start building calicoctl
    def artifactoryUrl = artifactoryServer.getUrl()
    def nodeImg = "calico/node"
    def ctlImg = "calico/ctl"
    def calicoContainersArts = calico.buildCalicoContainers([
      artifactoryURL: "${artifactoryUrl}/binary-prod-virtual",
      dockerRegistry: env.DOCKER_REGISTRY,
      nodeImage: nodeImg,
      ctlImage: ctlImg,
      projectNamespace: projectNamespace
    ])

    def calicoImgTag = calicoContainersArts["CALICO_VERSION"]

    stage('Publishing containers artifacts') {
      artifactory.uploadImageToArtifactory(artifactoryServer,
                                           env.DOCKER_REGISTRY,
                                           "${projectNamespace}/calico/node",
                                           calicoImgTag,
                                           docker_dev_repo)
      artifactory.uploadImageToArtifactory(artifactoryServer,
                                           env.DOCKER_REGISTRY,
                                           "${projectNamespace}/calico/ctl",
                                           calicoImgTag,
                                           docker_dev_repo)
    } // publishing artifacts

    currentBuild.description = """
        <b>node</b>: ${calicoContainersArts["CALICO_NODE_IMAGE_REPO"]}:${calicoContainersArts["CALICO_VERSION"]}<br>
        <b>ctl</b>: ${calicoContainersArts["CALICOCTL_IMAGE_REPO"]}:${calicoContainersArts["CALICO_VERSION"]}<br>
      """

    return calicoContainersArts

  } catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }
  finally {
    // fix workspace owners
    sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE} ${env.HOME}/.glide || true"
  }

} // buildCalicoContainers


def promoteArtifacts () {

  stage('promote') {
    // search calicoctl artifacts
    def calicoProperties = [
      'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
      'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
      'com.mirantis.gerritChangeNumber' : "${env.GERRIT_CHANGE_NUMBER}"
    ]
    // Search for an artifact with required properties
    def artifactURI = artifactory.uriByProperties(artifactoryServer.getUrl(), calicoProperties)
    // Get build info: build id and job name
    if ( artifactURI ) {
        def buildProperties = artifactory.getPropertiesForArtifact(artifactURI)
          //promote calico/ctl image
          artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                  docker_dev_repo,
                  docker_prod_repo,
                  "${projectNamespace}/calico/ctl",
                  buildProperties.get('com.mirantis.targetTag').join(','),
                  buildProperties.get('com.mirantis.targetTag').join(','),
                  true)
          artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                  docker_dev_repo,
                  docker_prod_repo,
                  "${projectNamespace}/calico/ctl",
                  buildProperties.get('com.mirantis.targetTag').join(','),
                  'latest')
          //promote calico/node image
          artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                  docker_dev_repo,
                  docker_prod_repo,
                  "${projectNamespace}/calico/node",
                  buildProperties.get('com.mirantis.targetTag').join(','),
                  buildProperties.get('com.mirantis.targetTag').join(','),
                  true)
          artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                  docker_dev_repo,
                  docker_prod_repo,
                  "${projectNamespace}/calico/node",
                  buildProperties.get('com.mirantis.targetTag').join(','),
                  'latest')
      } else {
          throw new RuntimeException("Artifacts were not found, nothing to promote")
      }
  } // stage promote
} // promote_artifacts

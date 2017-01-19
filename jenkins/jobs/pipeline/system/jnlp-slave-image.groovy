// Add library functions from pipeline-library
artifactory = new com.mirantis.mcp.MCPArtifactory()
common = new com.mirantis.mcp.Common()
gitTools = new com.mirantis.mcp.Git()
// Artifactory server
artifactoryServer = Artifactory.server("mcp-ci")
buildInfo = Artifactory.newBuildInfo()

projectNamespace = "mirantis/jenkins-slave-images"
jnlpSlaveImageName = "${projectNamespace}/jnlp-slave"
docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"


if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
    buildJnlpContainers()
} else if ( env.GERRIT_EVENT_TYPE == 'change-merged' ) {
    promoteArtifacts()
} else {
  throw new RuntimeException("Job should be triggered only on patchset-created or change-merged events")
}

def buildJnlpContainers(){

  node('docker'){

    try {
      deleteDir()
      stage ('Checkout calicoctl'){
        gitTools.gerritPatchsetCheckout ([
          credentialsId : "mcp-ci-gerrit",
          withWipeOut : true
        ])
      }

      def imageTag = common.getDatetime()
      def dockerRepository = env.DOCKER_REGISTRY

      stage ('Build jnlp slave'){
        dir ('jnlp-slave'){
          sh "docker build -t ${dockerRepository}/${jnlpSlaveImageName}:${imageTag} ."
        }

      }

      stage('Publishing container') {
        artifactory.uploadImageToArtifactory(artifactoryServer,
                                             dockerRepository,
                                             jnlpSlaveImageName,
                                             imageTag,
                                             docker_dev_repo)
      } // publishing artifacts

      currentBuild.description = """
        <b>jnlp-slave</b>: ${nodeImg}:${imageTag}<br>
        """

    }
    catch(err) {
      echo "Failed: ${err}"
      currentBuild.result = 'FAILURE'
    }
  }

}


def promoteArtifacts () {
    node('docker') {
        stage('promote') {

          // search calicoctl artifacts
          def imgProperties = [
            'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
            'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
            'com.mirantis.gerritChangeNumber' : "${env.GERRIT_CHANGE_NUMBER}"
          ]
          // Search for an artifact with required properties
          def artifactURI = artifactory.uriByProperties(artifactoryServer.getUrl(), imgProperties)
          // Get build info: build id and job name
          if ( artifactURI ) {
              def buildProperties = artifactory.getPropertiesForArtifact(artifactURI)
                artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                        docker_dev_repo,
                        docker_prod_repo,
                        jnlpSlaveImageName,
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        true)
                artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                        docker_dev_repo,
                        docker_prod_repo,
                        jnlpSlaveImageName,
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        'latest')
            } else {
                throw new RuntimeException("Artifacts were not found, nothing to promote")
            }
        }
    }
}

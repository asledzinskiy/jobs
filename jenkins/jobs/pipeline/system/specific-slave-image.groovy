// Add library functions from pipeline-library
artifactory = new com.mirantis.mcp.MCPArtifactory()
common = new com.mirantis.mcp.Common()
gitTools = new com.mirantis.mcp.Git()
// Artifactory server
artifactoryServer = Artifactory.server("mcp-ci")

projectNamespace = "mirantis/jenkins-slave-images"
slaveType = env.SLAVE_TYPE
slaveImageName = "${projectNamespace}/${slaveType}-slave"
docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"


if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
    buildContainers()
} else if ( env.GERRIT_EVENT_TYPE == 'change-merged' ) {
    promoteArtifacts()
} else {
  throw new RuntimeException("Job should be triggered only on patchset-created or change-merged events")
}

def buildContainers(){

  node('docker'){

    try {
      deleteDir()
      stage ('checkout code'){
        gitTools.gerritPatchsetCheckout ([
          credentialsId : "mcp-ci-gerrit",
          withWipeOut : true
        ])
      }

      def imageTag = common.getDatetime()
      def dockerRepository = env.DOCKER_REGISTRY

      stage ("Build ${slaveType} slave"){
        dir ("${slaveType}-slave"){
          sh "docker rmi -f ${dockerRepository}/${slaveImageName} || true"
          if ("${slaveType}" == "jnlp") {
            sh "docker build --pull --build-arg JENKINS_MASTER=${env.JENKINS_URL} -t ${dockerRepository}/${slaveImageName}:${imageTag} ."
          } else {
            sh "docker build --pull -t ${dockerRepository}/${slaveImageName}:${imageTag} ."
          }
        }
      }

      stage('Publishing container') {
        artifactory.uploadImageToArtifactory(artifactoryServer,
                                             dockerRepository,
                                             slaveImageName,
                                             imageTag,
                                             docker_dev_repo)
      } // publishing artifacts
      def artifactory_url = artifactoryServer.getUrl()
      def custom_properties = ['com.mirantis.imageType': "${slaveType}"]
      def artifact_url = "${artifactory_url}/api/storage/${docker_dev_repo}/${slaveImageName}/${imageTag}"
      artifactory.setProperties(artifact_url, custom_properties, true)

      //cleanup
      sh "docker rmi -f ${dockerRepository}/${slaveImageName}:${imageTag} || true"

      currentBuild.description = """
        <b>${slaveType}-slave</b>: ${dockerRepository}/${slaveImageName}:${imageTag}<br>
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
            'com.mirantis.gerritChangeNumber': "${env.GERRIT_CHANGE_NUMBER}",
            'com.mirantis.imageType': "${slaveType}"
          ]
          // Search for an artifact with required properties
          def artifactURI = artifactory.uriByProperties(artifactoryServer.getUrl(), imgProperties)
          // Get build info: build id and job name
          if ( artifactURI ) {
              def buildProperties = artifactory.getPropertiesForArtifact(artifactURI)
                artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                        docker_dev_repo,
                        docker_prod_repo,
                        slaveImageName,
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        true)
                artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                        docker_dev_repo,
                        docker_prod_repo,
                        slaveImageName,
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        'latest')
            } else {
                throw new RuntimeException("Artifacts were not found, nothing to promote")
            }
        }
    }
}

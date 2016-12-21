artifactory = new com.mirantis.mcp.MCPArtifactory()
common = new com.mirantis.mcp.Common()
git = new com.mirantis.mcp.Git()
artifactoryServer = Artifactory.server("mcp-ci")

projectNamespace = "mirantis/mcp-netchecker"
docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"

if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
    buildNetcheckerServer()
} else if ( env.GERRIT_EVENT_TYPE == 'change-merged' ) {
    promote_artifacts()
} else {
  throw new RuntimeException("Job should be triggered only on patchset-created or change-merged events")
}

def buildNetcheckerServer(){

  node('calico'){

    try {

      stage ('Checkout mcp-netchecker-server'){
        git.gerritPatchsetCheckout ([
          credentialsId : "mcp-ci-gerrit",
          withWipeOut : true
        ])
      }

      def dockerRepository = env.DOCKER_REGISTRY
      def dockerImage = "${dockerRepository}/${projectNamespace}/server"
      def imgTag = git.getGitDescribe(true) + "-" + common.getDatetime()

      stage ('Run syntax tests') { sh "tox -v"  }

      stage ('Build mcp-netchecker/server image') {
        sh "docker build -t ${dockerImage}:${imgTag} . "
      }

      stage('Publish mcp-netchecker/server image') {
        artifactory.uploadImageToArtifactory(artifactoryServer,
                                             dockerRepository,
                                             "${projectNamespace}/server",
                                             imgTag,
                                             docker_dev_repo)
        currentBuild.description = "<b>Docker image</b>: ${dockerImage}:${imgTag}<br>"
      }

      stage ("Run system tests") {
         build job: 'mcp-netchecker-server.system-test.deploy', propagate: true, wait: true, parameters:
          [
              [$class: 'StringParameterValue', name: 'MCP_NETCHECKER_SERVER_IMAGE_REPO', value: dockerImage],
              [$class: 'StringParameterValue', name: 'MCP_NETCHECKER_SERVER_VERSION', value: imgTag],
              [$class: 'StringParameterValue', name: 'MCP_BRANCH', value: 'mcp'],
              [$class: 'StringParameterValue', name: 'GERRIT_BRANCH', value: env.GERRIT_BRANCH],
              [$class: 'StringParameterValue', name: 'GERRIT_REFSPEC', value: env.GERRIT_REFSPEC],
          ]
      }
    }
    catch(err) {
      echo "Failed: ${err}"
      currentBuild.result = 'FAILURE'
    }
  }
}

def promote_artifacts () {
    node('calico') {
        stage('promote') {

          def netcheckerProperties = [
            'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
            'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
            'com.mirantis.gerritChangeNumber' : "${env.GERRIT_CHANGE_NUMBER}"
          ]
          // Search for an artifact with required properties
          def artifactURI = artifactory.uriByProperties(artifactoryServer.getUrl(), netcheckerProperties)
          // Get build info: build id and job name
          if ( artifactURI ) {
            def buildProperties = artifactory.getPropertiesForArtifact(artifactURI)
            def publishTag = buildProperties.get('com.mirantis.targetTag').join(',').replaceAll(/-\d+$/, "")
            //promote netchecker-server image
            artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                    docker_dev_repo,
                    docker_prod_repo,
                    "${projectNamespace}/server",
                    buildProperties.get('com.mirantis.targetTag').join(','),
                    publishTag,
                    true)
            artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                    docker_dev_repo,
                    docker_prod_repo,
                    "${projectNamespace}/server",
                    buildProperties.get('com.mirantis.targetTag').join(','),
                    'latest')
            currentBuild.description = "<b>Docker image</b>: ${env.DOCKER_REGISTRY}/${projectNamespace}/server:${publishTag}<br>"
            } else {
                throw new RuntimeException("Artifacts were not found, nothing to promote")
            }
        }
    }
}

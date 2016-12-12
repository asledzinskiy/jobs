// Add library functions from pipeline-library
artifactory = new com.mirantis.mcp.MCPArtifactory()
common = new com.mirantis.mcp.Common()
docker = new com.mirantis.mcp.Docker()
git = new com.mirantis.mcp.Git()
// Artifactory server
artifactoryServer = Artifactory.server("mcp-ci")
buildInfo = Artifactory.newBuildInfo()

projectNamespace = "mirantis/projectcalico"
projectModule = "calico/felix"
docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"


if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
    buildFelix()
} else if ( env.GERRIT_EVENT_TYPE == 'change-merged' ) {
    promote_artifacts()
} else {
  throw new RuntimeException("Job should be triggered only on patchset-created or change-merged events")
}

def buildFelix(){

  node('calico'){

    try {

      def artifactoryUrl = artifactoryServer.getUrl()
      def dockerRepository = env.DOCKER_REGISTRY
      // define global variables for images
      def felixImg = "${dockerRepository}/${projectNamespace}/${projectModule}"
      // depends on git, so will be defined after checkout
      def felixImgTag = ""

      def HOST = env.GERRIT_HOST
      gitSSHCheckout {
        credentialsId = "mcp-ci-gerrit"
        branch = "mcp"
        host = HOST
        project = "projectcalico/calico-containers"
      }

      dir("${env.WORKSPACE}/tmp_calico-felix"){

        stage ('Checkout felix'){
          gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
            withWipeOut = true
          }
        }
        // define tag
        felixImgTag = git.getGitDescribe(true) + "-" + common.getDatetime()

        stage ('Run felix unittests'){
          // inject COMPARE_BRANCH variable for felix coverage test
          sh "make ut UT_COMPARE_BRANCH=gerrit/${env.GERRIT_BRANCH}"
        }

        stage ('Build calico/felix image') {
          docker.setDockerfileLabels("./Dockerfile", ["docker.imgTag=${felixImgTag}"])

          sh """
                make calico/felix
                docker tag calico/felix ${felixImg}:${felixImgTag}
             """
        }

      }// dir

      stage('Publishing felix artifacts') {
        artifactory.uploadImageToArtifactory(artifactoryServer,
                                             dockerRepository,
                                             "${projectNamespace}/${projectModule}",
                                             felixImgTag,
                                             docker_dev_repo,
                                             buildInfo)
      } // publishing artifacts

      currentBuild.description = "felix image: ${felixImg}:${felixImgTag}<br>"

      // we need to have separate valiable to correctly pass it to
      // buildCalicoContainers() build step
      def nodeImg = "${dockerRepository}/${projectNamespace}/calico/node"
      def ctlImg = "${dockerRepository}/${projectNamespace}/calico/ctl"
      def felixContainerName = felixImg + ":" + felixImgTag
      // start building calico-containers
      def calicoContainersArts = buildCalicoContainers {
        artifactoryURL = "${artifactoryUrl}/binary-prod-virtual"
        dockerRepo = dockerRepository
        felixImage = felixContainerName
        nodeImage = nodeImg
        ctlImage = ctlImg
      }

      def calicoImgTag = calicoContainersArts["CALICO_VERSION"]

      stage('Publishing containers artifacts') {
        artifactory.uploadImageToArtifactory(artifactoryServer,
                                             dockerRepository,
                                             "${projectNamespace}/calico/node",
                                             calicoImgTag,
                                             docker_dev_repo)
        artifactory.uploadImageToArtifactory(artifactoryServer,
                                             dockerRepository,
                                             "${projectNamespace}/calico/ctl",
                                             calicoImgTag,
                                             docker_dev_repo)
      } // publishing artifacts

      currentBuild.description = """
        <b>felix</b>: ${felixImg}:${felixImgTag}<br>
        <b>node</b>: ${nodeImg}:${calicoImgTag}<br>
        <b>ctl</b>: ${ctlImg}:${calicoImgTag}<br>
        """
      stage ("Run system tests") {
         build job: 'calico.system-test.deploy', propagate: true, wait: true, parameters:
          [
              [$class: 'StringParameterValue', name: 'CALICO_NODE_IMAGE_REPO', value: calicoContainersArts["CALICO_NODE_IMAGE_REPO"]],
              [$class: 'StringParameterValue', name: 'CALICOCTL_IMAGE_REPO', value: calicoContainersArts["CALICOCTL_IMAGE_REPO"]],
              [$class: 'StringParameterValue', name: 'CALICO_VERSION', value: calicoImgTag],
              [$class: 'StringParameterValue', name: 'MCP_BRANCH', value: 'mcp'],
          ]
      }

    }
    catch(err) {
      echo "Failed: ${err}"
      currentBuild.result = 'FAILURE'
    }
    finally {
      // fix workspace owners
      sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE} ${env.HOME}/.glide"
    }
  }

}


def promote_artifacts () {
    node('calico') {
        stage('promote') {

          // Search calico-felix artifacts and promote them first
          def felixProperties = [
            'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
            'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
            'com.mirantis.gerritChangeNumber' : "${env.GERRIT_CHANGE_NUMBER}",
            'com.mirantis.targetImg': "${projectNamespace}/${projectModule}"
          ]
          def felixArtifactUri = artifactory.uriByProperties(artifactoryServer.getUrl(), felixProperties)
          // Get build info: build id and job name
          if ( felixArtifactUri ) {
              def buildProperties = artifactory.getPropertiesForArtifact(felixArtifactUri)
              //promote felix image
              artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                      docker_dev_repo,
                      docker_prod_repo,
                      "${projectNamespace}/${projectModule}",
                      buildProperties.get('com.mirantis.targetTag').join(','),
                      buildProperties.get('com.mirantis.targetTag').join(','),
                      true)
              artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                      docker_dev_repo,
                      docker_prod_repo,
                      "${projectNamespace}/${projectModule}",
                      buildProperties.get('com.mirantis.targetTag').join(','),
                      'latest')
          } else {
              throw new RuntimeException("Artifacts were not found, nothing to promote")
          }

          // search calico-containers artifacts, since they have the same tags
          // we will get correct images
          def properties = [
            'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
            'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
            'com.mirantis.gerritChangeNumber' : "${env.GERRIT_CHANGE_NUMBER}",
            'com.mirantis.targetImg': "${projectNamespace}/calico/node"
          ]
          // Search for an artifact with required properties
          def artifactURI = artifactory.uriByProperties(artifactoryServer.getUrl(), properties)
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
        }
    }
}

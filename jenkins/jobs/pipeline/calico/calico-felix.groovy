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
      // define global variables for images
      def felixImg = "${env.DOCKER_REGISTRY}/${projectNamespace}/${projectModule}"
      // depends on git, so will be defined after checkout
      def felixImgTag = ""

      def HOST = env.GERRIT_HOST
      git.gitSSHCheckout ([
        credentialsId : "mcp-ci-gerrit",
        branch : "mcp",
        host : HOST,
        project : "projectcalico/calicoctl"
      ])

      dir("${env.WORKSPACE}/tmp_calico-felix"){

        stage ('Checkout felix'){
          git.gerritPatchsetCheckout ([
            credentialsId : "mcp-ci-gerrit",
            withWipeOut : true
          ])
        }
        // define tag
        felixImgTag = git.getGitDescribe(true) + "-" + common.getDatetime()

        stage ('Run felix unittests'){
          sh "make ut"
        }

        stage ('Build calico/felix image') {
          docker.setDockerfileLabels("./docker-image/Dockerfile", ["docker.imgTag=${felixImgTag}"])

          sh """
                make calico/felix
                docker tag calico/felix ${felixImg}:${felixImgTag}
             """
        }

      }// dir

      stage('Publishing felix artifacts') {
        artifactory.uploadImageToArtifactory(artifactoryServer,
                                             env.DOCKER_REGISTRY,
                                             "${projectNamespace}/${projectModule}",
                                             felixImgTag,
                                             docker_dev_repo,
                                             buildInfo)
      } // publishing artifacts

      def nodeImg = "calico/node"
      def ctlImg = "calico/ctl"
      def felixContainerName = felixImg + ":" + felixImgTag
      // start building calicoctl
      def calicoContainersArts = calico.buildCalicoContainers([
        artifactoryURL: "${artifactoryUrl}/binary-prod-virtual",
        dockerRegistry: env.DOCKER_REGISTRY,
        nodeImage: nodeImg,
        ctlImage: ctlImg,
        felixImage: felixContainerName,
        projectNamespace: projectNamespace,
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
        <b>felix</b>: ${felixImg}:${felixImgTag}<br>
        <b>node</b>: ${calicoContainersArts["CALICO_NODE_IMAGE_REPO"]}:${calicoContainersArts["CALICO_VERSION"]}<br>
        <b>ctl</b>: ${calicoContainersArts["CALICOCTL_IMAGE_REPO"]}:${calicoContainersArts["CALICO_VERSION"]}<br>
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
      sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE} ${env.HOME}/.glide || true"
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

          // search calicoctl artifacts, since they have the same tags
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

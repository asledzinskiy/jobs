// Add library functions from pipeline-library
artifactory = new com.mirantis.mcp.MCPArtifactory()
common = new com.mirantis.mcp.Common()
docker = new com.mirantis.mcp.Docker()
git = new com.mirantis.mcp.Git()
// Artifactory server
artifactoryServer = Artifactory.server("mcp-ci")
buildInfo = Artifactory.newBuildInfo()

projectNamespace = "mirantis/projectcalico"
docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"
// Define binary repos
binaryDevRepo = "binary-dev-local"
binaryProdRepo = "binary-prod-local"

// tag for bird binary
binaryTag = ""


if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
    buildBird()
} else if ( env.GERRIT_EVENT_TYPE == 'change-merged' ) {
    promote_artifacts()
} else {
  throw new RuntimeException("Job should be triggered only on patchset-created or change-merged events")
}

def buildBird(){

  node('calico'){

    try {

      def HOST = env.GERRIT_HOST
      gitSSHCheckout {
        credentialsId = "mcp-ci-gerrit"
        branch = "mcp"
        host = HOST
        project = "projectcalico/calico-containers"
      }

      dir("${env.WORKSPACE}/tmp_bird"){

        stage ('Checkout bird'){
          gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
            withWipeOut = true
          }
        }

        stage ('Build bird binaries'){
                sh "/bin/sh -x build.sh"
        }

        stage('Publishing bird artifacts') {

          dir("artifacts"){
            // define tag for bird
            binaryTag = git.getGitDescribe(true) + "-" + common.getDatetime()
            sh """
              cp ${WORKSPACE}/tmp_bird/dist/bird bird-${binaryTag}
              cp ${WORKSPACE}/tmp_bird/dist/bird6 bird6-${binaryTag}
              cp ${WORKSPACE}/tmp_bird/dist/birdcl birdcl-${binaryTag}
            """
            writeFile file: "latest", text: "${binaryTag}"
            // define mandatory properties for binary artifacts
            // and some additional
            def properties = artifactory.getBinaryBuildProperties([
              "tag=${binaryTag}",
              "project=bird"
              ])

            def uploadSpec = """{
                "files": [
                        {
                            "pattern": "**",
                            "target": "${binaryDevRepo}/${projectNamespace}/bird/",
                            "props": "${properties}"
                        }
                    ]
                }"""

            // Upload to Artifactory.
            artifactory.uploadBinariesToArtifactory(artifactoryServer, buildInfo, uploadSpec, true)
          }// dir
        } // publishing artifacts

      }// dir

      // we need to have separate valiable to correctly pass it to
      // buildCalicoContainers() build step
      def dockerRepository = env.DOCKER_REGISTRY
      def nodeImg = "${dockerRepository}/${projectNamespace}/calico/node"
      def ctlImg = "${dockerRepository}/${projectNamespace}/calico/ctl"
      def bird = artifactoryServer.getUrl() + "/${binaryDevRepo}/${projectNamespace}/bird/bird-${binaryTag}"
      def bird6 = artifactoryServer.getUrl() + "/${binaryDevRepo}/${projectNamespace}/bird/bird6-${binaryTag}"
      def birdcl = artifactoryServer.getUrl() + "/${binaryDevRepo}/${projectNamespace}/bird/birdcl-${binaryTag}"
      // start building calico-containers
      def calicoContainersArts = buildCalicoContainers {
        dockerRepo = dockerRepository
        birdUrl = bird
        bird6Url = bird6
        birdclUrl = birdcl
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
        <b>bird</b>: ${bird}<br>
        <b>bird6</b>: ${bird6}<br>
        <b>birdcl</b>: ${birdcl}<br>
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

          // Search bird artifacts and promote them first
          def birdProperties = [
            'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
            'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
            'com.mirantis.gerritChangeNumber' : "${env.GERRIT_CHANGE_NUMBER}",
            'com.mirantis.project': "bird"
          ]
          def birdUri = artifactory.uriByProperties(artifactoryServer.getUrl(), birdProperties)
          // Get build info: build id and job name
          if ( birdUri ) {
            def buildProperties = artifactory.getPropertiesForArtifact(birdUri)
            def promotionConfig = [
                    'buildName'  : buildProperties.get('com.mirantis.buildName').join(','), // value for each key property is an array
                    'buildNumber': buildProperties.get('com.mirantis.buildNumber').join(','),
                    'status'     : 'Released',
                    'targetRepo' : binaryProdRepo.toString()]
            artifactoryServer.promote(promotionConfig)
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

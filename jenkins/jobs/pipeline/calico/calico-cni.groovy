// Add library functions from pipeline-library
artifactory = new com.mirantis.mcp.MCPArtifactory()
common = new com.mirantis.mcp.Common()
docker = new com.mirantis.mcp.Docker()
git = new com.mirantis.mcp.Git()
// Artifactory server
artifactoryServer = Artifactory.server("mcp-ci")
buildInfo = Artifactory.newBuildInfo()
dockerRepository = env.DOCKER_REGISTRY

projectNamespace = "mirantis/projectcalico"
projectModule = "calico/cni"
docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"

if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
    buildCalicoCNI()
} else if ( env.GERRIT_EVENT_TYPE == 'change-merged' ) {
    promote_artifacts()
} else {
  throw new RuntimeException("Job should be triggered only on patchset-created or change-merged events")
}

def buildCalicoCNI(){

  node('calico'){

    try {

      stage ('Checkout calico-cni'){
        git.gerritPatchsetCheckout {
          credentialsId = "mcp-ci-gerrit"
          withWipeOut = true
        }
      }

      // define timestamp
      def cniBuildId = git.getGitDescribe(true) + "-" + common.getDatetime()

      stage ('Switch to the downstream libcalico-go') {
        def LIBCALICOGO_PATH = "${env.WORKSPACE}/tmp_libcalico-go"
        def HOST = env.GERRIT_HOST
        git.gitSSHCheckout {
          credentialsId = "mcp-ci-gerrit"
          branch = "mcp"
          host = HOST
          project = "projectcalico/libcalico-go"
          targetDir = "${LIBCALICOGO_PATH}"
        }

        // TODO(apanchenko): replace `sed` by Yaml.load() -> modify map -> Yaml.dump()
        sh """
          sed -e '/^- name: .*\\/libcalico-go\$/a \\  repo: file:\\/\\/\\/go\\/src\\/github.com\\/projectcalico\\/libcalico-go\\n  vcs: git' -i.bak glide.lock
          grep -qP '.*repo:\\s+file:.*libcalico-go' glide.lock || { echo 1>&2 \'Repository (libcalico-go) path was not properly set in glide.lock!'; exit 1; }
          """
        sh "LIBCALICOGO_PATH=${LIBCALICOGO_PATH} make vendor"
      }

      stage ('Unit tests') {
        sh "make static-checks-containerized test-containerized"
      }

      stage ('Build calico-cni') {
        CALICO_CNI_IMAGE_REPO="${dockerRepository}/${projectNamespace}/${projectModule}"
        CALICO_CNI_IMAGE_TAG = cniBuildId
        //add LABEL to docker file, also with custom one imgTag
        docker.setDockerfileLabels("./Dockerfile", ["docker.imgTag=${CALICO_CNI_IMAGE_TAG}"])
        sh "make docker-image"
        sh "docker tag calico/cni ${CALICO_CNI_IMAGE_REPO}:${CALICO_CNI_IMAGE_TAG}"
      }

      stage('Publishing cni artifacts') {
        artifactory.uploadImageToArtifactory(artifactoryServer,
                                             dockerRepository,
                                             "${projectNamespace}/${projectModule}",
                                             CALICO_CNI_IMAGE_TAG,
                                             docker_dev_repo,
                                             buildInfo)
      } // publishing cni artifacts

      currentBuild.description = "image: ${CALICO_CNI_IMAGE_REPO}:${CALICO_CNI_IMAGE_TAG}<br>"

      stage ("Run system tests") {
         build job: 'calico.system-test.deploy', propagate: true, wait: true, parameters:
          [
              [$class: 'StringParameterValue', name: 'CALICO_CNI_IMAGE_REPO', value: CALICO_CNI_IMAGE_REPO],
              [$class: 'StringParameterValue', name: 'CALICO_CNI_IMAGE_TAG', value: CALICO_CNI_IMAGE_TAG],
              [$class: 'StringParameterValue', name: 'OVERWRITE_HYPERKUBE_CNI', value: 'true'],
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

          def properties = [
            'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
            'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
            'com.mirantis.gerritChangeNumber' : "${env.GERRIT_CHANGE_NUMBER}"
          ]
          // Search for an artifact with required properties
          def artifactURI = artifactory.uriByProperties(artifactoryServer.getUrl(), properties)
            // Get build info: build id and job name
            if ( artifactURI ) {
                def buildProperties = artifactory.getPropertiesForArtifact(artifactURI)
                //promote docker image
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
        }
    }
}

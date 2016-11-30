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

// tag for confd binary
binaryTag = ""


if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
    buildConfd()
} else if ( env.GERRIT_EVENT_TYPE == 'change-merged' ) {
    promote_artifacts()
} else {
  throw new RuntimeException("Job should be triggered only on patchset-created or change-merged events")
}

def buildConfd(){

  node('calico'){

    try {

      def HOST = env.GERRIT_HOST
      gitSSHCheckout {
        credentialsId = "mcp-ci-gerrit"
        branch = "mcp"
        host = HOST
        project = "projectcalico/calico-containers"
      }

      dir("${env.WORKSPACE}/tmp_confd"){

        def container_src_dir = "/usr/src/confd"
        def src_suffix = "src/github.com/kelseyhightower/confd"
        def container_workdir = "${container_src_dir}/${src_suffix}"
        def container_gopath = "${container_src_dir}/vendor:${container_src_dir}"

        stage ('Checkout confd'){
          gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
            withWipeOut = true
          }
        }

        stage ('Run unittest for confd'){
          sh """
          docker run --rm \
            -v ${env.WORKSPACE}/tmp_confd:/usr/src/confd \
            -w /usr/src/confd \
            golang:1.7 \
            bash -c \
            \"go get github.com/constabulary/gb/...; gb test -v\"
          """
        }

        stage ('Build confd binary'){
          sh """
            docker run --rm \
              -v ${env.WORKSPACE}/tmp_confd:${container_src_dir} \
              -w ${container_workdir} \
              -e GOPATH=${container_gopath} \
              golang:1.7 \
              bash -c \
              \"go build -a -installsuffix cgo -ldflags '-extld ld -extldflags -static' -a -x .\"
          """
        }

        stage('Publishing confd artifacts') {

          dir("artifacts"){
            // define tag for confd
            binaryTag = git.getGitDescribe(true) + "-" + common.getDatetime()
            // create two files confd and confd+tag
            sh "cp ${env.WORKSPACE}/tmp_confd/${src_suffix}/confd confd-${binaryTag}"
            writeFile file: "latest", text: "${binaryTag}"

            // define mandatory properties for binary artifacts
            // and some additional
            def properties = artifactory.getBinaryBuildProperties([
              "tag=${binaryTag}",
              "project=confd"
              ])

            def uploadSpec = """{
                "files": [
                        {
                            "pattern": "**",
                            "target": "${binaryDevRepo}/${projectNamespace}/confd/",
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
      def confd = artifactoryServer.getUrl() + "/${binaryDevRepo}/${projectNamespace}/confd/confd-${binaryTag}"
      // start building calico-containers
      def calicoContainersArts = buildCalicoContainers {
        dockerRepo = dockerRepository
        confdUrl = confd
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
        <b>confd</b>: ${confd}<br>
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

          // Search confd artifacts and promote them first
          def confdProperties = [
            'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
            'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
            'com.mirantis.gerritChangeNumber' : "${env.GERRIT_CHANGE_NUMBER}",
            'com.mirantis.project': "confd"
          ]
          def confdUri = artifactory.uriByProperties(artifactoryServer.getUrl(), confdProperties)
          // Get build info: build id and job name
          if ( confdUri ) {
            def buildProperties = artifactory.getPropertiesForArtifact(confdUri)
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
